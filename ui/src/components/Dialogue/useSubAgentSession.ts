import { useEffect, useState } from "react";
import { buildAgentSessionStreamUrl } from "@/services/agentRun";
import { conversationHistoryApi } from "@/services/agentConversation";
import type {
  ConversationHistoryPage,
  ConversationRunReplay,
} from "@/services/agentConversation";
import {
  pickChildSessionRun,
  shouldFetchChildSessionReplay,
  type ChildSessionRun,
} from "@/utils/chat/subAgentChildSession";

/**
 * 子会话实时订阅 + 历史回放。
 *
 * 对齐 ZCode「详情打开才订、关闭即断」：enabled=false 时绝不建连，也绝不发请求。
 *
 * 两条互斥的数据源（plan §5.4：heartbeat 不能冒充轨迹）：
 * <ul>
 *   <li>运行中 run：子会话 SSE 自带全量回放（lastEventSeq=0 起），直接用实时帧。</li>
 *   <li>终态 run：没有活投影，SSE 立刻 follow_idle，改拉单 run replay（账本事件）。</li>
 * </ul>
 * 两者不叠加，否则同一段 agent_stream 增量会被追加两次。
 */
export type SubAgentSessionState = {
  /** 详情是否就绪（首帧到达或历史已 hydrate） */
  ready: boolean;
  loading: boolean;
  error: string;
  /** 最近一次会话流帧（原始 SSE 事件） */
  frames: unknown[];
  /** 会话级历史（runs 摘要），决定 query / status / requestId */
  history: ConversationHistoryPage | null;
  /** 单 run 回放（终态 run 的轨迹来源） */
  replay: ConversationRunReplay | null;
  /** 数据源标记：history=历史回放，live=实时流 */
  source: "idle" | "history" | "live";
};

const IDLE_STATE: SubAgentSessionState = {
  ready: false,
  loading: false,
  error: "",
  frames: [],
  history: null,
  replay: null,
  source: "idle",
};

/** 单次缓冲上限，防止后台长任务时无界增长 */
const MAX_BUFFERED_FRAMES = 512;

const FOLLOW_IDLE = "follow_idle";
const SESSION_CONTROL_PACKAGES = new Set([
  "heartbeat",
  FOLLOW_IDLE,
  "follow_pending",
]);

function isControlFrame(frame: unknown): boolean {
  if (!frame || typeof frame !== "object") {
    return false;
  }
  return SESSION_CONTROL_PACKAGES.has(
    String((frame as { packageType?: unknown }).packageType || "")
  );
}

