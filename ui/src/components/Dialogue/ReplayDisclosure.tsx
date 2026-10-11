import { useRef, useState, type FC } from "react";
import {
  AlertCircle,
  Ban,
  CheckCircle2,
  ChevronRight,
  Clock3,
  LoaderCircle,
  type LucideIcon,
} from "lucide-react";
import { formatWorkDuration } from "@/lib/workDuration";
import { cn } from "@/lib/utils";

export type ReplayRequestHandler = (
  requestId: string
) => void | PromiseLike<unknown>;

type ReplayStatusMeta = {
  label: string;
  icon: LucideIcon;
  iconClass: string;
};

const STATUS_META: Record<string, ReplayStatusMeta> = {
  SUCCESS: {
    // 对齐 ZCode `chat.history.workedFor`：时长跟在「已工作」之后，而不是「已完成 4m13s」。
    label: "已工作",
    icon: CheckCircle2,
    iconClass: "text-[var(--color-success)]",
  },
  FAILED: {
    label: "执行失败",
    icon: AlertCircle,
    iconClass: "text-[var(--color-danger)]",
  },
  STOPPED: {
    label: "已停止",
    icon: Ban,
    iconClass: "text-[var(--chat-text-muted)]",
  },
  TIMEOUT: {
    label: "已超时",
    icon: Clock3,
    iconClass: "text-[var(--chat-text-muted)]",
  },
  WAITING_INPUT: {
    label: "等待回答",
    icon: Clock3,
    iconClass: "text-[var(--color-warning)]",
  },
};

const FALLBACK_STATUS_META: ReplayStatusMeta = {
  label: "查看回放",
  icon: Clock3,
  iconClass: "text-[var(--chat-text-muted)]",
};

export type ReplayRequestGate = {
  isLocked: () => boolean;
  request: (
    requestId: string,
    onRequestReplay: ReplayRequestHandler,
    onSettled?: () => void
  ) => boolean;
};

function isPromiseLike(value: void | PromiseLike<unknown>): value is PromiseLike<unknown> {
  return (
    (typeof value === "object" && value !== null) ||
    typeof value === "function"
  ) && typeof (value as PromiseLike<unknown>).then === "function";
}

/**
 * 该轮是否还需要补回放。
 *
 * 折叠/展开是**纯 UI 动作**，不该反复打后端：
 * - 同一个 `requestId` 只自动补一次（`attemptedRequestId` 记录），成功失败都不重试；
 * - `replayLoaded` 是成功路径的额外保险（历史会话进入时它本来就是 `true`）；
 * - `replayAvailable === false` 表示后端没有回放数据，不要白打一次。
 */
// eslint-disable-next-line react-refresh/only-export-components
export function shouldHydrateRunReplay(params: {
  attemptedRequestId: string | null;
  requestId: string;
  replayLoaded?: boolean;
  replayAvailable?: boolean;
}): boolean {
  if (!params.requestId) {
    return false;
  }
  if (params.attemptedRequestId === params.requestId) {
    return false;
  }
  if (params.replayLoaded) {
    return false;
  }
  if (params.replayAvailable === false) {
    return false;
  }
  return true;
}

/** Prevents two synchronous clicks from starting the same replay request. */
// eslint-disable-next-line react-refresh/only-export-components
export function createReplayRequestGate(): ReplayRequestGate {
  let locked = false;

  return {
    isLocked: () => locked,
    request: (requestId, onRequestReplay, onSettled) => {
      if (locked) {
        return false;
      }

      locked = true;
      const settle = () => {
        locked = false;
        onSettled?.();
      };

      try {
        const result = onRequestReplay(requestId);
        if (isPromiseLike(result)) {
          void result.then(settle, settle);
        } else {
          void Promise.resolve().then(settle);
        }
        return true;
      } catch (error) {
        settle();
        throw error;
      }
    },
  };
}

type ReplayDisclosureProps = {
  chat: CHAT.ChatItem;
  onRequestReplay: ReplayRequestHandler;
  replayLoading?: boolean;
  /** 过程区当前是否展开（受控） */
  open?: boolean;
  /** 过程区展开态变化。**只有存在可折叠过程时才传**，不传则退化为纯回放入口。 */
  onToggle?: (open: boolean) => void;
};

