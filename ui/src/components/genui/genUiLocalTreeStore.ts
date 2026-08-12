/**
 * Client-side GenUI patches for in-UI interaction (patch_ui / form local state).
 * Server emit_ui_patch still flows through multiAgent tasks; this store only
 * holds patches applied by clicking GenUI controls without starting a chat turn.
 */

export type LocalUiPatch = {
  op: string;
  path: string;
  value?: unknown;
};

type ScopeState = {
  patches: LocalUiPatch[];
  version: number;
};

const scopes = new Map<string, ScopeState>();
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((l) => l());
}

export function genUiLocalScopeKey(
  sessionId?: string,
  messageId?: string
): string {
  return `${sessionId || "session"}::${messageId || "message"}`;
}

export function subscribeGenUiLocalTree(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function getGenUiLocalSnapshot(): number {
  let total = 0;
  scopes.forEach((s) => {
    total += s.version;
  });
  return total;
}

export function getLocalUiPatches(scopeKey: string): LocalUiPatch[] {
  return scopes.get(scopeKey)?.patches ?? [];
}

export function getLocalUiVersion(scopeKey: string): number {
  return scopes.get(scopeKey)?.version ?? 0;
}

export function applyLocalUiPatches(
  scopeKey: string,
  patches: LocalUiPatch[]
): number {
  if (!scopeKey || !Array.isArray(patches) || !patches.length) {
    return getLocalUiVersion(scopeKey);
  }
  const prev = scopes.get(scopeKey);
  const nextPatches = [...(prev?.patches ?? []), ...patches];
  const version = (prev?.version ?? 0) + 1;
  scopes.set(scopeKey, { patches: nextPatches, version });
  emit();
  return version;
}

export function clearLocalUiPatches(scopeKey: string): void {
  if (!scopes.has(scopeKey)) return;
  scopes.delete(scopeKey);
  emit();
}
