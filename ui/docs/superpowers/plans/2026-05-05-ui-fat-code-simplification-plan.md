# UI Fat Code Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变前端可观察行为的前提下，分阶段拆解 `ui/` 中职责失控的大文件，优先收敛聊天主链路、图片工作区和通用输入组件的维护复杂度。

**Architecture:** 本次不做功能增强，也不做全仓重写；采用“先补保护性测试，再抽 Hook / projector / renderer / section 子模块”的方式，把页面级状态机、协议解析和展示拼装从单文件中拆开。每一阶段都要求输出可独立验证的最小闭环，避免一次性大手术导致聊天协议、流式渲染或附件行为回归。

**Tech Stack:** React 19、TypeScript 5、Vite 6、Vitest、Ant Design 5、Radix UI、现有 `ui/src/utils/chat.ts` 投影链与 `querySSE` 流式链路。

---

## Plan Relationship

- 本计划聚焦第一阶段的聊天主链路、图片工作区、`prompt-input` 与 `Dialogue` 拆分。
- `2026-05-05-ui-frontend-debt-governance-plan.md` 负责后续更高层的入口状态机、MRag、DataChat 与预览渲染治理。
- 执行顺序以债务治理计划里的 `Phase Gate` 为准；只有当前在途聊天核心与图片工作区基线恢复为绿色后，才进入后续波次。

## Follow-up Status

- 聊天主链路、首页、输入组件、MRag、DataChat 与预览渲染的第一轮治理已按 Phase Gate 顺序落地。
- 当前剩余债务应继续遵守“主页面只做装配、派生逻辑优先进入纯函数或 hook”这一边界。

## Debt Governance Invariants

- 不改变 SSE 协议字段名、任务 `messageType` 语义和对话持久化消费格式。
- 不改变首页默认进入的产品类型和角色兜底规则。
- 不改变附件上传接口、上传完成后的 `CHAT.TFile` 结构。
- 不改变图片生成请求参数默认值和 `WorkspaceImageGeneration` 的会话行为。
- `prompt-input` 与 `Dialogue` 仅做职责拆分，不改变现有 JSX 出口和用户可见交互。

## Phase Gate

- `src/components/ChatView/useConversationStream.ts`
- `src/utils/chat.ts`
- `src/components/Dialogue/index.tsx`
- `src/components/ActionPanel/useContent.ts`
- `src/pages/WorkspaceImageGeneration/index.tsx`

只有以上在途改动对应测试、lint 与 build 恢复为绿色后，才进入后续债务治理任务。

---

## File Structure

### 第一阶段目标文件边界

- Modify: `ui/src/components/ChatView/index.tsx`
  - 当前承担会话状态、SSE 消费、草稿同步、右侧工作区、面板拖拽与折叠。
  - 计划拆出流式会话控制与布局控制，保留页面级编排。
- Create: `ui/src/components/ChatView/useConversationStream.ts`
  - 承担聊天/多智能体 SSE 消费、节流刷新、会话草稿提交、错误/关闭处理。
- Create: `ui/src/components/ChatView/useWorkspacePanels.ts`
  - 承担左右面板宽度、拖拽、折叠与跟随状态。
- Create: `ui/src/components/ChatView/chatView.types.ts`
  - 承担 `ChatView` 内部拆分后仍需复用的局部类型，避免继续把内部约定堆回主文件。
- Test: `ui/src/components/ChatView/useConversationStream.test.ts`
  - 覆盖关键流式状态转换与错误兜底。

- Modify: `ui/src/utils/chat.ts`
  - 当前既是协议解析器，也是 timeline/projector/render snapshot 组装器。
- Create: `ui/src/utils/chat/planner.ts`
  - 承担 plan / plan_thought / planner round 维护。
- Create: `ui/src/utils/chat/toolCalls.ts`
  - 承担 tool_call、tool_thought、文件/图片工具结果归并。
- Create: `ui/src/utils/chat/timeline.ts`
  - 承担 timeline task container / group upsert。
- Create: `ui/src/utils/chat/renderTasks.ts`
  - 承担 render snapshot 构建与 deep search render task 派生。
- Create: `ui/src/utils/chat/index.ts`
  - 统一对外导出，保持旧调用方 import 兼容。
