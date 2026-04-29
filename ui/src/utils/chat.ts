import { getPrimaryTaskFile, normalizeTaskFile } from "@/utils/taskArtifacts";
import {
  formatDeepSearchQueryText,
  resolveDeepSearchActionText,
  resolveDeepSearchStage,
} from "@/utils/deepSearch";

export const combineData = (
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) => {
  switch (eventData.messageType) {
    case "plan": {
      handlePlanMessage(eventData, currentChat);
      break;
    }
    case "plan_thought": {
      handlePlanThoughtMessage(eventData, currentChat);
      break;
    }
    case "task": {
      handleTaskMessage(eventData, currentChat);
      break;
    }
    default:
      break;
  }
  return currentChat;
};

type TaskRenderCacheEntry = {
  signature: string;
  items: CHAT.Task[];
};

const taskRenderCache = new WeakMap<object, TaskRenderCacheEntry>();

/**
 * 实时 SSE 的文件类事件会把 artifactRefs 放在 eventData 顶层，
 * 这里统一折叠进任务对象，保证工作区始终走同一套取文件逻辑。
 */
export function buildTaskFromEventData(eventData: MESSAGE.EventData): MESSAGE.Task {
  const artifactRefs = Array.isArray(eventData.artifactRefs)
    ? [...eventData.artifactRefs]
    : undefined;

  return {
    taskId: eventData.taskId,
    ...(artifactRefs?.length ? { artifactRefs } : {}),
    ...eventData.resultMap,
  } as MESSAGE.Task;
}

/**
 * deep_search 的 search/report 可能复用同一个 messageId，
 * 工作区同步时优先使用前端派生的 render id，避免不同卡片互相串位。
 */
export function getStableTaskIdentity(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  if (!task) {
    return "";
  }

  return (
    task.id ||
    task.messageId ||
    (task.taskId && task.messageTime ? `${task.taskId}:${task.messageTime}` : "") ||
    ""
  );
}

function isImageGenerationToolResultTask(task?: Partial<MESSAGE.Task>) {
  return task?.messageType === "tool_result" &&
    task?.toolResult?.toolName === "image_generation_tool";
}

function isImageGenerationFileTask(task?: Partial<MESSAGE.Task>) {
  return task?.messageType === "file" &&
    task?.resultMap?.command === "生成图片";
}

function findLastTaskIndex(
  tasks: MESSAGE.Task[],
  matcher: (task: MESSAGE.Task) => boolean
) {
  for (let index = tasks.length - 1; index >= 0; index -= 1) {
    if (matcher(tasks[index])) {
      return index;
    }
  }
  return -1;
}

/**
 * 图像生成会先后发 file / tool_result 两类事件。
 * 前端统一把它们折叠成一个 tool_result 任务，既保留“工具调用”语义，又带上图片产物引用。
 */
function mergeImageGenerationToolTask(
  toolTask: MESSAGE.Task,
  fileTask: MESSAGE.Task
): MESSAGE.Task {
  const artifactRefs = Array.isArray(fileTask.artifactRefs)
    ? [...fileTask.artifactRefs]
    : Array.isArray(toolTask.artifactRefs)
      ? [...toolTask.artifactRefs]
      : undefined;
  const mergedFileInfo = Array.isArray(fileTask.resultMap?.fileInfo)
    ? [...fileTask.resultMap.fileInfo]
    : toolTask.resultMap?.fileInfo;

  return {
    ...toolTask,
    ...(artifactRefs?.length ? { artifactRefs } : {}),
    resultMap: {
      ...(toolTask.resultMap || {}),
      ...(mergedFileInfo?.length ? { fileInfo: mergedFileInfo } : {}),
    },
  };
}

/**
 * 处理计划类型的消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天对象
 */
function handlePlanMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  if (!eventData.taskId) {
    currentChat.multiAgent.plan = {
      taskId: eventData.taskId,
      ...eventData?.resultMap,
    } as unknown as CHAT.Plan;
  }
}

/**
 * 处理计划思考类型的消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天对象
 */
function handlePlanThoughtMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  if (!currentChat.multiAgent.plan_thought) {
    currentChat.multiAgent.plan_thought = "";
  }
  if (eventData.resultMap.isFinal) {
    currentChat.multiAgent.plan_thought = eventData.resultMap.planThought;
  } else {
    currentChat.multiAgent.plan_thought += eventData.resultMap.planThought;
  }
  // Keep thought in sync immediately so streaming UI can update
  // without waiting for heavy task aggregation.
  currentChat.thought = currentChat.multiAgent.plan_thought;
}

/**
 * 处理任务类型的消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天对象
 */
function handleTaskMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  if (!currentChat.multiAgent.tasks) {
    currentChat.multiAgent.tasks = [];
  }
  const taskIndex = findTaskIndex(currentChat.multiAgent.tasks, eventData.taskId);
  if (eventData.resultMap?.messageType) {
    handleTaskMessageByType(eventData, currentChat, taskIndex);
  }
}

/**
 * 查找工具在指定任务中的索引
 * @param tasks 任务数组
 * @param taskIndex 任务索引
 * @param messageId 消息ID
 * @returns 工具索引，如果未找到则返回-1
 */
function findToolIndex(
  tasks: MESSAGE.Task[][],
  taskIndex: number,
  messageId: string | undefined,
  messageType: string | undefined
): number {
  if (taskIndex === -1) return -1;

  return tasks[taskIndex]?.findIndex(
    // 同一个工具在流式过程中会复用 messageId，但像 multimodalagent_tool 会在同一 messageId 下
    // 先发 knowledge 增量、再发 markdown 成果；这里需要把 messageType 一起纳入主键，避免串并项。
    (item: MESSAGE.Task) =>
      item.messageId === messageId && item.messageType === messageType
  );
}

/**
 * 根据消息类型处理任务消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天对象
 * @param taskIndex 任务索引
 */
function handleTaskMessageByType(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number
) {
  const messageType = eventData.resultMap.messageType;
  const toolIndex = findToolIndex(
    currentChat.multiAgent.tasks!,
    taskIndex,
    eventData.messageId,
    messageType
  );

  switch (messageType) {
    case "agent_stream":
      handleAgentStreamMessage(eventData, currentChat);
      break;
    case "tool_thought":
      handleToolThoughtMessage(eventData, currentChat, taskIndex, toolIndex);
      break;
    case "html":
    case "markdown":
    case "ppt":
    case "knowledge":
    case "data_analysis":
      handleContentMessage(eventData, currentChat, taskIndex, toolIndex);
      break;
    case "deep_search":
      handleDeepSearchMessage(eventData, currentChat, taskIndex);
      break;
    default:
      handleNonStreamingMessage(eventData, currentChat, taskIndex);
      break;
  }
}

/**
 * 处理总结阶段的流式增量文本（agent_stream）。
 * 多智能体模式下先写入临时 conclusion，等待最终 result 覆盖。
 */
function handleAgentStreamMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  const chunk = eventData.resultMap?.result || "";
  if (!chunk) {
    return;
  }

  const streamConclusion = currentChat.conclusion;
  if (!streamConclusion || streamConclusion.messageType !== "agent_stream") {
    currentChat.conclusion = {
      taskId: eventData.taskId,
      messageId: eventData.messageId,
      messageType: "agent_stream",
      messageTime: eventData.resultMap?.messageTime || String(Date.now()),
      requestId: eventData.resultMap?.requestId || "",
      finish: false,
      isFinal: false,
      id: eventData.messageId || String(Date.now()),
      result: chunk,
      resultMap: {
        taskSummary: chunk,
        fileList: [],
      },
    } as unknown as CHAT.Task;
    return;
  }

  streamConclusion.result = `${streamConclusion.result || ""}${chunk}`;
  if (!streamConclusion.resultMap) {
    streamConclusion.resultMap = {} as any;
  }
  streamConclusion.resultMap.taskSummary = `${streamConclusion.resultMap.taskSummary || ""}${chunk}`;
}

/**
 * 查找任务在任务数组中的索引
 * @param tasks 任务数组
 * @param taskId 任务ID
 * @returns 任务索引，如果未找到则返回-1
 */
function findTaskIndex(tasks: MESSAGE.Task[][], taskId: string | undefined): number {
  return tasks.findIndex(
    (item: MESSAGE.Task[]) => item[0]?.taskId === taskId
  );
}

/**
 * 处理工具思考消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天项
 * @param taskIndex 任务索引
 * @param toolIndex 工具索引
 */
function handleToolThoughtMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number
) {
  const { tasks } = currentChat.multiAgent;
  const { taskId, resultMap } = eventData;
  const { toolThought, isFinal } = resultMap;

  if (taskIndex === -1) {
    tasks.push([createNewTask(taskId, resultMap)]);
    return;
  }

  if (toolIndex === -1) {
    tasks[taskIndex].push(createNewTask(taskId, resultMap));
    return;
  }

  updateToolThought(tasks[taskIndex][toolIndex], toolThought || '', isFinal);
}

/**
 * 创建新任务对象
 * @param taskId 任务ID
 * @param resultMap 结果映射
 * @returns 新任务对象
 */
function createNewTask(taskId: string, resultMap: MESSAGE.Task): MESSAGE.Task {
  return {
    taskId,
    ...resultMap,
  };
}

