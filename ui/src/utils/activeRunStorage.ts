const ACTIVE_RUN_STORAGE_KEY = "reactor.activeRun";

export type ActiveRunCheckpoint = {
  sessionId: string;
  requestId: string;
  lastEventId: string;
};

/**
 * 保存当前 tab 正在观察的 run。
 * sessionStorage 会跨页面刷新保留，但不会把一个 tab 的执行状态泄漏到另一个 tab。
 */
export function saveActiveRun(sessionId: string, requestId: string) {
  if (!sessionId || !requestId || typeof window === "undefined") {
    return;
  }
  try {
    window.sessionStorage.setItem(
      ACTIVE_RUN_STORAGE_KEY,
      JSON.stringify({
        sessionId,
        requestId,
        lastEventId: "0",
      })
    );
  } catch {
    // 存储不可用时不影响当前 SSE 对话。
  }
}

export function readActiveRun(): ActiveRunCheckpoint | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.sessionStorage.getItem(ACTIVE_RUN_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<ActiveRunCheckpoint>;
    if (!parsed.sessionId || !parsed.requestId) {
      return null;
    }
    return {
      sessionId: parsed.sessionId,
      requestId: parsed.requestId,
      lastEventId: parsed.lastEventId || "0",
    };
  } catch {
    return null;
  }
}

export function updateActiveRunEvent(requestId: string, eventId: string) {
  const activeRun = readActiveRun();
  if (!activeRun || activeRun.requestId !== requestId || !eventId) {
    return;
  }
  try {
    window.sessionStorage.setItem(
      ACTIVE_RUN_STORAGE_KEY,
      JSON.stringify({
        ...activeRun,
        lastEventId: eventId,
      })
    );
  } catch {
    // 存储不可用时不影响当前 SSE 对话。
  }
}

export function clearActiveRun(requestId?: string) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    const activeRun = readActiveRun();
    if (!requestId || activeRun?.requestId === requestId) {
      window.sessionStorage.removeItem(ACTIVE_RUN_STORAGE_KEY);
    }
  } catch {
    // 存储不可用时不影响当前 SSE 对话。
  }
}