- Test: `ui/src/utils/chat.test.ts`
  - 迁移并补齐回归用例，保证协议行为不变。

- Modify: `ui/src/pages/WorkspaceImageGeneration/index.tsx`
  - 当前承担配置、历史、图片编辑器、蒙版、解码器、批处理等全部行为。
- Create: `ui/src/pages/WorkspaceImageGeneration/useImageGenerationConfig.ts`
  - 承担本地配置读取、持久化与状态归一。
- Create: `ui/src/pages/WorkspaceImageGeneration/useImageEditor.ts`
  - 承担编辑图片列表、当前图片、蒙版同步、绘制事件绑定。
- Create: `ui/src/pages/WorkspaceImageGeneration/useImageGenerationHistory.ts`
  - 承担历史批次分页、加载更多、错误状态。
- Create: `ui/src/pages/WorkspaceImageGeneration/useImageGenerationSession.ts`
  - 承担 prompt、消息、发送、批量生成、debug payload。
- Test: `ui/src/pages/WorkspaceImageGeneration/batch.test.ts`
  - 复用现有批处理测试。
- Create: `ui/src/pages/WorkspaceImageGeneration/useImageEditor.test.ts`
  - 覆盖蒙版/对象 URL 生命周期。

- Modify: `ui/src/components/ai-elements/prompt-input.tsx`
  - 当前 provider、本地附件、drop、paste、voice、submit 都在一个文件内。
- Create: `ui/src/components/ai-elements/prompt-input/attachments.ts`
  - 承担附件 accept / size / count 校验与对象 URL 生命周期。
- Create: `ui/src/components/ai-elements/prompt-input/usePromptInputAttachments.ts`
  - 承担 provider / local 双模式附件管理。
- Create: `ui/src/components/ai-elements/prompt-input/usePromptInputDrop.ts`
  - 承担局部 / 全局拖拽上传。
- Create: `ui/src/components/ai-elements/prompt-input/useSpeechRecognition.ts`
  - 承担语音输入能力探测与生命周期。
- Create: `ui/src/components/ai-elements/prompt-input/index.tsx`
  - 作为新的主入口，保留当前导出接口。
- Test: `ui/src/components/ai-elements/prompt-input/attachments.test.ts`
  - 覆盖 accept、maxFiles、maxFileSize、URL 回收。

- Modify: `ui/src/components/Dialogue/index.tsx`
  - 当前包含计划卡、时间线、复制动作、版本游标等多个子视图。
- Create: `ui/src/components/Dialogue/PlanSection.tsx`
  - 承担计划卡展示。
- Create: `ui/src/components/Dialogue/Timeline.tsx`
  - 承担 timeline 展示。
- Create: `ui/src/components/Dialogue/MessageToolbar.tsx`
  - 承担复制、重试、更多动作。
- Test: `ui/src/components/Dialogue/plannerHistory.test.ts`
  - 现有测试保留。
- Create: `ui/src/components/Dialogue/Timeline.test.tsx`
  - 覆盖完成态图标、loading 态、deep search preview 边界。

### 暂不进入第一阶段

- `ui/src/components/DataChat/ChartUtils.js`
  - 风险高但与聊天主链路解耦，单列为第二阶段历史债治理，不纳入当前计划落地。
- `ui/src/pages/WorkspaceMRag/index.tsx`
  - 明显偏胖，但与当前最核心聊天入口相比优先级略低，放在第一阶段收尾后评估。
- `ui/src/components/ActionPanel/ActionPanel.tsx`
  - 现阶段先观察，不在本计划中拆分。

---

### Task 1: 锁定重构范围与基线验证

**Files:**
- Modify: `ui/docs/superpowers/plans/2026-05-05-ui-fat-code-simplification-plan.md`
- Test: `ui/src/utils/chat.test.ts`
- Test: `ui/src/components/Dialogue/plannerHistory.test.ts`
- Test: `ui/src/pages/WorkspaceImageGeneration/batch.test.ts`

- [x] **Step 1: 记录第一阶段边界与不变量**

## 第一阶段不变量

- 不修改接口契约、SSE 协议字段名、会话数据结构对外形态。
- 不修改聊天模式 / 深度思考 / 深度研究的功能入口。
- 不修改图片生成请求参数默认值。
- 不修改 PromptInput 对外导出名与现有使用方 JSX 结构。

