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
    label: "已完成",
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

// eslint-disable-next-line react-refresh/only-export-components
export function formatReplayDuration(durationMs?: number): string | undefined {
  if (typeof durationMs !== "number" || !Number.isFinite(durationMs) || durationMs < 0) {
    return undefined;
  }

  const totalSeconds = Math.floor(durationMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}h${minutes}m${seconds}s`;
  }
  if (minutes > 0) {
    return `${minutes}m${seconds}s`;
  }
  return `${seconds}s`;
}

type ReplayDisclosureProps = {
  chat: CHAT.ChatItem;
  onRequestReplay: ReplayRequestHandler;
  replayLoading?: boolean;
};

const ReplayDisclosure: FC<ReplayDisclosureProps> = (props) => {
  const { chat, onRequestReplay, replayLoading = false } = props;
  const [requestPending, setRequestPending] = useState(false);
  const requestGateRef = useRef<ReplayRequestGate | null>(null);
  if (requestGateRef.current === null) {
    requestGateRef.current = createReplayRequestGate();
  }
  const requestGate = requestGateRef.current;

  if (
    !chat.requestId ||
    chat.replayAvailable === false ||
    chat.replayLoaded ||
    chat.loading
  ) {
    return null;
  }

  const status = String(chat.metrics?.status || "").trim().toUpperCase();
  const statusMeta = STATUS_META[status] || FALLBACK_STATUS_META;
  const StatusIcon = statusMeta.icon;
  const duration = formatReplayDuration(chat.runDurationMs);
  const isLoading = replayLoading || requestPending;

  const handleRequest = () => {
    if (isLoading || requestGate.isLocked()) {
      return;
    }

    const started = requestGate.request(
      chat.requestId,
      onRequestReplay,
      () => setRequestPending(false)
    );
    if (started) {
      setRequestPending(true);
    }
  };

  return (
    <button
      type="button"
      data-testid="replay-disclosure"
      data-request-id={chat.requestId}
      onClick={handleRequest}
      disabled={isLoading}
      aria-busy={isLoading || undefined}
      aria-label={`${statusMeta.label}${duration ? ` ${duration}` : ""}，展开回放`}
      title="展开当前 run 的完整回放"
      className="flex min-h-9 w-full items-center gap-2 rounded-[6px] px-2 py-1.5 text-left text-[12px] text-[var(--chat-text-muted)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--chat-accent-soft)] disabled:cursor-wait disabled:opacity-60"
    >
      <StatusIcon
        className={`h-3.5 w-3.5 shrink-0 ${statusMeta.iconClass}`}
        aria-hidden="true"
      />
      <span className="shrink-0">{statusMeta.label}</span>
      {duration ? (
        <span className="shrink-0 tabular-nums">{duration}</span>
      ) : null}
      <span className="ml-auto inline-flex shrink-0" aria-hidden="true">
        {isLoading ? (
          <LoaderCircle
            className="h-3.5 w-3.5 animate-spin"
            data-testid="replay-loading"
          />
        ) : (
          <ChevronRight
            className="h-3.5 w-3.5"
            data-testid="replay-chevron"
          />
        )}
      </span>
    </button>
  );
};

export default ReplayDisclosure;