/**
 * 更新工具思考内容
 * @param tool 工具对象
 * @param newThought 新的思考内容
 * @param isFinal 是否为最终结果
 */
function updateToolThought(tool: MESSAGE.Task, newThought: string, isFinal: boolean) {
  if (isFinal) {
    tool.toolThought = newThought;
  } else {
    tool.toolThought = (tool.toolThought || '') + newThought;
  }
}

/**
 * 处理内容消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天
 * @param taskIndex 任务索引
 * @param toolIndex 工具索引
 */
function handleContentMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number
) {
  if (taskIndex !== -1) {
    // 更新
    if (toolIndex !== -1) {
      const targetTool = currentChat.multiAgent.tasks[taskIndex][toolIndex];
      // 已完成
      if (eventData.resultMap.resultMap.isFinal) {
        targetTool.resultMap = {
          ...eventData.resultMap.resultMap,
          codeOutput:
            eventData.resultMap.resultMap.data ||
            eventData.resultMap.resultMap.codeOutput ||
            targetTool.resultMap?.codeOutput ||
            "",
        };
        mergeTaskArtifactRefs(targetTool, eventData);
      } else {
        // 进行中
        targetTool.resultMap.isFinal = false;
        targetTool.resultMap.codeOutput += eventData.resultMap.resultMap?.data || "";
        mergeTaskArtifactRefs(targetTool, eventData);
      }
    } else {
      eventData.resultMap.resultMap = initializeResultMap(eventData.resultMap.resultMap);

      // 添加tool
      currentChat.multiAgent.tasks[taskIndex].push(buildTaskFromEventData(eventData));
    }
  } else {

    eventData.resultMap.resultMap = initializeResultMap(eventData.resultMap.resultMap);

    // 添加任务及tool
    currentChat.multiAgent.tasks.push([
      buildTaskFromEventData(eventData),
    ]);
  }
}

/**
 * 初始化结果映射
 * @param originalResultMap 原始结果映射
 * @returns 初始化后的结果映射
 */
export function initializeResultMap(originalResultMap: any) {
  return {
    ...originalResultMap,
    codeOutput: originalResultMap.codeOutput || originalResultMap.data || '',
    fileInfo: originalResultMap.fileInfo || [],
  };
}

/**
 * 处理现有任务
 * @param currentChat 当前聊天
 * @param taskIndex 任务索引
 * @param toolIndex 工具索引
 * @param eventData 事件数据
 * @param resultMap 结果映射
 */
export function handleExistingTask(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number,
  eventData: MESSAGE.EventData,
  resultMap: any
) {
  if (toolIndex !== -1) {
    updateExistingTool(currentChat, taskIndex, toolIndex, resultMap, eventData);
  } else {
    addNewTool(currentChat, taskIndex, eventData, resultMap);
  }
}

/**
 * 更新现有工具
 * @param currentChat 当前聊天
 * @param taskIndex 任务索引
 * @param toolIndex 工具索引
 * @param resultMap 结果映射
 */
function updateExistingTool(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number,
  resultMap: any,
  eventData?: MESSAGE.EventData
) {
  const tool = currentChat.multiAgent.tasks[taskIndex][toolIndex];
  if (resultMap.isFinal) {
    tool.resultMap = {
      ...resultMap,
      codeOutput: resultMap.data || resultMap.codeOutput || tool.resultMap?.codeOutput || "",
    };
  } else {
    tool.resultMap.isFinal = false;
    tool.resultMap.codeOutput += resultMap.data || '';
  }
  mergeTaskArtifactRefs(tool, eventData);
}

/**
 * 添加新工具
 * @param currentChat 当前聊天
 * @param taskIndex 任务索引
 * @param eventData 事件数据
 * @param resultMap 结果映射
 */
function addNewTool(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  eventData: MESSAGE.EventData,
  resultMap: any
) {
  currentChat.multiAgent.tasks[taskIndex].push({
    ...buildTaskFromEventData(eventData),
    resultMap: resultMap,
  } as MESSAGE.Task);
}

function mergeTaskArtifactRefs(targetTask: MESSAGE.Task | undefined, eventData?: MESSAGE.EventData) {
  if (!targetTask || !eventData) {
    return;
  }

  const artifactRefs = Array.isArray(eventData.artifactRefs)
    ? [...eventData.artifactRefs]
    : [];

  if (!artifactRefs.length) {
    return;
  }

  targetTask.artifactRefs = artifactRefs as any;
}

/**
 * 处理新任务
 * @param currentChat 当前聊天
 * @param eventData 事件数据
 * @param resultMap 结果映射
 */