- [ ] **Step 2: 运行现有测试建立基线**

Run: `cd ui && npm run test -- src/utils/chat.test.ts src/components/Dialogue/plannerHistory.test.ts src/pages/WorkspaceImageGeneration/batch.test.ts`

Expected: `PASS`，至少上述 3 个测试文件全部通过。

- [ ] **Step 3: 运行 lint 建立基线**

Run: `cd ui && npm run lint`

Expected: 若已有历史告警，记录在实施备注中；不得引入新的错误级问题。

- [ ] **Step 4: 提交基线确认**

```bash
git add ui/docs/superpowers/plans/2026-05-05-ui-fat-code-simplification-plan.md
git commit -m "docs: add ui fat code simplification plan"
```

---

### Task 2: 拆分 `utils/chat.ts` 的 planner / tool / timeline / render 责任

**Files:**
- Create: `ui/src/utils/chat/planner.ts`
- Create: `ui/src/utils/chat/toolCalls.ts`
- Create: `ui/src/utils/chat/timeline.ts`
- Create: `ui/src/utils/chat/renderTasks.ts`
- Create: `ui/src/utils/chat/index.ts`
- Modify: `ui/src/utils/chat.ts`
- Test: `ui/src/utils/chat.test.ts`

- [ ] **Step 1: 为 planner round 行为补保护性测试**

```ts
it("plan_thought 非 final 时应追加到同一 plannerRound", () => {
  const chat = createChatItem(createDeepSearchTask("search"));
  chat.multiAgent.plannerRounds = [];

  combineData({
    messageType: "plan_thought",
    messageId: "thought-msg-1",
    taskId: "planner-task-1",
    resultMap: {
      plannerRoundId: "round-1",
      planThought: "第一段",
      isFinal: false,
    },
  } as unknown as MESSAGE.EventData, chat);

  combineData({
    messageType: "plan_thought",
    messageId: "thought-msg-1",
    taskId: "planner-task-1",
    resultMap: {
      plannerRoundId: "round-1",
      planThought: "第二段",
      isFinal: false,
    },
  } as unknown as MESSAGE.EventData, chat);

  expect(chat.multiAgent.plannerRounds?.[0]?.planThought).toBe("第一段第二段");
});
```

- [ ] **Step 2: 先抽 planner 相关纯函数**

```ts
// ui/src/utils/chat/planner.ts
export function ensurePlannerRounds(currentChat: CHAT.ChatItem) {
  if (!Array.isArray(currentChat.multiAgent.plannerRounds)) {
    currentChat.multiAgent.plannerRounds = [];
  }
  return currentChat.multiAgent.plannerRounds;
}

export function syncLatestPlannerAlias(currentChat: CHAT.ChatItem) {
  const plannerRounds = currentChat.multiAgent.plannerRounds || [];
  const latestRound = plannerRounds[plannerRounds.length - 1];
  if (!latestRound) {
    return;
  }
  currentChat.multiAgent.plan_thought = latestRound.planThought;
  currentChat.multiAgent.plan = latestRound.plan;
  currentChat.thought = latestRound.planThought || "";
}
```

- [ ] **Step 3: 抽 tool call / tool thought / file merge 逻辑**

```ts
// ui/src/utils/chat/toolCalls.ts
export function resolveTaskToolCallId(task?: Partial<MESSAGE.Task> | Partial<CHAT.Task>) {
  if (!task) {
    return "";
  }
  return task.resultMap?.toolCallId || task.toolResult?.toolCallId || "";
}

export function mergeTaskArtifactRefs(
  targetTask: MESSAGE.Task | undefined,
  eventData?: MESSAGE.EventData
) {
  if (!targetTask || !Array.isArray(eventData?.artifactRefs) || !eventData?.artifactRefs.length) {
    return;
  }
  targetTask.artifactRefs = [...(targetTask.artifactRefs || []), ...eventData.artifactRefs];
}
```

- [ ] **Step 4: 抽 timeline / renderTasks 逻辑并由 `chat.ts` 转发**

