import { useEffect, useState } from "react";
import { buildAgentSessionStreamUrl } from "@/services/agentRun";
import { conversationHistoryApi } from "@/services/agentConversation";

/**
 * 子会话实时订阅 + 历史回放。
 *
 * 对齐 ZCode「详情打开才订、关闭即断」：enabled=false 时绝不建连，也绝不发请求。
 * 运行中只缓冲 live 帧占位，具体轨迹合并由 AgentDetailPanel 侧按
 * requestId/messageId/toolCallId 去重后按 seq 合并（计划书 Phase 4 任务 2）。
 */
export type SubAgentSessionState = {
  /** 详情是否就绪（首帧到达或历史已 hydrate） */
  ready: boolean;
  loading: boolean;
  error: string;
  /** 最近一次会话流帧（原始 SSE 事件） */
  frames: unknown[];
  /** 数据源标记：history=历史回放，live=实时流 */
  source: "idle" | "history" | "live";
};

const IDLE_STATE: SubAgentSessionState = {
  ready: false,
  loading: false,
  error: "",
  frames: [],
  source: "idle",
};

/** 单次缓冲上限，防止后台长任务时无界增长 */
const MAX_BUFFERED_FRAMES = 512;

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

  // 1) 先拉子会话 history（owner 只读校验由后端 GET /conversation/sessions/{id} 保证）
  conversationHistoryApi
    .getSessionDetail(childSessionId, { limit: 50 })
    .then((page) => {
      if (controller.signal.aborted) {
        return;
      }
      void page;
      onStatePatch(onState, { ready: true });
    })
    .catch((error: unknown) => {
      if (controller.signal.aborted) {
        return;
      }
      onState({ error: toMessage(error) });
    });

  // 2) 订阅子会话流：运行中实时追加，结束后仅保留回放
  let frameCount = 0;
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
        frameCount += 1;
        if (frameCount > MAX_BUFFERED_FRAMES) {
          return;
        }
        onState({ ready: true, source: "live", frames: [frame] });
      });
    } catch (error: unknown) {
      if (controller.signal.aborted) {
        return;
      }
      onState({ error: toMessage(error) });
    }
  })();

  return () => controller.abort();
}

function onStatePatch(
  onState: (patch: Partial<SubAgentSessionState>) => void,
  patch: Partial<SubAgentSessionState>
) {
  onState(patch);
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
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