const ReplayDisclosure: FC<ReplayDisclosureProps> = (props) => {
  const {
    chat,
    onRequestReplay,
    replayLoading = false,
    open = false,
    onToggle,
  } = props;
  const [requestPending, setRequestPending] = useState(false);
  const requestGateRef = useRef<ReplayRequestGate | null>(null);
  /** 已经为哪个 requestId 补过回放（不论成败），避免折叠/展开反复打后端 */
  const attemptedReplayIdRef = useRef<string | null>(null);
  if (requestGateRef.current === null) {
    requestGateRef.current = createReplayRequestGate();
  }
  const requestGate = requestGateRef.current;

  // 注意：这里**不再**因 `chat.replayLoaded` 返回 null。
  // 回放补齐后本组件仍要常驻——它同时是该轮过程区的折叠 trigger
  // （对齐 ZCode `AssistantHistoryStatus`：展开后还能再点折叠）。
  if (!chat.requestId || chat.replayAvailable === false || chat.loading) {
    return null;
  }

  const collapsible = typeof onToggle === "function";
  const status = String(chat.metrics?.status || "").trim().toUpperCase();
  const statusMeta = STATUS_META[status] || FALLBACK_STATUS_META;
  const StatusIcon = statusMeta.icon;
  const duration = formatWorkDuration(chat.runDurationMs);
  const isLoading = replayLoading || requestPending;

  const handleRequest = () => {
    if (isLoading || requestGate.isLocked()) {
      return;
    }

    // 回放只补一次：补完仍保留本组件，供后续折叠/展开。
    if (
      shouldHydrateRunReplay({
        attemptedRequestId: attemptedReplayIdRef.current,
        requestId: chat.requestId,
        replayLoaded: chat.replayLoaded,
        replayAvailable: chat.replayAvailable,
      })
    ) {
      attemptedReplayIdRef.current = chat.requestId;
      const started = requestGate.request(
        chat.requestId,
        onRequestReplay,
        () => setRequestPending(false)
      );
      if (started) {
        setRequestPending(true);
      }
    }

    onToggle?.(!open);
  };

  return (
    <button
      type="button"
      data-testid="replay-disclosure"
      data-request-id={chat.requestId}
      onClick={handleRequest}
      disabled={isLoading}
      aria-busy={isLoading || undefined}
      aria-label={`${statusMeta.label}${duration ? ` ${duration}` : ""}，${
        collapsible ? (open ? "折叠" : "展开") + "过程" : "展开回放"
      }`}
      title={
        collapsible
          ? open
            ? "折叠本轮执行过程"
            : "展开本轮执行过程"
          : "展开当前 run 的完整回放"
      }
      data-process-open={collapsible ? String(open) : undefined}
      className="flex w-full items-center gap-2 border-b border-[var(--chat-border)]/50 pb-2 text-left text-ui-base text-[var(--chat-text-soft)] transition-colors hover:text-[var(--chat-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--chat-accent-soft)] disabled:cursor-wait disabled:opacity-60"
    >
      <StatusIcon
        className={`h-3.5 w-3.5 shrink-0 ${statusMeta.iconClass}`}
        aria-hidden="true"
      />
      <span className="shrink-0">{statusMeta.label}</span>
      {duration ? (
        <span className="shrink-0 tabular-nums">{duration}</span>
      ) : null}
      <span
        className="inline-flex shrink-0 text-[var(--chat-text-faint)]"
        aria-hidden="true"
      >
        {isLoading ? (
          <LoaderCircle
            className="h-3.5 w-3.5 animate-spin"
            data-testid="replay-loading"
          />
        ) : collapsible ? (
          <ChevronRight
            className={cn(
              "h-3.5 w-3.5 transition-transform duration-200",
              open && "rotate-90"
            )}
            data-testid="replay-chevron"
          />
        ) : null}
      </span>
    </button>
  );
};

export default ReplayDisclosure;