```ts
// ui/src/utils/chat.ts
export {
  buildTaskFromEventData,
  combineData,
  getStableTaskIdentity,
  handleTaskData,
  buildConversationTaskData,
  buildAction,
} from "./chat/index";
```

- [ ] **Step 5: 运行 `chat.ts` 相关测试**

Run: `cd ui && npm run test -- src/utils/chat.test.ts`

Expected: `PASS`，原有 deep search / tool call / html artifact / identity 用例保持通过。

- [ ] **Step 6: 运行 lint 验证模块拆分后导入正确**

Run: `cd ui && npm run lint`

Expected: `PASS`，无循环依赖式临时 hack、无未使用导出。

- [ ] **Step 7: 提交 `chat` 模块拆分**

```bash
git add ui/src/utils/chat.ts ui/src/utils/chat ui/src/utils/chat.test.ts
git commit -m "refactor: split chat projection utilities"
```

---

### Task 3: 拆分 `ChatView` 的流式会话控制与布局状态

**Files:**
- Create: `ui/src/components/ChatView/useConversationStream.ts`
- Create: `ui/src/components/ChatView/useWorkspacePanels.ts`
- Create: `ui/src/components/ChatView/chatView.types.ts`
- Modify: `ui/src/components/ChatView/index.tsx`
- Test: `ui/src/components/ChatView/useConversationStream.test.ts`
- Test: `ui/src/utils/chat.test.ts`

- [ ] **Step 1: 为多智能体流式兜底错误补测试**

```ts
it("guard error 应将当前 chat 标记为 FAILED 并生成 conclusion", () => {
  const currentChat = {
    requestId: "req-1",
    loading: true,
    multiAgent: { tasks: [] },
    metrics: {},
  } as unknown as CHAT.ChatItem;

  const next = applyGuardError(currentChat, "当前请求处理失败，请稍后重试");

  expect(next.loading).toBe(false);
  expect(next.metrics?.status).toBe("FAILED");
  expect(next.conclusion?.messageType).toBe("task_summary");
});
```

- [ ] **Step 2: 抽出流式运行时控制 Hook**

```ts
// ui/src/components/ChatView/useConversationStream.ts
export function useConversationStream(options: UseConversationStreamOptions) {
  const [taskList, setTaskList] = useState<CHAT.Task[]>([]);
  const [activeRunState, setActiveRunState] = useState<ActiveRunState>();
  const [plan, setPlan] = useState<CHAT.Plan>();
  const [loading, setLoading] = useState(false);
  const [streamingThoughtMap, setStreamingThoughtMap] = useState<Record<string, string>>({});

  return {
    taskList,
    activeRunState,
    plan,
    loading,
    streamingThoughtMap,
    sendMessage,
    regenerateLastMessage,
  };
}
```

- [ ] **Step 3: 抽出左右面板状态 Hook**

```ts
// ui/src/components/ChatView/useWorkspacePanels.ts
export function useWorkspacePanels() {
  const [leftPanelWidth, setLeftPanelWidth] = useState(50);
  const [isDragging, setIsDragging] = useState(false);
  const [isLeftCollapsed, setIsLeftCollapsed] = useState(false);
  const [isRightCollapsed, setIsRightCollapsed] = useState(false);

  return {
    leftPanelWidth,
    isDragging,
    isLeftCollapsed,
    isRightCollapsed,
    handleDragStart,
    setIsLeftCollapsed,
    setIsRightCollapsed,
  };
}
```

- [ ] **Step 4: 让 `ChatView` 退化为编排组件**

```tsx
// ui/src/components/ChatView/index.tsx
const stream = useConversationStream({
  conversation,
  product,
  inputInfo: inputInfoProp,
  onConversationChange,
  onInputConsumed,
});

const panels = useWorkspacePanels();
```

- [ ] **Step 5: 运行流式与聊天投影相关测试**

Run: `cd ui && npm run test -- src/components/ChatView/useConversationStream.test.ts src/utils/chat.test.ts`

Expected: `PASS`，确保流式控制与投影链协同未回归。

- [ ] **Step 6: 运行构建验证类型未破坏**

Run: `cd ui && npm run build`

Expected: `vite build` 成功，`tsc -b` 无类型错误。

- [ ] **Step 7: 提交 `ChatView` 拆分**

