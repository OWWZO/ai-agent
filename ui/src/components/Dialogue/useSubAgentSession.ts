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
import querySSE from "@/utils/querySSE";
import { parseAgentAnswer } from "@/utils/sseParsers";

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
 * <p>
 * <b>顺序上先 history 再决定建流</b>：终态 run 根本不开这条连接——它唯一的归宿就是
 * 立刻 follow_idle，建了纯属白跑一次往返、白起一个 emitter 与心跳任务。
 * 实时流从 {@code lastEventSeq=0} 订阅、投影自带全量回放，所以晚订阅不丢帧。
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
  onState({
    ...IDLE_STATE,
    loading: true,
    error: ""
  });

  let frameCount = 0;
  let replayRequested = false;
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
        onState({
          ready: true,
          loading: false,
          replay,
          source: "history"
        });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        onState({ error: toMessage(error) });
      });
  };

  /** 实时流没有给出任何轨迹时（follow_idle / 断流）改用账本回放。 */
  const loadReplayIfNoLiveFrames = () => {
    if (frameCount > 0) {
      return;
    }
    loadReplay(run?.requestId);
  };

  /**
   * 订阅子会话流。必须走 querySSE —— 它统一注入 Authorization 并在 401 时刷新 token 重试；
   * 裸 fetch 不带凭证会被 AuthenticationFilter 直接挡成 401。
   * <p>
   * 从 {@code lastEventSeq=0} 起订阅：投影自带全量回放，因此**晚订阅不会丢帧**，
   * 这才让「先问 history 再决定开不开流」成为安全的重排。
   */
  const openLiveStream = () => {
    if (controller.signal.aborted) {
      return;
    }
    querySSE(
      {
        method: "GET",
        body: null,
        signal: controller.signal,
        // GET 观察流不重发：断线恢复由调用方重新打开详情面板承担
        retryOnError: false,
        parser: parseAgentAnswer,
        handleMessage: (frame) => {
          if (isControlFrame(frame)) {
            if (isFollowIdleFrame(frame)) {
              // 流已收口但一帧轨迹都没拿到（订阅晚了 / 终态）：退账本回放。
              loadReplayIfNoLiveFrames();
            }
            return;
          }
          frameCount += 1;
          if (frameCount > MAX_BUFFERED_FRAMES) {
            return;
          }
          onState({
            ready: true,
            source: "live",
            frames: [frame]
          });
        },
        handleError: (error) => {
          if (controller.signal.aborted) {
            return;
          }
          onState({ error: toMessage(error) });
          loadReplayIfNoLiveFrames();
        },
        handleClose: () => {
          if (controller.signal.aborted) {
            return;
          }
          loadReplayIfNoLiveFrames();
        },
      },
      buildAgentSessionStreamUrl({
        sessionId: childSessionId,
        lastEventSeq: 0,
      })
    );
  };

  // 1) 先拉子会话 history（owner 只读校验由后端 GET /conversation/sessions/{id} 保证）。
  //    它同时决定「要不要建实时流」，所以必须先落地再决定，不能和流并行起。
  conversationHistoryApi
    .getSessionDetail(childSessionId, { limit: 50 })
    .then((page) => {
      if (controller.signal.aborted) {
        return;
      }
      run = pickChildSessionRun(page);
      onState({
        ready: true,
        history: page,
        source: "history"
      });
      // 终态 run：账本回放才是权威轨迹，实时流只会立刻 follow_idle。
      // 「回看已完成的子 Agent」正是详情面板的主要用法，没必要为它白建一条连接。
      if (shouldFetchChildSessionReplay(run)) {
        loadReplay(run?.requestId);
        return;
      }
      openLiveStream();
    })
    .catch((error: unknown) => {
      if (controller.signal.aborted) {
        return;
      }
      onState({ error: toMessage(error) });
      // 拿不到 run 状态时不能连实时流也放弃，否则面板彻底空白。
      openLiveStream();
    });

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
  const next: SubAgentSessionState = {
    ...prev,
    ...patch
  };
  // 帧以「增量追加」语义合并，避免调用方自己拼帧
  if (patch.frames && patch.frames.length > 0) {
    next.frames = [...prev.frames, ...patch.frames];
  }
  if (patch.source !== "live") {
    next.source = next.source === "idle" ? "history" : next.source;
  }
  return next;
}

export default useSubAgentSession;