export function handleNewTask(
  currentChat: CHAT.ChatItem,
  eventData: MESSAGE.EventData,
  resultMap: any
) {
  currentChat.multiAgent.tasks.push([
    {
      ...buildTaskFromEventData(eventData),
      resultMap: resultMap,
    } as MESSAGE.Task,
  ]);
}

/**
 * 处理深度搜索消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天
 * @param taskIndex 任务索引
 * @param toolIndex 工具索引
 */
function handleDeepSearchMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number
) {
  const resultMap = eventData.resultMap.resultMap;
  const stage = resolveDeepSearchStage(resultMap?.messageType);
  const toolIndex =
    taskIndex === -1
      ? -1
      : findDeepSearchToolIndex(
        currentChat.multiAgent.tasks[taskIndex] || [],
        eventData.messageId,
        stage
      );

  if (taskIndex !== -1) {
    if (toolIndex !== -1) {
      updateExistingTaskTool(currentChat, taskIndex, toolIndex, resultMap);
    } else {
      addNewToolToExistingTask(currentChat, taskIndex, eventData);
    }
  } else {
    addNewTask(currentChat, eventData);
  }
}

/**
 * deep_search 的查询分解/搜索完成会复用同一条工具记录，
 * 但总结阶段必须单独占一条记录，否则会把左侧“搜索完成”卡片直接覆盖成“正在总结”。
 */
function findDeepSearchToolIndex(
  taskGroup: MESSAGE.Task[],
  messageId: string | undefined,
  stage: ReturnType<typeof resolveDeepSearchStage>
): number {
  return taskGroup.findIndex((item) => {
    if (item.messageType !== "deep_search" || item.messageId !== messageId) {
      return false;
    }

    const itemStage = resolveDeepSearchStage(item.resultMap?.messageType);
    if (stage === "report") {
      return itemStage === "report";
    }

    return itemStage !== "report";
  });
}

/**
 * 更新现有任务和工具的结果
 */
function updateExistingTaskTool(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number,
  resultMap: MESSAGE.ResultMap
) {
  const targetTool = currentChat.multiAgent.tasks[taskIndex][toolIndex];
  targetTool.resultMap.isFinal = resultMap?.isFinal;
  targetTool.resultMap.messageType = resultMap?.messageType;
  updateSearchResult(targetTool.resultMap, resultMap?.searchResult);
  const nextAnswer = resultMap?.answer || "";

  // 总结阶段的最终包通常会带完整 answer，不能继续追加，否则会把整段总结再拼一遍。
  if (!nextAnswer) {
    return;
  }

  if (resultMap?.isFinal) {
    const currentAnswer = targetTool.resultMap.answer || "";
    if (nextAnswer === currentAnswer) {
      return;
    }

    targetTool.resultMap.answer = nextAnswer.startsWith(currentAnswer)
      ? nextAnswer
      : `${currentAnswer}${nextAnswer}`;
    return;
  }

  targetTool.resultMap.answer += nextAnswer;
}

/**
 * 添加新工具到现有任务
 */
function addNewToolToExistingTask(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  eventData: MESSAGE.EventData
) {
  const resultMap = eventData.resultMap.resultMap;

  resultMap.answer = resultMap?.answer || "";
  ensureSearchResult(resultMap);

  currentChat.multiAgent.tasks[taskIndex].push(buildTaskFromEventData(eventData));
}

/**
 * 添加新任务
 */
function addNewTask(currentChat: CHAT.ChatItem, eventData: MESSAGE.EventData) {
  const resultMap = eventData.resultMap.resultMap;

  resultMap.answer = resultMap?.answer || "";
  ensureSearchResult(resultMap);

  currentChat.multiAgent.tasks.push([
    buildTaskFromEventData(eventData),
  ]);
}

/**
 * 更新搜索结果
 */
function updateSearchResult(target: MESSAGE.ResultMap, source?: MESSAGE.SearchResult) {
  if (source?.query?.length) {
    target.searchResult!.query = source.query;
  }
  if (source?.docs?.length) {
    target.searchResult!.docs = source.docs;
  }
}

/**
 * 确保搜索结果存在
 */
function ensureSearchResult(resultMap: MESSAGE.ResultMap) {
  if (resultMap.searchResult) {
    resultMap.searchResult.query = resultMap.searchResult?.query || [];
    resultMap.searchResult.docs = resultMap.searchResult?.docs || [];
  } else {
    resultMap.searchResult = {
      query: [],
      docs: []
    };
  }
}

type TimelineTaskContainer = {
  hidden: boolean;
  task?: string;
  children: CHAT.Task[];
  __placeholder?: boolean;
} & Partial<MESSAGE.Task>;

