/**
 * Cute overlay: forward OpenCLI daemon HTTP to Java BrowserRelayHub.
 * Upstream OpenCLI talks to 127.0.0.1:19825; this process must not spawn that daemon.
 */

export const RELAY_DAEMON_VERSION = 'relay';

export function isRelayMode(): boolean {
  const url = process.env.OPENCLI_RELAY_URL;
  return typeof url === 'string' && url.trim().length > 0;
}

export function relayVisitorId(): string {
  return (process.env.OPENCLI_RELAY_VISITOR_ID ?? '').trim();
}

export function relaySecret(): string {
  return process.env.OPENCLI_RELAY_SECRET ?? '';
}

export function relayUrl(): string {
  return (process.env.OPENCLI_RELAY_URL ?? '').trim();
}

export function relayStatusBody(): {
  ok: boolean;
  pid: number;
  uptime: number;
  daemonVersion: string;
  extensionConnected: boolean;
  pending: number;
  memoryMB: number;
  port: number;
} {
  return {
    ok: true,
    pid: process.pid,
    uptime: 0,
    daemonVersion: RELAY_DAEMON_VERSION,
    extensionConnected: true,
    pending: 0,
    memoryMB: 0,
    port: 0,
  };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'X-OpenCLI': '1' },
  });
}

function pathOnly(pathname: string): string {
  const q = pathname.indexOf('?');
  return q >= 0 ? pathname.slice(0, q) : pathname;
}

function readBodyObject(init?: RequestInit): Record<string, unknown> {
  const raw = init?.body;
  if (raw == null) return {};
  const text = typeof raw === 'string' ? raw : Buffer.isBuffer(raw) ? raw.toString('utf8') : String(raw);
  if (!text.trim()) return {};
  try {
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : {};
  } catch {
    return {};
  }
}

export async function relayRequest(pathname: string, init?: RequestInit & { timeout?: number }): Promise<Response> {
  const path = pathOnly(pathname);
  if (path === '/status' || path === '/ping') {
    return jsonResponse(relayStatusBody());
  }
  if (path === '/shutdown') {
    return jsonResponse({ ok: true });
  }
  if (path !== '/command') {
    return jsonResponse({ ok: false, error: 'not found', errorCode: 'not_found' }, 404);
  }

  const payload = readBodyObject(init);
  if (payload.action === 'lease-release') {
    return jsonResponse({ ok: true, id: payload.id ?? null });
  }

  const visitorId = relayVisitorId();
  if (visitorId) {
    payload.userId = visitorId;
  }
  const { timeout = 2000, headers, ...rest } = init ?? {};
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  const secret = relaySecret();
  try {
    return await fetch(relayUrl(), {
      ...rest,
      method: rest.method ?? 'POST',
      headers: {
        'X-OpenCLI': '1',
        'Content-Type': 'application/json',
        ...(secret ? { 'X-Adapter-Secret': secret } : {}),
        ...headers,
      },
      body: JSON.stringify(payload),
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timer);
  }
}