```bash
git add ui/src/components/ChatView ui/src/utils/chat.test.ts
git commit -m "refactor: split chat view stream and panel state"
```

---

### Task 4: 拆分 `Dialogue` 视图子块，降低单文件展示复杂度

**Files:**
- Create: `ui/src/components/Dialogue/PlanSection.tsx`
- Create: `ui/src/components/Dialogue/Timeline.tsx`
- Create: `ui/src/components/Dialogue/MessageToolbar.tsx`
- Create: `ui/src/components/Dialogue/Timeline.test.tsx`
- Modify: `ui/src/components/Dialogue/index.tsx`
- Test: `ui/src/components/Dialogue/plannerHistory.test.ts`
- Test: `ui/src/components/Dialogue/timelineStatus.test.ts`

- [ ] **Step 1: 为时间线完成态图标补单测**

```tsx
it("最后一组任务在 loading=false 且全部完成时显示完成图标", () => {
  const { getByLabelText } = render(
    <Timeline
      tasks={[[{ task: "收集资料", status: "completed", children: [] } as unknown as CHAT.Task]]}
      loading={false}
      deepThink={true}
    />
  );

  expect(getByLabelText("timeline-completed")).toBeTruthy();
});
```

- [ ] **Step 2: 抽出 `PlanSection`**

```tsx
// ui/src/components/Dialogue/PlanSection.tsx
export const PlanSection = memo(function PlanSection(props: PlanSectionProps) {
  const normalizedPlan = useMemo(() => normalizePlanForDisplay(props.plan), [props.plan]);
  if (!normalizedPlan || !normalizedPlan.stages.length) {
    return null;
  }
  return <motion.div>{/* 保持原 JSX 结构 */}</motion.div>;
});
```

- [ ] **Step 3: 抽出 `Timeline` 与 `MessageToolbar`**

```tsx
// ui/src/components/Dialogue/MessageToolbar.tsx
export function MessageToolbar({ response, onRegenerate }: MessageToolbarProps) {
  const [copied, setCopied] = useState(false);
  return (
    <MessageActions className="ml-1 mt-1">
      {/* 复用原复制 / 重新生成 / 更多菜单行为 */}
    </MessageActions>
  );
}
```

- [ ] **Step 4: 收敛 `Dialogue/index.tsx` 为装配层**

```tsx
// ui/src/components/Dialogue/index.tsx
return (
  <Message>
    <MessageContent>{/* 主响应内容 */}</MessageContent>
    <PlanSection {...planSectionProps} />
    <Timeline {...timelineProps} />
    <MessageToolbar response={chat.response} onRegenerate={onRegenerate} />
  </Message>
);
```

- [ ] **Step 5: 运行 `Dialogue` 相关测试**

Run: `cd ui && npm run test -- src/components/Dialogue/plannerHistory.test.ts src/components/Dialogue/timelineStatus.test.ts src/components/Dialogue/Timeline.test.tsx`

Expected: `PASS`，planner history 和 timeline 状态判断无回归。

- [ ] **Step 6: 提交 `Dialogue` 子块拆分**

```bash
git add ui/src/components/Dialogue
git commit -m "refactor: split dialogue sections"
```

---

### Task 5: 拆分 `WorkspaceImageGeneration` 的配置、编辑器、历史与会话

**Files:**
- Create: `ui/src/pages/WorkspaceImageGeneration/useImageGenerationConfig.ts`
- Create: `ui/src/pages/WorkspaceImageGeneration/useImageEditor.ts`
- Create: `ui/src/pages/WorkspaceImageGeneration/useImageGenerationHistory.ts`
- Create: `ui/src/pages/WorkspaceImageGeneration/useImageGenerationSession.ts`
- Create: `ui/src/pages/WorkspaceImageGeneration/useImageEditor.test.ts`
- Modify: `ui/src/pages/WorkspaceImageGeneration/index.tsx`
- Test: `ui/src/pages/WorkspaceImageGeneration/batch.test.ts`

- [ ] **Step 1: 为对象 URL 清理与蒙版同步补测试**