/**
 * 某些任务组里工具事件会早于 task 父节点到达，
 * 这里先创建一个临时容器承接左侧时间线子项，避免查询分解等阶段被直接丢弃。
 */
function ensureTimelineTaskContainer(
  taskGroup: TimelineTaskContainer[],
  task?: MESSAGE.Task
): TimelineTaskContainer {
  const lastContainer = taskGroup[taskGroup.length - 1];
  if (lastContainer) {
    return lastContainer;
  }

  const placeholder: TimelineTaskContainer = {
    hidden: false,
    task: task?.task || "",
    taskId: task?.taskId,
    messageId: task?.messageId,
    messageTime: task?.messageTime,
    children: [],
    __placeholder: true,
  };
  taskGroup.push(placeholder);
  return placeholder;
}

/**
 * task 父节点晚到时，用正式 task 信息回填占位容器，
 * 保留之前已经挂上的工具子项。
 */
function upsertTimelineTaskContainer(
  taskGroup: TimelineTaskContainer[],
  task: MESSAGE.Task
): TimelineTaskContainer {
  const lastContainer = taskGroup[taskGroup.length - 1];
  if (lastContainer?.__placeholder) {
    const children = lastContainer.children || [];
    Object.assign(lastContainer, {
      ...task,
      task: task.task,
      hidden: false,
      children,
    });
    delete lastContainer.__placeholder;
    return lastContainer;
  }

  const nextContainer: TimelineTaskContainer = {
    ...task,
    task: task.task,
    hidden: false,
    children: [],
  };
  taskGroup.push(nextContainer);
  return nextContainer;
}

function handleNonStreamingMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number,
) {
  const nextTask = buildTaskFromEventData(eventData);

  if (taskIndex !== -1) {
    const taskGroup = currentChat.multiAgent.tasks[taskIndex];

    if (isImageGenerationToolResultTask(nextTask)) {
      const fileTaskIndex = findLastTaskIndex(taskGroup, isImageGenerationFileTask);
      if (fileTaskIndex !== -1) {
        taskGroup[fileTaskIndex] = mergeImageGenerationToolTask(
          nextTask,
          taskGroup[fileTaskIndex]
        );
        return;
      }
    }

    if (isImageGenerationFileTask(nextTask)) {
      const toolTaskIndex = findLastTaskIndex(taskGroup, isImageGenerationToolResultTask);
      if (toolTaskIndex !== -1) {
        taskGroup[toolTaskIndex] = mergeImageGenerationToolTask(
          taskGroup[toolTaskIndex],
          nextTask
        );
        return;
      }
    }

    taskGroup.push(nextTask);
  } else {
    currentChat.multiAgent.tasks.push([
      nextTask,
    ]);
  }

}

/**
 * 处理多智能体任务数据，整合聊天、计划和任务信息
 * @param currentChat 当前聊天对象
 * @param deepThink 深度思考
 * @param multiAgent 多智能体数据
 * @returns 处理后的数据对象
 */
