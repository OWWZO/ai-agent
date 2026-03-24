import { FC, useState, useCallback, memo } from "react";
import AttachmentList from "@/components/AttachmentList";
import LoadingDot from "@/components/LoadingDot";
import LoadingSpinner from "@/components/LoadingSpinner";
import { buildAction, getIcon, buildAttachment } from "@/utils/chat";
import {
  Message,
  MessageContent,
  MessageResponse,
  MessageActions,
  MessageAction,
} from "@/components/ai-elements/message";
import {
  Reasoning,
  ReasoningTrigger,
  ReasoningContent,
} from "@/components/ai-elements/reasoning";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { CopyIcon, CheckIcon, RefreshCwIcon, MoreHorizontalIcon } from "lucide-react";

type Props = {
  chat: CHAT.ChatItem;
  streamingThought?: string;
  deepThink: boolean;
  changeTask?: (task: CHAT.Task) => void;
  changeFile?: (file: CHAT.TFile) => void;
  changePlan?: () => void;
  onRegenerate?: () => void;
};

const PlanSection: FC<{ plan: CHAT.PlanItem[] }> = ({ plan }) => (
  <div className="space-y-2">
    <div className="flex items-center gap-2 rounded-lg p-2 text-sm text-muted-foreground">
      <i className="font_family icon-renwu text-[13px] text-primary"></i>
      <span>任务计划</span>
    </div>
    <div className="mt-1 space-y-3 border-l-2 border-muted pl-4">
      {plan.map((p, i) => (
        <div key={i} className="space-y-1.5">
          <div className="flex items-center gap-2 text-[14px] font-medium text-[#1d1d1f]">
            <div className="h-1.5 w-1.5 rounded-full bg-primary"></div>
            {p.name}
          </div>
          <div className="space-y-1 pl-3">
            {p.list.map((step, j) => (
              <div key={j} className="text-[13px] leading-6 text-[#6b7280]">
                {j + 1}. {step}
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
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
          className="mt-3 flex w-fit max-w-full cursor-pointer items-center gap-3 overflow-hidden rounded-[20px] border border-[#e8e8ed] bg-[#f5f5f7] px-4 py-3 shadow-[0_2px_8px_rgba(0,0,0,0.04)] transition-all duration-200 hover:shadow-[0_4px_12px_rgba(0,0,0,0.06)] hover:border-[#d2d2d7]"
          onClick={() => changePlan?.()}
        >
          <i className={`font_family ${getIcon(tool.messageType)} text-[#0071e3]`}></i>
          <div className="flex items-center gap-2 overflow-hidden">
            <span className="shrink-0 text-[14px] font-medium text-[#1d1d1f]">已完成</span>
            <span className="truncate text-[13px] text-[#86868b]">
              {tool.plan?.steps[completedIndex]}
            </span>
          </div>
        </div>
      );
    }
    case "tool_thought": {
      const streamingThought = !tool.resultMap?.isFinal;
      return (
        <div className="mt-[8px]">
          <Reasoning isStreaming={streamingThought} defaultOpen>
            <ReasoningTrigger />
            <ReasoningContent>{tool.toolThought || ""}</ReasoningContent>
          </Reasoning>
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
          className="mt-3 flex w-fit max-w-full cursor-pointer items-center gap-3 overflow-hidden rounded-[20px] border border-[#e8e8ed] bg-[#f5f5f7] px-4 py-3 shadow-[0_2px_8px_rgba(0,0,0,0.04)] transition-all duration-200 hover:shadow-[0_4px_12px_rgba(0,0,0,0.06)] hover:border-[#d2d2d7]"
          onClick={() => changeActiveChat(tool)}
        >
          {loading ? (
            <LoadingSpinner color="#0071e3"/>
          ) : (
            <i
              className={`font_family ${getIcon(
                tool.messageType === "deep_search" &&
                  tool.resultMap.messageType === "report"
                  ? "file"
                  : tool.messageType
              )} text-[#0071e3]`}
            ></i>
          )}
          <div className="flex items-center gap-2 overflow-hidden">
            <span className="shrink-0 text-[14px] font-medium text-[#1d1d1f]">{actionInfo.action}</span>
            <span className="truncate text-[13px] text-[#86868b]">
              {actionInfo.name}
            </span>
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
        <div className="flex w-full" key={i}>
          {!isReactType ? (
            <div className="relative mb-2 mt-1 w-8 shrink-0 overflow-hidden">
              {lastTask && chat.loading ? (
                <LoadingSpinner/>
              ) : (
                <i className="font_family icon-yiwanchengtianchong absolute left-0 top-0 text-[16px] text-[#0071e3]"></i>
              )}
              <div className="ml-[7px] h-full border-l border-dashed border-[#e8e8ed]"></div>
            </div>
          ) : null}
          <div className="mb-2 flex-1 overflow-hidden">
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
  const summaryStreaming =
    !!chat.loading && chat.conclusion?.messageType === "agent_stream";
  return (
    <div className="mb-[8px]">
      <div className="mb-[8px]">
        <MessageResponse isStreaming={summaryStreaming}>{summary}</MessageResponse>
      </div>
      <AttachmentList
        files={buildAttachment(chat.conclusion?.resultMap.fileList || [])}
        preview={true}
        review={changeFile}
      />
    </div>
  );
};

const DialogueComponent: FC<Props> = (props) => {
  const { chat, streamingThought, deepThink, changeTask, changeFile, changePlan, onRegenerate } = props;
  const isReactType = !deepThink;
  const thoughtText = streamingThought ?? chat.thought ?? "";
  const [copied, setCopied] = useState(false);

  const changeActiveChat = (task: CHAT.Task) => {
    changeTask?.(task);
  };

  const handleCopy = useCallback(() => {
    if (!chat.response) return;
    navigator.clipboard.writeText(chat.response).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [chat.response]);

  return (
    <div className="flex h-full flex-col text-[14px] font-normal text-[#111827]">
      {/* 附件 */}
      {(chat.files || []).length ? (
        <div className="mt-6 flex w-full justify-end">
          <AttachmentList files={chat.files} preview={false} />
        </div>
      ) : null}

      {/* 用户消息 */}
      {chat.query ? (
        <div className="mt-6 flex w-full justify-end">
          <Message from="user" className="max-w-[82%]">
            <MessageContent>
              {chat.query}
            </MessageContent>
          </Message>
        </div>
      ) : null}

      {/* 提示 */}
      {chat.tip ? (
        <div className="mt-5 w-full rounded-[16px] border border-[#e8e8ed] bg-[#f5f5f7] px-4 py-3 text-[14px] text-[#86868b]">
          {chat.tip}
        </div>
      ) : null}

      {/* AI 回复（Markdown） */}
      {chat.response ? (
        <div className="mt-6 flex w-full justify-start">
          <Message from="assistant" className="w-full max-w-full">
            <MessageContent>
              <MessageResponse isStreaming={chat.loading}>{chat.response}</MessageResponse>
            </MessageContent>
            {!chat.loading ? (
              <MessageActions className="mt-2">
                <MessageAction tooltip="复制" onClick={handleCopy}>
                  {copied
                    ? <CheckIcon className="size-4" />
                    : <CopyIcon className="size-4" />}
                </MessageAction>
                <MessageAction tooltip="重新生成" onClick={onRegenerate} disabled={!onRegenerate}>
                  <RefreshCwIcon className="size-4" />
                </MessageAction>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <MessageAction tooltip="更多">
                      <MoreHorizontalIcon className="size-4" />
                    </MessageAction>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start">
                    <DropdownMenuItem onClick={handleCopy}>复制原文</DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </MessageActions>
            ) : null}
          </Message>
        </div>
      ) : null}

      {/* 思考过程（深度研究模式） */}
      {!isReactType && thoughtText ? (
        <div className="mt-6 w-full rounded-[20px] border border-[#e8e8ed] bg-white/60 px-5 py-4 shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
          <Reasoning isStreaming={chat.loading}>
            <ReasoningTrigger />
            <ReasoningContent>{thoughtText}</ReasoningContent>
          </Reasoning>
        </div>
      ) : null}

      {/* 任务计划 */}
      {!isReactType && chat.planList?.length ? (
        <div className="mt-6 w-full rounded-[20px] border border-[#e8e8ed] bg-white/60 px-5 py-4 shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
          <PlanSection plan={chat.planList} />
        </div>
      ) : null}

      {/* 任务时间线 */}
      {chat.tasks.length ? (
        <div className="mt-6 w-full">
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
      {chat.loading ? (
        <div className="mt-4 flex w-full justify-start">
          {chat.response ? <LoadingDot /> : null}
        </div>
      ) : null}
    </div>
  );
};

const Dialogue = memo(
  DialogueComponent,
  (prev, next) =>
    prev.chat === next.chat &&
    prev.deepThink === next.deepThink &&
    prev.streamingThought === next.streamingThought &&
    prev.changeTask === next.changeTask &&
    prev.changeFile === next.changeFile &&
    prev.changePlan === next.changePlan &&
    prev.onRegenerate === next.onRegenerate
);

export default Dialogue;