```ts
it("卸载编辑器时会释放所有 objectUrl", () => {
  const revokeSpy = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
  const { unmount } = renderHook(() =>
    useImageEditor({
      initialImages: [
        { id: "img-1", objectUrl: "blob:img-1" } as unknown as EditorImageItem,
      ],
    })
  );

  unmount();
  expect(revokeSpy).toHaveBeenCalledWith("blob:img-1");
});
```

- [ ] **Step 2: 抽出配置 Hook**

```ts
// ui/src/pages/WorkspaceImageGeneration/useImageGenerationConfig.ts
export function useImageGenerationConfig() {
  const [config, setConfig] = useState<GenerationConfig>(() => loadStoredConfig());

  useEffect(() => {
    localStorage.setItem(IMAGE_GENERATION_STORAGE_KEY, JSON.stringify(config));
  }, [config]);

  return { config, setConfig };
}
```

- [ ] **Step 3: 抽出编辑器 Hook**

```ts
// ui/src/pages/WorkspaceImageGeneration/useImageEditor.ts
export function useImageEditor() {
  const [images, setImages] = useState<EditorImageItem[]>([]);
  const [editingImageId, setEditingImageId] = useState<string | null>(null);
  const [brushSize, setBrushSize] = useState(32);
  const [toolMode, setToolMode] = useState<"brush" | "eraser">("brush");

  return {
    images,
    editingImageId,
    brushSize,
    toolMode,
    addFiles,
    clearMask,
    removeImage,
  };
}
```

- [x] **Step 4: 抽出历史与生成会话 Hook，主页面只拼装**

```tsx
// ui/src/pages/WorkspaceImageGeneration/index.tsx
const { config, setConfig } = useImageGenerationConfig();
const editor = useImageEditor();
const history = useImageGenerationHistory({ config });
const session = useImageGenerationSession({ config, editor, history });
```

- [x] **Step 5: 运行图片工作区相关测试和构建**

Run: `cd ui && npm run test -- src/pages/WorkspaceImageGeneration/batch.test.ts src/pages/WorkspaceImageGeneration/useImageEditor.test.ts`

Expected: `PASS`，批处理和编辑器资源释放行为通过。

- [x] **Step 6: 运行构建，验证 hooks 拆分未引入浏览器端类型问题**

Run: `cd ui && npm run build`

Expected: `PASS`。

- [ ] **Step 7: 提交图片工作区拆分**

```bash
git add ui/src/pages/WorkspaceImageGeneration
git commit -m "refactor: split image generation workspace logic"
```

---

### Task 6: 拆分 `prompt-input.tsx` 的附件、拖拽与语音输入

**Files:**
- Create: `ui/src/components/ai-elements/prompt-input/attachments.ts`
- Create: `ui/src/components/ai-elements/prompt-input/usePromptInputAttachments.ts`
- Create: `ui/src/components/ai-elements/prompt-input/usePromptInputDrop.ts`
- Create: `ui/src/components/ai-elements/prompt-input/useSpeechRecognition.ts`
- Create: `ui/src/components/ai-elements/prompt-input/attachments.test.ts`
- Create: `ui/src/components/ai-elements/prompt-input/index.tsx`
- Modify: `ui/src/components/ai-elements/prompt-input.tsx`

- [ ] **Step 1: 为附件校验补测试**

```ts
it("不符合 accept 的文件应触发 accept 错误", () => {
  const onError = vi.fn();
  const files = [new File(["abc"], "demo.exe", { type: "application/x-msdownload" })];

  const result = validatePromptInputFiles(files, {
    accept: "image/*,.pdf",
    maxFiles: 3,
  });

  expect(result.accepted).toEqual([]);
  expect(result.error?.code).toBe("accept");
  expect(onError).not.toHaveBeenCalled();
});
```

- [ ] **Step 2: 抽出附件纯函数与 hook**

```ts
// ui/src/components/ai-elements/prompt-input/attachments.ts
export function validatePromptInputFiles(
  files: File[],
  options: PromptInputAttachmentOptions
) {
  return {
    accepted: files.filter((file) => matchesAccept(file, options.accept)),
    error: undefined as PromptInputError | undefined,
  };
}
```

- [ ] **Step 3: 抽出拖拽与语音输入 Hook**