function isFollowIdleFrame(frame: unknown): boolean {
  if (!frame || typeof frame !== "object") {
    return false;
  }
  return String((frame as { packageType?: unknown }).packageType || "") === FOLLOW_IDLE;
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/**
 * 订阅侧副作用工厂（纯逻辑，便于测试）。
 * <p>
 * 抽成独立函数而不是写在 effect 里：测试需要断言「未启用时绝不调用 fetch / history」，
 * 也方便直接驱动帧回调而不依赖 React 运行时。
 *
 * @returns abort 句柄
 */
export function subscribeSubAgentSession(
  childSessionId: string,
  enabled: boolean,
  onState: (patch: Partial<SubAgentSessionState>) => void
): () => void {
  // 未启用 / 无 childSessionId：不发任何请求（含 history），对齐 ZCode「详情打开才订」
  if (!enabled || !childSessionId) {
    onState(IDLE_STATE);
    return () => {};
  }

  const controller = new AbortController();
  onState({ ...IDLE_STATE, loading: true, error: "" });

  let frameCount = 0;
  let replayRequested = false;
  let streamEnded = false;
  let run: ChildSessionRun = undefined;

  /** 终态 / 收流后无帧时的账本回放兜底。一次订阅只拉一次。 */
  const loadReplay = (requestId?: string) => {
    if (!requestId || replayRequested || controller.signal.aborted) {
      return;
    }
    replayRequested = true;
    conversationHistoryApi
      .getRunReplay(requestId)
      .then((replay) => {
        if (controller.signal.aborted) {
          return;
        }
        onState({ ready: true, loading: false, replay, source: "history" });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        onState({ error: toMessage(error) });
      });
  };

  /** 实时流没有给出任何轨迹时（follow_idle / 断流 / 终态）改用账本回放。 */
  const loadReplayIfNoLiveFrames = () => {
    if (frameCount > 0) {
      return;
    }
    loadReplay(run?.requestId);
  };

  // 1) 先拉子会话 history（owner 只读校验由后端 GET /conversation/sessions/{id} 保证）
  conversationHistoryApi
    .getSessionDetail(childSessionId, { limit: 50 })
    .then((page) => {
      if (controller.signal.aborted) {
        return;
      }
      run = pickChildSessionRun(page);
      onState({ ready: true, history: page, source: "history" });
      if (streamEnded) {
        loadReplayIfNoLiveFrames();
        return;
      }
      if (shouldFetchChildSessionReplay(run)) {
        loadReplay(run?.requestId);
      }
    })
    .catch((error: unknown) => {
      if (controller.signal.aborted) {
        return;
      }
      onState({ error: toMessage(error) });
    });

  // 2) 订阅子会话流：运行中实时追加，结束后仅保留回放
  void (async () => {
    try {
      const reader = await fetch(buildAgentSessionStreamUrl({
        sessionId: childSessionId,
        lastEventSeq: 0,
      }), {
        method: "GET",
        headers: { Accept: "text/event-stream" },
        signal: controller.signal,
      }).then((response) => {
        if (!response.ok || !response.body) {
          throw new Error(`子会话流响应异常: ${response.status}`);
        }
        return response.body.getReader();
      });
      await readStream(reader, controller, (frame) => {
        if (isControlFrame(frame)) {
          if (isFollowIdleFrame(frame)) {
            loadReplayIfNoLiveFrames();
          }
          return;
        }
        frameCount += 1;
        if (frameCount > MAX_BUFFERED_FRAMES) {
          return;
        }
        onState({ ready: true, source: "live", frames: [frame] });
      });
      streamEnded = true;
      loadReplayIfNoLiveFrames();
    } catch (error: unknown) {
      if (controller.signal.aborted) {
        return;
      }
      streamEnded = true;
      onState({ error: toMessage(error) });
      loadReplayIfNoLiveFrames();
    }
  })();

  return () => controller.abort();
}

export function useSubAgentSession(
  childSessionId: string,
  enabled: boolean
): SubAgentSessionState {
  const [state, setState] = useState<SubAgentSessionState>(IDLE_STATE);

  useEffect(() => {
    const abort = subscribeSubAgentSession(childSessionId, enabled, (patch) => {
      setState((prev) => mergePatch(prev, patch));
    });
    return abort;
  }, [childSessionId, enabled]);

  return state;
}

function mergePatch(
  prev: SubAgentSessionState,
  patch: Partial<SubAgentSessionState>
): SubAgentSessionState {
  const next: SubAgentSessionState = { ...prev, ...patch };
  // 帧以「增量追加」语义合并，避免调用方自己拼帧
  if (patch.frames && patch.frames.length > 0) {
    next.frames = [...prev.frames, ...patch.frames];
  }
  if (patch.source !== "live") {
    next.source = next.source === "idle" ? "history" : next.source;
  }
  return next;
}

/**
 * SSE 读取：按行切分，仅透出 data: 负载（JSON 或纯文本）。
 * 不维护 offset 状态机——子会话面板不需要断点续传语义，lastEventSeq 由调用方在
 * 重新打开时从 0 开始回放。
 */
async function readStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  controller: AbortController,
  onFrame: (frame: unknown) => void
): Promise<void> {
  const decoder = new TextDecoder();
  let buffer = "";

  while (!controller.signal.aborted) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith(":")) {
        continue;
      }
      if (!trimmed.startsWith("data:")) {
        continue;
      }
      const raw = trimmed.slice(5).trim();
      if (!raw) {
        continue;
      }
      try {
        onFrame(JSON.parse(raw) as unknown);
      } catch {
        onFrame(raw);
      }
    }
  }
}

export default useSubAgentSession;
