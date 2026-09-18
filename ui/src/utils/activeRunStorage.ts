const ACTIVE_RUN_STORAGE_KEY = "reactor.activeRun";
const SESSION_CURSOR_PREFIX = "reactor.sessionCursor.";

export type ActiveRunCheckpoint = {
  sessionId: string;
  requestId: string;
  lastEventId: string;
  lastEventSeq: number;
};

function cursorKey(sessionId: string) {
  return `${SESSION_CURSOR_PREFIX}${sessionId}`;
}

export function readSessionCursor(sessionId: string): number {
  if (!sessionId || typeof window === "undefined") {
    return 0;
  }
  try {
    const raw = window.sessionStorage.getItem(cursorKey(sessionId));
    const seq = Number(raw);
    return Number.isFinite(seq) ? seq : 0;
  } catch {
    return 0;
  }
}

export function writeSessionCursor(sessionId: string, eventSeq: number) {
  if (!sessionId || typeof window === "undefined" || !Number.isFinite(eventSeq)) {
    return;
  }
  try {
    const next = Math.max(readSessionCursor(sessionId), eventSeq);
    window.sessionStorage.setItem(cursorKey(sessionId), String(next));
  } catch {
    // 存储不可用时不影响当前 SSE 对话。
  }
}

export function resetSessionCursor(sessionId: string) {
  if (!sessionId || typeof window === "undefined") {
    return;
  }
  try {
    window.sessionStorage.setItem(cursorKey(sessionId), "0");
  } catch {
    // 存储不可用时不影响当前 SSE 对话。
  }
}

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
        lastEventSeq: readSessionCursor(sessionId),
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
      lastEventSeq: Math.max(
        Number(parsed.lastEventSeq) || 0,
        readSessionCursor(parsed.sessionId)
      ),
    };
  } catch {
    return null;
  }
}

export function updateActiveRunSeq(sessionId: string, eventSeq: number) {
  if (!sessionId || !Number.isFinite(eventSeq)) {
    return;
  }
  writeSessionCursor(sessionId, eventSeq);
  const activeRun = readActiveRun();
  if (!activeRun || activeRun.sessionId !== sessionId) {
    return;
  }
  try {
    window.sessionStorage.setItem(
      ACTIVE_RUN_STORAGE_KEY,
      JSON.stringify({
        ...activeRun,
        lastEventSeq: Math.max(activeRun.lastEventSeq, eventSeq),
      })
    );
  } catch {
    // 存储不可用时不影响当前 SSE 对话。
  }
}

export function updateActiveRunEvent(sessionId: string, eventId: string) {
  if (!sessionId || !eventId) {
    return;
  }
  const seq = Number(eventId);
  if (Number.isFinite(seq) && seq > 0) {
    writeSessionCursor(sessionId, seq);
  }
  const activeRun = readActiveRun();
  if (!activeRun || activeRun.sessionId !== sessionId) {
    return;
  }
  try {
    window.sessionStorage.setItem(
      ACTIVE_RUN_STORAGE_KEY,
      JSON.stringify({
        ...activeRun,
        lastEventId: eventId,
        lastEventSeq: Number.isFinite(seq)
          ? Math.max(activeRun.lastEventSeq, seq)
          : activeRun.lastEventSeq,
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