export const handleTaskData = (
  currentChat: CHAT.ChatItem,
  deepThink?: boolean,
  multiAgent?: MESSAGE.MultiAgent
) => {
  const {
    plan: fullPlan,
    tasks: fullTasks,
    plan_thought: planThought,
  } = multiAgent ?? {};

  const TOOL_TYPES = [
    "tool_result",
    "browser",
    "code",
    "html",
    "file",
    "knowledge",
    "result",
    "deep_search",
    "markdown",
    "ppt",
    "data_analysis",
  ];

  currentChat.thought = planThought || "";

  let requestConclusion: MESSAGE.Task | CHAT.Task | undefined;
  let fallbackTaskSummary: MESSAGE.Task | CHAT.Task | undefined;
  let plan = fullPlan;
  const taskList: CHAT.Task[] = [];

  const validTasks: MESSAGE.Task[][] = fullTasks?.filter(
    (item: MESSAGE.Task[]) => item && item?.length > 0
  ) ?? [];

  const chatList: TimelineTaskContainer[][] = !deepThink
    ? [
      [
        {
          hidden: false,
          task: "",
          children: [],
        },
      ],
    ]
    : Array.from({ length: validTasks?.length || 0 }, () => []);

  validTasks?.forEach((taskGroup, groupIndex) => {
    const timelineTaskGroup = chatList[groupIndex];

    taskGroup?.forEach((task, taskIndex) => {
      const time = task.messageTime;
      const id = time?.concat(String(taskIndex));

      const isCodeOutputOnly =
        task?.messageType === "code" &&
        (task.resultMap?.codeOutput || !task.resultMap?.code);

      const processedInfo = processTaskForRender(task, id);

      if (task.messageType === "task") {
        upsertTimelineTaskContainer(timelineTaskGroup, task);
      // 深度研究里的 task_summary 属于任务级总结，必须保留在时间线中；
      // 只有请求级 result 才应该落在底部最终结论区。
      } else if (
        task?.messageType !== "result" &&
        !isCodeOutputOnly
      ) {
        ensureTimelineTaskContainer(timelineTaskGroup, task).children.push(...processedInfo);
      }

      if (
        TOOL_TYPES.includes(task?.messageType) &&
        !isCodeOutputOnly
      ) {
        taskList.push(...processedInfo);
      }

      if (task?.messageType === "plan") {
        plan = task.plan;
      }

      if (task?.messageType === "result") {
        requestConclusion = task;
      } else if (task?.messageType === "task_summary") {
        fallbackTaskSummary = task;
      }
    });
  });

  const streamConclusion =
    currentChat.conclusion?.messageType === "agent_stream"
      ? currentChat.conclusion
      : undefined;

  currentChat.tasks = chatList as unknown as CHAT.Task[][];
  currentChat.plan = plan;
  currentChat.conclusion =
    (requestConclusion as CHAT.Task | undefined) ||
    streamConclusion ||
    (!currentChat.loading
      ? (fallbackTaskSummary as CHAT.Task | undefined)
      : undefined);
  currentChat.planList = plan?.stages?.reduce(
    (result: CHAT.PlanItem[], stage: string, index: number) => {
      const group = result.find((item) => item.name === stage);
      if (group) {
        group.list.push(plan?.steps[index] || "");
      } else {
        result.push({
          name: stage,
          list: [plan?.steps[index] || ""],
        });
      }
      return result;
    },
    []
  );

  return {
    currentChat,
    plan,
    taskList,
    chatList: chatList as unknown as CHAT.Task[][],
  };
};

function getTaskRenderSignature(task: any, baseId: string): string {
  const resultMap = task.resultMap || {};
  const searchResult = resultMap.searchResult;
  const plan = task.plan;
  const artifactRefs = Array.isArray(task.artifactRefs) ? task.artifactRefs : [];
  const querySignature = Array.isArray(searchResult?.query)
    ? searchResult.query.join("||")
    : "";
  const docsSignature = Array.isArray(searchResult?.docs)
    ? searchResult.docs
      .map((docs: MESSAGE.Doc[] | MESSAGE.Doc) => (Array.isArray(docs) ? docs.length : 0))
      .join(",")
    : "";

  return [
    baseId,
    task.messageId || "",
    task.messageType || "",
    task.messageTime || "",
    resultMap.messageType || "",
    resultMap.isFinal ? "1" : "0",
    resultMap.searchFinish ? "1" : "0",
    task.toolThought?.length || 0,
    resultMap.answer?.length || 0,
    resultMap.codeOutput?.length || 0,
    resultMap.data?.length || 0,
    artifactRefs.length,
    artifactRefs[0]?.resourceKey || artifactRefs[0]?.previewUrl || artifactRefs[0]?.downloadUrl || "",
    querySignature,
    docsSignature,
    Array.isArray(plan?.stepStatus) ? plan.stepStatus.join(",") : "",
  ].join("|");
}

function clonePlanForRender(plan?: MESSAGE.Plan) {
  if (!plan) {
    return plan;
  }

  return {
    ...plan,
    notes: [...(plan.notes || [])],
    stages: [...(plan.stages || [])],
    stepStatus: [...(plan.stepStatus || [])],
    steps: [...(plan.steps || [])],
  };
}

function cloneSearchResultSnapshot(searchResult?: MESSAGE.SearchResult) {
  if (!searchResult) {
    return searchResult;
  }

  return {
    ...searchResult,
    query: [...(searchResult.query || [])],
    docs: (searchResult.docs || []).map((item) =>
      Array.isArray(item) ? [...item] : item
    ),
  };
}

function cloneResultMapSnapshot(
  resultMap?: MESSAGE.ResultMap
): MESSAGE.ResultMap {
  if (!resultMap) {
    return {} as MESSAGE.ResultMap;
  }

  return {
    ...resultMap,
    searchResult: cloneSearchResultSnapshot(resultMap.searchResult),
    fileInfo: [...(resultMap.fileInfo || [])],
    fileList: [...(resultMap.fileList || [])],
    refList: [...(resultMap.refList || [])],
    steps: [...(resultMap.steps || [])],
  };
}