```ts
// ui/src/components/ai-elements/prompt-input/usePromptInputDrop.ts
export function usePromptInputDrop(options: UsePromptInputDropOptions) {
  useEffect(() => {
    if (!options.globalDrop) {
      return;
    }
    // 保持原 document dragover / drop 行为
  }, [options]);
}
```

```ts
// ui/src/components/ai-elements/prompt-input/useSpeechRecognition.ts
export function useSpeechRecognition() {
  const [isListening, setIsListening] = useState(false);
  const [recognition, setRecognition] = useState<SpeechRecognition | null>(null);
  return { isListening, recognition, startListening, stopListening };
}
```

- [x] **Step 4: 让主入口文件只保留上下文与 JSX 编排**

```tsx
// ui/src/components/ai-elements/prompt-input.tsx
export * from "./prompt-input/index";
```

- [x] **Step 5: 运行附件与输入相关测试**

Run: `cd ui && npm run test -- src/components/ai-elements/prompt-input/attachments.test.ts`

Expected: `PASS`，accept / maxFiles / URL 回收行为符合旧逻辑。

- [x] **Step 6: 运行 lint 和 build 验证导出兼容**

Run: `cd ui && npm run lint && npm run build`

Expected: `PASS`，现有 `GeneralInput` 等调用方无需改导入路径。

- [ ] **Step 7: 提交 `prompt-input` 拆分**

```bash
git add ui/src/components/ai-elements/prompt-input.tsx ui/src/components/ai-elements/prompt-input
git commit -m "refactor: split prompt input behaviors"
```

---

### Task 7: 第一阶段收尾验证与第二阶段候选登记

**Files:**
- Modify: `ui/docs/superpowers/plans/2026-05-05-ui-fat-code-simplification-plan.md`
- Modify: `ui/CLAUDE.md`

- [ ] **Step 1: 运行第一阶段完整回归**

Run: `cd ui && npm run test && npm run lint && npm run build`

Expected: 全部通过；如存在历史遗留非本次引入问题，需在提交说明中写清楚。

- [ ] **Step 2: 更新前端维护约定，记录新的模块边界**

```md
## Frontend Maintenance Notes

- `ChatView` 只负责页面编排，流式逻辑放在 `components/ChatView/useConversationStream.ts`
- 协议投影逻辑集中在 `src/utils/chat/`
- `WorkspaceImageGeneration` 页面不再新增业务逻辑，优先进入对应 hooks
- `prompt-input` 的附件、拖拽、语音输入必须改对应子模块
```

- [ ] **Step 3: 在计划文档中登记第二阶段候选**

```md
## Phase 2 Candidates

- `src/components/DataChat/ChartUtils.js`：迁移到 TypeScript 并按图表类型拆策略
- `src/pages/WorkspaceMRag/index.tsx`：拆分页、轮询、查询会话逻辑
- `src/components/ActionPanel/ActionPanel.tsx`：按 renderer registry 收敛
```

- [ ] **Step 4: 提交第一阶段收尾**

```bash
git add ui/CLAUDE.md ui/docs/superpowers/plans/2026-05-05-ui-fat-code-simplification-plan.md
git commit -m "docs: record ui simplification boundaries"
```

---

## Self-Review

### Spec coverage

- 覆盖了当前最主要的四类胖文件：`ChatView`、`utils/chat.ts`、`WorkspaceImageGeneration`、`prompt-input.tsx`。
- 补充覆盖了展示层胖文件 `Dialogue/index.tsx`，避免只拆逻辑不拆视图。
- 明确把 `ChartUtils.js`、`WorkspaceMRag`、`ActionPanel` 列为第二阶段候选，避免本计划范围失控。

### Placeholder scan

- 本计划未使用 `TODO`、`TBD`、`类似 Task N` 这类占位写法。
- 每个任务都给出了明确文件路径、验证命令和提交粒度。

### Type consistency

- `useConversationStream` / `useWorkspacePanels` / `useImageEditor` / `useSpeechRecognition` 等命名在任务内保持一致。
- `src/components/ai-elements/prompt-input.tsx` 最终作为兼容导出壳，与当前导入路径保持一致。

### Notes

- 该计划默认在 `ui/` 目录内执行，不要求切到单独 worktree。
- 该计划不要求一次性完成全部任务，允许按任务粒度逐步实施并在每次提交后回归验证。
