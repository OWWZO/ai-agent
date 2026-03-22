import { FC } from "react";
import AttachmentList from "@/components/AttachmentList";
import LoadingDot from "@/components/LoadingDot";
import LoadingSpinner from "@/components/LoadingSpinner";
import { buildAction, getIcon, buildAttachment } from "@/utils/chat";
import {
  Message,
  MessageContent,
  MessageResponse,
} from "@/components/ai-elements/message";
import {
  Reasoning,
  ReasoningTrigger,
  ReasoningContent,
} from "@/components/ai-elements/reasoning";
import {
  Task,
  TaskTrigger,
  TaskContent,
  TaskItem,
} from "@/components/ai-elements/task";

type Props = {
  chat: CHAT.ChatItem;
  deepThink: boolean;
  changeTask?: (task: CHAT.Task) => void;
  changeFile?: (file: CHAT.TFile) => void;
  changePlan?: () => void;
};

const PlanSection: FC<{ plan: CHAT.PlanItem[] }> = ({ plan }) => (
  <div>
    <div className="text-[16px] font-[600] mb-[8px]">任务计划</div>
    {plan.map((p, i) => (
      <div key={i} className="mb-[8px]">
        <div className="h-[22px] text-[#2029459E] text-[15px] font-[500] flex items-center mb-[5px]">
          <div className="w-[6px] h-[6px] rounded-[50%] bg-[#27272a] mx-8"></div>
          {p.name}
        </div>
        <div className="ml-[22px] text-[15px]">
          {p.list.map((step, j) => (
            <div key={j} className="leading-[22px]">
              {j + 1}.{step}
            </div>
          ))}
        </div>
      </div>
    ))}
  </div>
);

const ToolItem: FC<{
  tool: CHAT.Task;
  changePlan?: () => void;
  changeActiveChat: (task: CHAT.Task) => void;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ tool, changePlan, changeActiveChat, changeFile }) => {
  const actionInfo = buildAction(tool);
  switch (tool.messageType) {
    case "plan": {
      const completedIndex = tool.plan?.stepStatus.lastIndexOf("completed") || 0;
      return (
        <div
          className="mt-[8px] flex items-center px-10 py-6 bg-[#F2F3F7] w-fit rounded-[16px] cursor-pointer overflow-hidden  max-w-full"
          onClick={() => changePlan?.()}
        >
          <i className={`font_family ${getIcon(tool.messageType)}`}></i>
          <div className="px-8 flex items-center overflow-hidden">
            <div className="shrink-1">已完成</div>
            <div className="text-[#2029459E] text-[13px] flex-1 overflow-hidden whitespace-nowrap text-ellipsis ml-[8px]">
              {tool.plan?.steps[completedIndex]}
            </div>
          </div>
        </div>
      );
    }
    case "tool_thought": {
      return (
        <div className="mt-[8px]">
          <Task>
            <TaskTrigger title="思考过程" />
            <TaskContent>
              <TaskItem>{tool.toolThought}</TaskItem>
            </TaskContent>
          </Task>
        </div>
      );
    }
    case "browser": {
      return (
        <div className="mt-[8px]">
          {tool.resultMap?.steps
            .filter((s) => s.status !== "completed")
            .map((s, idx) => (
              <div key={idx}>
                <i className={`font_family ${getIcon(tool.messageType)}`}></i>
                <div>
                  <div>{actionInfo.action}</div>
                  <div>{s.goal}</div>
                </div>
              </div>
            ))}
        </div>
      );
    }
    case "task_summary": {
      return (
        <div className="mt-[8px]">
          <div className="mb-[8px]">{tool.resultMap.taskSummary}</div>
          <AttachmentList
            files={buildAttachment(tool.resultMap.fileList!)}
            preview={true}
            review={changeFile}
          />
        </div>
      );
    }
    default: {
      const loadingType = ["html", "markdown", "data_analysis"];
      const loading =
        !tool.resultMap?.isFinal &&
        ((tool.messageType === "deep_search" &&
          (tool.resultMap.messageType === "extend" ||
            tool.resultMap.messageType === "report")) ||
          loadingType.includes(tool.messageType));
      return (
        <div
          className="mt-[8px] flex items-center px-10 py-6 bg-[#F2F3F7] w-fit rounded-[16px] cursor-pointer overflow-hidden max-w-full"
          onClick={() => changeActiveChat(tool)}
        >
          {loading ? (
            <LoadingSpinner color="#F2F3F7"/>
          ) : (
            <i
              className={`font_family ${getIcon(
                tool.messageType === "deep_search" &&
                  tool.resultMap.messageType === "report"
                  ? "file"
                  : tool.messageType
              )}`}
            ></i>
          )}
          <div className="px-8 flex items-center overflow-hidden">
            <div className="shrink-0">{actionInfo.action}</div>
            <div className="text-[#2029459E] text-[13px] overflow-hidden whitespace-nowrap text-ellipsis flex-1 ml-[8px]">
              {actionInfo.name}
            </div>
          </div>
        </div>
      );
    }
  }
};