function cloneTaskSnapshot(task: MESSAGE.Task): MESSAGE.Task {
  return {
    ...task,
    plan: clonePlanForRender(task.plan),
    resultMap: cloneResultMapSnapshot(task.resultMap),
    toolResult: task.toolResult
      ? {
        ...task.toolResult,
        toolParam: task.toolResult.toolParam
          ? { ...task.toolResult.toolParam }
          : task.toolResult.toolParam,
      }
      : task.toolResult,
  };
}

/**
 * 为当前会话快照重建工作区任务数据。
 * 这里统一把缓存下来的任务结果重新整理成界面消费结构，
 * 避免组件层直接依赖流式过程中产生的临时对象形态。
 */
export const buildConversationTaskData = (
  chat: CHAT.ChatItem,
  deepThink?: boolean
) => {
  const snapshotChat = {
    ...chat,
    files: [...(chat.files || [])],
    tasks: [],
    multiAgent: {
      ...chat.multiAgent,
      plan: clonePlanForRender(chat.multiAgent?.plan),
      tasks: (chat.multiAgent?.tasks || []).map((group) =>
        group.map((task) => cloneTaskSnapshot(task))
      ),
    },
    timeline: [...(chat.timeline || [])],
  } as CHAT.ChatItem;

  return handleTaskData(snapshotChat, deepThink, snapshotChat.multiAgent);
};

function createRenderTask(
  task: any,
  id: string,
  searchResult?: {
    query: string;
    docs: MESSAGE.Doc[];
  }
): CHAT.Task {
  const nextTask = {
    ...task,
    id,
    resultMap: task.resultMap ? { ...task.resultMap } : task.resultMap,
    plan: clonePlanForRender(task.plan),
  } as CHAT.Task;

  if (searchResult && nextTask.resultMap) {
    nextTask.resultMap.searchResult = searchResult as any;
  }

  return nextTask;
}

function processTaskForRender(task: any, baseId: string): CHAT.Task[] {
  const signature = getTaskRenderSignature(task, baseId);
  const cached = taskRenderCache.get(task);
  if (cached?.signature === signature) {
    return cached.items;
  }

  let items: CHAT.Task[];
  if (task.messageType === "deep_search") {
    items = processDeepSearchTask(task, baseId);
  } else {
    items = [createRenderTask(task, baseId)];
  }

  taskRenderCache.set(task, {
    signature,
    items,
  });

  return items;
}

/**
 * 处理深度搜索任务的辅助函数
 * @param task 深度搜索任务
 * @param baseId 基础ID
 * @returns 处理后的任务信息数组
 */
function processDeepSearchTask(task: any, baseId: string): CHAT.Task[] {
  const messageType = task.resultMap?.messageType;
  if (messageType === "report") {
    return [
      createRenderTask(task, baseId),
    ];
  }

  if (messageType === "extend" || messageType === "search") {
    const queries = task.resultMap?.searchResult?.query || [];

    // 查询分解和检索阶段都按 query 拆分；没有真实 query 时不制造伪占位项。
    if (!queries.length) {
      return [];
    }

    return queries.map((query: string, index: number) => {
      const rawDocs = task.resultMap.searchResult?.docs?.[index];
      const searchResult = {
        query: query,
        docs: Array.isArray(rawDocs) ? rawDocs : rawDocs ? [rawDocs] : [],
      };

      return createRenderTask(task, baseId.concat(String(index)), searchResult);
    });
  }

  return [
    createRenderTask(task, baseId),
  ];
}

/**
 * 构建任务动作信息
 * @param task 任务对象
 * @returns 包含action、tool和name的动作信息对象
 */