const TimeLineContent: FC<{
  tasks: CHAT.Task[];
  isReactType: boolean;
  changeActiveChat: (task: CHAT.Task) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ tasks, isReactType, changeActiveChat, changePlan, changeFile }) => (
  <>
    {tasks.map((t, i) => (
      <div key={i} className="overflow-hidden">
        {!isReactType ? <div className="font-[500]">{t.task}</div> : null}
        {(t.children || []).map((tool, j) => (
          <div key={j}>
            <ToolItem
              tool={tool}
              changePlan={changePlan}
              changeActiveChat={changeActiveChat}
              changeFile={changeFile}
            />
          </div>
        ))}
      </div>
    ))}
  </>
);

const TimeLine: FC<{
  chat: CHAT.ChatItem;
  isReactType: boolean;
  changeActiveChat: (task: CHAT.Task) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ chat, isReactType, changeActiveChat, changePlan, changeFile }) => (
  <>
    {chat.tasks.map((t, i) => {
      const lastTask = i === chat.tasks.length - 1;
      return (
        <div className="w-full flex" key={i}>
          {!isReactType ? (
            <div className="w-[30px] mt-[2px] mb-[8px] relative shrink-0 overflow-hidden">
              {lastTask && chat.loading ? (
                <LoadingSpinner/>
              ) : (
                <i className="font_family icon-yiwanchengtianchong text-[#4040ff] text-[16px] absolute top-[-4px] left-0"></i>
              )}
              <div className="h-full w-[1px] border-dashed border-l-[1px] border-[#e0e0e9] ml-[7px] "></div>
            </div>
          ) : null}
          <div className="flex-1 mb-[8px] overflow-hidden">
            <TimeLineContent
              tasks={t}
              isReactType={isReactType}
              changeActiveChat={changeActiveChat}
              changePlan={changePlan}
              changeFile={changeFile}
            />
          </div>
        </div>
      );
    })}
  </>
);

const ConclusionSection: FC<{
  chat: CHAT.ChatItem;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ chat, changeFile }) => {
  const summary =
    chat.conclusion?.resultMap?.taskSummary ||
    chat.conclusion?.result ||
    "任务已完成";
  return (
    <div className="mb-[8px]">
      <div className="mb-[8px]">{summary}</div>
      <AttachmentList
        files={buildAttachment(chat.conclusion?.resultMap.fileList || [])}
        preview={true}
        review={changeFile}
      />
    </div>
  );
};

const Dialogue: FC<Props> = (props) => {
  const { chat, deepThink, changeTask, changeFile, changePlan } = props;
  const isReactType = !deepThink;

  const changeActiveChat = (task: CHAT.Task) => {
    changeTask?.(task);
  };

  return (
    <div className="h-full text-[14px] font-normal flex flex-col text-[#27272a]">
      {/* 附件 */}
      {(chat.files || []).length ? (
        <div className="w-full mt-[24px] justify-end">
          <AttachmentList files={chat.files} preview={false} />
        </div>
      ) : null}

      {/* 用户消息 */}
      {chat.query ? (
        <div className="w-full mt-[24px] flex justify-end">
          <Message from="user" className="max-w-[80%]">
            <MessageContent className="group-[.is-user]:bg-[#4040FFB2] group-[.is-user]:text-white">
              {chat.query}
            </MessageContent>
          </Message>
        </div>
      ) : null}

      {/* 提示 */}
      {chat.tip ? (
        <div className="w-full rounded-[12px] mt-[24px]">{chat.tip}</div>
      ) : null}

      {/* AI 回复（Markdown） */}
      {chat.response ? (
        <div className="w-full mt-[24px] flex justify-start">
          <Message from="assistant" className="max-w-[80%]">
            <MessageContent>
              <MessageResponse>{chat.response}</MessageResponse>
            </MessageContent>
          </Message>
        </div>
      ) : null}

      {/* 思考过程（深度研究模式） */}
      {!isReactType && chat.thought ? (
        <div className="w-full mt-[24px]">
          <Reasoning isStreaming={chat.loading && !chat.response}>
            <ReasoningTrigger />
            <ReasoningContent>{chat.thought}</ReasoningContent>
          </Reasoning>
        </div>
      ) : null}

      {/* 任务计划 */}
      {!isReactType && chat.planList?.length ? (
        <div className="w-full px-12 py-8 rounded-[12px] mt-[24px] bg-[#F2F3F7]">
          <PlanSection plan={chat.planList} />
        </div>
      ) : null}

      {/* 任务时间线 */}
      {chat.tasks.length ? (
        <div className="w-full mt-[24px]">
          <TimeLine
            chat={chat}
            isReactType={isReactType}
            changeActiveChat={changeActiveChat}
            changePlan={changePlan}
            changeFile={changeFile}
          />
        </div>
      ) : null}

      {/* 结论 */}
      {chat.conclusion ? (
        <div className="w-full">
          <ConclusionSection chat={chat} changeFile={changeFile} />
        </div>
      ) : null}

      {/* 加载中 */}
      {chat.loading ? <LoadingDot /> : null}
    </div>
  );
};

export default Dialogue;