export const buildAction = (task: CHAT.Task) => {
  // 定义消息类型常量
  const MESSAGE_TYPES = {
    TOOL_RESULT: "tool_result",
    CODE: "code",
    HTML: "html",
    PLAN_THOUGHT: "plan_thought",
    PLAN: "plan",
    FILE: "file",
    KNOWLEDGE: "knowledge",
    DEEP_SEARCH: "deep_search",
    MARKDOWN: "markdown",
    DATA_ANALYSIS: "data_analysis"
  };

  const TOOL_NAMES = {
    WEB_SEARCH: "web_search",
    INTERNAL_SEARCH: "internal_search",
    CODE_INTERPRETER: "code_interpreter"
  };

  switch (task.messageType) {
    case MESSAGE_TYPES.TOOL_RESULT:
      return handleToolResult(task);

    case MESSAGE_TYPES.CODE:
      return {
        action: "正在执行代码",
        tool: "编辑器",
        name: ""
      };

    case MESSAGE_TYPES.HTML:
      return {
        action: "正在生成web页面",
        tool: "编辑器",
        name: ""
      };

    case MESSAGE_TYPES.PLAN_THOUGHT:
      return {
        action: "正在思考下一步计划",
        tool: "",
        name: ""
      };

    case MESSAGE_TYPES.PLAN:
      return {
        action: "更新任务列表",
        tool: "",
        name: ""
      };

    case MESSAGE_TYPES.FILE:
      return handleFileTask(task);

    case MESSAGE_TYPES.KNOWLEDGE:
      return {
        action: "正在调用知识库",
        tool: "文件编辑器",
        name: "查询知识库"
      };

    case MESSAGE_TYPES.DEEP_SEARCH:
      return handleDeepSearchTask(task);

    case MESSAGE_TYPES.MARKDOWN:
      return {
        action: "正在生成报告",
        tool: "markdown",
        name: ""
      };

    case MESSAGE_TYPES.DATA_ANALYSIS:
      return {
        action: "正在分析数据",
        tool: "数据分析工具",
        name: task.resultMap.task
      };

    default:
      return {
        action: "正在调用工具",
        tool: task?.messageType || "",
        name: ""
      };
  }

  /**
   * 处理工具结果类型的任务
   * @param task 任务对象
   * @returns 动作信息对象
   */
  function handleToolResult(task: CHAT.Task) {
    const toolName = task?.toolResult?.toolName;
    const primaryFile = getPrimaryTaskFile(task);

    switch (toolName) {
      case TOOL_NAMES.WEB_SEARCH:
      case TOOL_NAMES.INTERNAL_SEARCH:
        return {
          action: "正在搜索",
          tool: "网络查询",
          name: task?.toolResult?.toolParam?.query || ""
        };

      case TOOL_NAMES.CODE_INTERPRETER:
        return {
          action: "正在执行代码",
          tool: "编辑器",
          name: "执行代码"
        };

      case "image_generation_tool":
        return {
          action: "生成图片",
          tool: "图片生成",
          name: primaryFile?.name || toolName
        };

      default:
        return {
          action: "正在调用工具",
          tool: toolName || "",
          name: toolName || ""
        };
    }
  }

  /**
   * 处理文件类型的任务
   * @param task 任务对象
   * @returns 动作信息对象
   */
  function handleFileTask(task: any) {
    const primaryFile = getPrimaryTaskFile(task);
    return {
      action: task?.resultMap?.command || "",
      tool: "文件编辑器",
      name: primaryFile?.name || ""
    };
  }

  /**
   * 处理深度搜索类型的任务
   * @param task 任务对象
   * @returns 动作信息对象
   */
  function handleDeepSearchTask(task: any) {
    const stage = resolveDeepSearchStage(task?.resultMap?.messageType);
    const queryText =
      stage === "report"
        ? formatDeepSearchQueryText(task?.resultMap?.query) ||
          formatDeepSearchQueryText(task?.resultMap?.searchResult?.query)
        : formatDeepSearchQueryText(task?.resultMap?.searchResult?.query);

    return {
      action: resolveDeepSearchActionText(stage, task?.resultMap?.isFinal),
      tool: "深度搜索",
      name: queryText
    };
  }
};

export enum IconType {
  PLAN = 'plan',
  PLAN_THOUGHT = 'plan_thought',
  TOOL_RESULT = 'tool_result',
  BROWSER = 'browser',
  FILE = 'file',
  DEEP_SEARCH = 'deep_search',
  CODE = 'code',
  HTML = 'html',
}

/**
 * 图标映射表
 */
const ICON_MAP: Record<IconType, string> = {
  [IconType.PLAN]: 'icon-renwu',
  [IconType.PLAN_THOUGHT]: 'icon-juli',
  [IconType.TOOL_RESULT]: 'icon-tiaoshi',
  [IconType.BROWSER]: 'icon-sousuo',
  [IconType.FILE]: 'icon-bianji',
  [IconType.DEEP_SEARCH]: 'icon-sousuo',
  [IconType.CODE]: 'icon-daima',
  [IconType.HTML]: 'icon-daima',
};

/**
 * 默认图标
 */
const DEFAULT_ICON = 'icon-tiaoshi';

/**
 * 根据指定的类型获取对应的图标名称
 * @param type - 图标类型，可以是 IconType 枚举中的值或其他字符串
 * @returns 对应的图标名称，如果类型不存在则返回默认图标
 */
export const getIcon = (type: string): string => {
  if (type in ICON_MAP) {
    return ICON_MAP[type as IconType];
  }
  return DEFAULT_ICON;
};

export const buildAttachment = (fileList?: CHAT.FileList[]): CHAT.TFile[] => {
  if (!Array.isArray(fileList) || !fileList.length) {
    return [];
  }

  return fileList
    .map((item) => normalizeTaskFile(item))
    .filter((item): item is CHAT.TFile => Boolean(item));
};
