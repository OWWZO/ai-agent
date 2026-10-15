/**
 * CDP client — implements IPage by connecting directly to a Chrome/Electron CDP WebSocket.
 *
 * Fixes applied:
 * - send() now has a 30s timeout guard (P0 #4)
 * - goto() waits for Page.loadEventFired instead of hardcoded 1s sleep (P1 #3)
 * - Implemented scroll, autoScroll, screenshot, networkRequests (P1 #2)
 * - Shared DOM helper methods extracted to reduce duplication with Page (P1 #5)
 */

import { WebSocket, type RawData } from 'ws';
import { request as httpRequest } from 'node:http';
import { request as httpsRequest } from 'node:https';
import type { BrowserCookie, BrowserEvaluateFunction, IPage, ScreenshotOptions } from '../types.js';
import type { IBrowserFactory } from '../runtime.js';
import { buildEvaluateExpression } from './utils.js';
import { generateStealthJs } from './stealth.js';
import { waitForDomStableJs } from './dom-helpers.js';
import { isRecord, saveBase64ToFile } from '../utils.js';
import { getAllElectronApps } from '../electron-apps.js';
import { CDPBasePage } from './base-page.js';

export interface CDPTarget {
  type?: string;
  url?: string;
  title?: string;
  webSocketDebuggerUrl?: string;
}

interface RuntimeEvaluateResult {
  result?: {
    value?: unknown;
  };
  exceptionDetails?: {
    exception?: {
      description?: string;
    };
  };
}

const CDP_SEND_TIMEOUT = 30_000;
const CDP_CLOSE_TIMEOUT = 1_000;
const ISOLATED_ADAPTER_TARGET_ERROR = 'Direct CDP endpoint cannot create an isolated browser tab.';

// Memory guard for in-process capture. The 4k cap we used to apply everywhere
// silently truncated JSON so `JSON.parse` failed or gave partial objects — the
// primary agent-facing bug. Now we keep the full body up to a large cap and
// surface `responseBodyFullSize` + `responseBodyTruncated` so downstream layers
// can tell the agent what happened instead of lying about the payload.
export const CDP_RESPONSE_BODY_CAPTURE_LIMIT = 8 * 1024 * 1024;
export const CDP_REQUEST_BODY_CAPTURE_LIMIT = 1 * 1024 * 1024;

export class CDPBridge implements IBrowserFactory {
  private _ws: WebSocket | null = null;
  private _sessionId: string | null = null;
  private _targetId: string | null = null;
  private _isolatedAdapterTarget = false;
  private _idCounter = 0;
  private _pending = new Map<number, { resolve: (val: unknown) => void; reject: (err: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  private _eventListeners = new Map<string, Set<(params: unknown) => void>>();

  async connect(opts?: { timeout?: number; session?: string; cdpEndpoint?: string; contextId?: string; preferredContextId?: string; idleTimeout?: number; windowMode?: 'foreground' | 'background'; surface?: 'browser' | 'adapter'; siteSession?: 'ephemeral' | 'persistent' }): Promise<IPage> {
    if (this._ws) throw new Error('CDPBridge is already connected. Call close() before reconnecting.');

    const endpoint = opts?.cdpEndpoint ?? process.env.OPENCLI_CDP_ENDPOINT;
    if (!endpoint) throw new Error('CDP endpoint not provided (pass cdpEndpoint or set OPENCLI_CDP_ENDPOINT)');
    const isolatedAdapterTarget = opts?.surface === 'adapter';
    this._isolatedAdapterTarget = isolatedAdapterTarget;

    let wsUrl = endpoint;
    if (endpoint.startsWith('http')) {
      try {
        if (isolatedAdapterTarget) {
          wsUrl = await resolveBrowserWebSocketUrl(endpoint);
        } else {
          const targets = await fetchJsonDirect(`${endpoint.replace(/\/$/, '')}/json`) as CDPTarget[];
          const target = selectCDPTarget(targets);
          if (!target || !target.webSocketDebuggerUrl) {
            throw new Error('No inspectable targets found at CDP endpoint');
          }
          wsUrl = target.webSocketDebuggerUrl;
        }
      } catch (err) {
        this._isolatedAdapterTarget = false;
        throw err;
      }
    }

    return new Promise((resolve, reject) => {
      const ws = new WebSocket(wsUrl);
      const timeoutMs = (opts?.timeout ?? 10) * 1000;
      const timeout = setTimeout(() => {
        this._ws = null;
        this._isolatedAdapterTarget = false;
        ws.close();
        reject(new Error('CDP connect timeout'));
      }, timeoutMs);

      ws.on('open', async () => {
        clearTimeout(timeout);
        this._ws = ws;
        try {
          if (isolatedAdapterTarget) {
            await this.attachIsolatedAdapterTarget();
          } else {
            await this.attachPageSessionIfBrowserLevel();
          }
          await this.send('Page.enable');
          await this.send('Page.addScriptToEvaluateOnNewDocument', { source: generateStealthJs() });
        } catch (err) {
          await this.close().catch(() => {});
          reject(err instanceof Error ? err : new Error(String(err)));
          return;
        }
        resolve(new CDPPage(this, opts?.session, opts?.contextId, opts?.preferredContextId));
      });

      ws.on('error', (err: Error) => {
        clearTimeout(timeout);
        reject(err);
      });

      ws.on('message', (data: RawData) => {
        try {
          const msg = JSON.parse(data.toString());
          if (msg.id && this._pending.has(msg.id)) {
            const entry = this._pending.get(msg.id)!;
            clearTimeout(entry.timer);
            this._pending.delete(msg.id);
            if (msg.error) {
              entry.reject(new Error(msg.error.message));
            } else {
              entry.resolve(msg.result);
            }
          }
          if (msg.method) {
            if (this._sessionId && typeof msg.sessionId === 'string' && msg.sessionId !== this._sessionId) {
              return;
            }
            const listeners = this._eventListeners.get(msg.method);
            if (listeners) {
              for (const fn of listeners) fn(msg.params);
            }
          }
        } catch (err) {
          if (process.env.OPENCLI_VERBOSE) {
            // eslint-disable-next-line no-console
            console.error('[cdp] Failed to parse WebSocket message:', err instanceof Error ? err.message : err);
          }
        }
      });
    });
  }

  async close(): Promise<void> {
    if (this._isolatedAdapterTarget && this._targetId) {
      await this.closeTarget(this._targetId, CDP_CLOSE_TIMEOUT).catch(() => {});
    }

    const ws = this._ws;
    this._ws = null;
    this._sessionId = null;
    this._targetId = null;
    this._isolatedAdapterTarget = false;
    for (const p of this._pending.values()) {
      clearTimeout(p.timer);
      p.reject(new Error('CDP connection closed'));
    }
    this._pending.clear();
    this._eventListeners.clear();
    if (ws) await this.closeWebSocket(ws);
  }

  private closeWebSocket(ws: WebSocket): Promise<void> {
    if (ws.readyState === WebSocket.CLOSED) return Promise.resolve();
    if (ws.readyState !== WebSocket.OPEN) {
      ws.terminate();
      return Promise.resolve();
    }

    return new Promise((resolve) => {
      let settled = false;
      const finish = () => {
        if (settled) return;
        settled = true;
        clearTimeout(timeout);
        ws.off('close', finish);
        resolve();
      };
      const timeout = setTimeout(() => {
        ws.terminate();
        finish();
      }, CDP_CLOSE_TIMEOUT);

      ws.once('close', finish);
      ws.close();
    });
  }

  getActiveTarget(): string | undefined {
    return this._targetId ?? undefined;
  }

  async listTargets(): Promise<BrowserTargetInfo[]> {
    const result = await this.send('Target.getTargets');
    const targetInfos = isRecord(result) && Array.isArray(result.targetInfos) ? result.targetInfos : [];
    return targetInfos
      .filter((target): target is Record<string, unknown> => isRecord(target))
      .filter((target) => typeof target.targetId === 'string' && target.targetId && target.type === 'page')
      .map((target) => ({
        targetId: target.targetId as string,
        type: target.type as string,
        url: typeof target.url === 'string' ? target.url : undefined,
        title: typeof target.title === 'string' ? target.title : undefined,
      }));
  }

  async createTarget(): Promise<string> {
    const result = await this.send('Target.createTarget', { url: 'about:blank' });
    if (!isRecord(result) || typeof result.targetId !== 'string' || !result.targetId) {
      throw new Error('CDP Target.createTarget returned no targetId');
    }
    return result.targetId;
  }

  async selectTarget(targetId: string): Promise<void> {
    if (!targetId) throw new Error('CDP targetId is required');
    if (this._targetId === targetId && this._sessionId) return;
    await this.detachCurrentTarget();
    const attached = await this.send('Target.attachToTarget', { targetId, flatten: true });
    if (!isRecord(attached) || typeof attached.sessionId !== 'string' || !attached.sessionId) {
      throw new Error(`CDP Target.attachToTarget returned no sessionId for ${targetId}`);
    }
    this._sessionId = attached.sessionId;
    this._targetId = targetId;
  }

  async closeTarget(targetId: string, timeoutMs: number = CDP_SEND_TIMEOUT): Promise<void> {
    if (!targetId) throw new Error('CDP targetId is required');
    let closed = false;
    try {
      await this.send('Target.closeTarget', { targetId }, timeoutMs);
      closed = true;
    } finally {
      if (closed && this._targetId === targetId) {
        this._sessionId = null;
        this._targetId = null;
      }
    }
  }

  private async detachCurrentTarget(): Promise<void> {
    const sessionId = this._sessionId;
    if (!sessionId) {
      this._targetId = null;
      return;
    }
    try {
      await this.send('Target.detachFromTarget', { sessionId });
    }
    finally {
      this._sessionId = null;
      this._targetId = null;
    }
  }

  async send(method: string, params: Record<string, unknown> = {}, timeoutMs: number = CDP_SEND_TIMEOUT): Promise<unknown> {
    if (!this._ws || this._ws.readyState !== WebSocket.OPEN) {
      throw new Error('CDP connection is not open');
    }
    const id = ++this._idCounter;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this._pending.delete(id);
        reject(new Error(`CDP command '${method}' timed out after ${timeoutMs / 1000}s`));
      }, timeoutMs);
      this._pending.set(id, { resolve, reject, timer });
      const payload: Record<string, unknown> = { id, method, params };
      if (this._sessionId && !isBrowserScopedCdpMethod(method)) {
        payload.sessionId = this._sessionId;
      }
      this._ws!.send(JSON.stringify(payload));
    });
  }

  private async attachPageSessionIfBrowserLevel(): Promise<void> {
    let result: unknown;
    try {
      result = await this.send('Target.getTargets');
    } catch {
      return;
    }
    const targetInfos = isRecord(result) && Array.isArray(result.targetInfos) ? result.targetInfos : [];
    const selected = selectBrowserPageTarget(targetInfos);
    if (!selected) {
      return;
    }
    try {
      await this.selectTarget(selected.targetId);
    } catch {
      this._sessionId = null;
      this._targetId = null;
    }
  }

  private async attachIsolatedAdapterTarget(): Promise<void> {
    let targetId: string | undefined;
    try {
      const result = await this.send('Target.getTargets');
      if (!isRecord(result) || !Array.isArray(result.targetInfos)) {
        throw new Error('Target.getTargets did not return browser target information');
      }
      targetId = await this.createTarget();
      await this.selectTarget(targetId);
    } catch (err) {
      if (targetId) {
        await this.send('Target.closeTarget', { targetId }, CDP_CLOSE_TIMEOUT).catch(() => {});
      }
      this._sessionId = null;
      this._targetId = null;
      throw isolatedAdapterTargetError(err);
    }
  }

  on(event: string, handler: (params: unknown) => void): void {
    let set = this._eventListeners.get(event);
    if (!set) {
      set = new Set();
      this._eventListeners.set(event, set);
    }
    set.add(handler);
  }

  off(event: string, handler: (params: unknown) => void): void {
    this._eventListeners.get(event)?.delete(handler);
  }

  waitForEvent(event: string, timeoutMs: number = 15_000): Promise<unknown> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.off(event, handler);
        reject(new Error(`Timed out waiting for CDP event '${event}'`));
      }, timeoutMs);
      const handler = (params: unknown) => {
        clearTimeout(timer);
        this.off(event, handler);
        resolve(params);
      };
      this.on(event, handler);
    });
  }
}

class CDPPage extends CDPBasePage {
  private _pageEnabled = false;

  // Network capture state (mirrors extension/src/cdp.ts NetworkCaptureEntry shape)
  private _networkCapturing = false;
  private _networkCapturePattern = '';
  private _networkEntries: Array<{
    url: string; method: string; responseStatus?: number;
    requestHeaders?: Record<string, string>;
    requestBodyKind?: string;
    requestBodyPreview?: string;
    requestBodyFullSize?: number;
    requestBodyTruncated?: boolean;
    responseContentType?: string;
    responsePreview?: string;
    responseBodyFullSize?: number;
    responseBodyTruncated?: boolean;
    timestamp: number;
  }> = [];
  private _pendingRequests = new Map<string, number>(); // requestId → index in _networkEntries
  private _pendingBodyFetches: Set<Promise<void>> = new Set(); // track in-flight getResponseBody calls
  private _consoleMessages: Array<{ type: string; text: string; timestamp: number }> = [];
  private _consoleCapturing = false;

  constructor(
    private bridge: CDPBridge,
    public readonly session?: string,
    public readonly contextId?: string,
    public readonly preferredContextId?: string,
  ) {
    super();
  }

  async closeWindow(): Promise<void> {
    const targetId = this.bridge.getActiveTarget();
    try {
      if (targetId) await this.bridge.closeTarget(targetId, CDP_CLOSE_TIMEOUT);
    } catch {
      // The bridge close fallback retries the target while the WebSocket is open.
    } finally {
      this.resetTargetState();
    }
  }

  async goto(url: string, options?: { waitUntil?: 'load' | 'none'; settleMs?: number; allowBoundNavigation?: boolean }): Promise<void> {
    if (!this._pageEnabled) {
      await this.bridge.send('Page.enable');
      this._pageEnabled = true;
    }
    const loadPromise = this.bridge.waitForEvent('Page.loadEventFired', 30_000).catch(() => {});
    await this.bridge.send('Page.navigate', { url });
    await loadPromise;
    this._lastUrl = url;
    if (options?.waitUntil !== 'none') {
      const maxMs = options?.settleMs ?? 1000;
      await this.evaluate(waitForDomStableJs(maxMs, Math.min(500, maxMs)));
    }
  }

  async evaluate<T = unknown>(js: string): Promise<T>;
  async evaluate<Args extends unknown[], T>(fn: BrowserEvaluateFunction<Args, T>, ...args: Args): Promise<Awaited<T>>;
  async evaluate(input: string | BrowserEvaluateFunction<unknown[], unknown>, ...args: unknown[]): Promise<unknown> {
    const expression = buildEvaluateExpression(input, args);
    const result = await this.bridge.send('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true,
    }) as RuntimeEvaluateResult;
    if (result.exceptionDetails) {
      throw new Error('Evaluate error: ' + (result.exceptionDetails.exception?.description || 'Unknown exception'));
    }
    return result.result?.value;
  }

  async getCookies(opts: { domain?: string; url?: string } = {}): Promise<BrowserCookie[]> {
    const result = await this.bridge.send('Network.getCookies', opts.url ? { urls: [opts.url] } : {});
    const cookies = isRecord(result) && Array.isArray(result.cookies) ? result.cookies : [];
    const domain = opts.domain;
    return domain
      ? cookies.filter((cookie): cookie is BrowserCookie => isCookie(cookie) && matchesCookieDomain(cookie.domain, domain))
      : cookies;
  }

  async screenshot(options: ScreenshotOptions = {}): Promise<string> {
    const fullPage = options.fullPage === true;
    const overrideWidth = options.width && options.width > 0 ? Math.ceil(options.width) : undefined;
    // height is ignored under fullPage so the captureBeyondViewport path stays unchanged for users who pass --height alongside --full-page.
    const overrideHeight = !fullPage && options.height && options.height > 0 ? Math.ceil(options.height) : undefined;
    const needsOverride = overrideWidth !== undefined || overrideHeight !== undefined;

    if (needsOverride) {
      if (overrideWidth !== undefined && fullPage) {
        await this.bridge.send('Emulation.setDeviceMetricsOverride', {
          mobile: false,
          width: overrideWidth,
          height: 0,
          deviceScaleFactor: 1,
        });
      }
      let finalWidth = overrideWidth ?? 0;
      let finalHeight = overrideHeight ?? 0;
      if (fullPage) {
        const metrics = await this.bridge.send('Page.getLayoutMetrics');
        const m = isRecord(metrics) ? metrics : {};
        const css = isRecord(m.cssContentSize) ? m.cssContentSize : undefined;
        const fb = isRecord(m.contentSize) ? m.contentSize : undefined;
        const size = css ?? fb;
        if (size && typeof size.width === 'number' && typeof size.height === 'number') {
          if (finalWidth === 0) finalWidth = Math.ceil(size.width);
          finalHeight = Math.ceil(size.height);
        }
      }
      await this.bridge.send('Emulation.setDeviceMetricsOverride', {
        mobile: false,
        width: finalWidth,
        height: finalHeight,
        deviceScaleFactor: 1,
      });
    }

    try {
      const result = await this.bridge.send('Page.captureScreenshot', {
        format: options.format ?? 'png',
        quality: options.format === 'jpeg' ? (options.quality ?? 80) : undefined,
        captureBeyondViewport: !needsOverride && fullPage,
      });
      const base64 = isRecord(result) && typeof result.data === 'string' ? result.data : '';
      if (options.path) {
        await saveBase64ToFile(base64, options.path);
      }
      return base64;
    } finally {
      if (needsOverride) {
        await this.bridge.send('Emulation.clearDeviceMetricsOverride').catch(() => {});
      }
    }
  }

  async startNetworkCapture(pattern: string = ''): Promise<boolean> {
    // Always update the filter pattern
    this._networkCapturePattern = pattern;

    // Reset state only on first start; avoid wiping entries if already capturing
    if (!this._networkCapturing) {
      this._networkEntries = [];
      this._pendingRequests.clear();
      this._pendingBodyFetches.clear();
      await this.bridge.send('Network.enable');

      // Step 1: Record request method/url on requestWillBeSent
      this.bridge.on('Network.requestWillBeSent', (params: unknown) => {
        const p = params as {
          requestId: string;
          request: {
            method: string;
            url: string;
            headers?: Record<string, unknown>;
            postData?: string;
            hasPostData?: boolean;
          };
          timestamp: number;
        };
        if (!this._networkCapturePattern || p.request.url.includes(this._networkCapturePattern)) {
          const rawBody = typeof p.request.postData === 'string' ? p.request.postData : '';
          const bodyTruncated = rawBody.length > CDP_REQUEST_BODY_CAPTURE_LIMIT;
          const idx = this._networkEntries.push({
            url: p.request.url,
            method: p.request.method,
            requestHeaders: Object.fromEntries(
              Object.entries(p.request.headers ?? {}).map(([name, value]) => [name, String(value)]),
            ),
            requestBodyKind: p.request.hasPostData ? 'string' : 'empty',
            requestBodyPreview: bodyTruncated ? rawBody.slice(0, CDP_REQUEST_BODY_CAPTURE_LIMIT) : rawBody,
            requestBodyFullSize: rawBody.length,
            requestBodyTruncated: bodyTruncated,
            timestamp: Date.now(),
          }) - 1;
          this._pendingRequests.set(p.requestId, idx);

          if (p.request.hasPostData && p.request.postData === undefined) {
            const requestBodyFetch = this.bridge.send('Network.getRequestPostData', { requestId: p.requestId }).then((result: unknown) => {
              const postData = (result as { postData?: string } | undefined)?.postData;
              if (typeof postData !== 'string') return;
              const truncated = postData.length > CDP_REQUEST_BODY_CAPTURE_LIMIT;
              this._networkEntries[idx].requestBodyPreview = truncated
                ? postData.slice(0, CDP_REQUEST_BODY_CAPTURE_LIMIT)
                : postData;
              this._networkEntries[idx].requestBodyFullSize = postData.length;
              this._networkEntries[idx].requestBodyTruncated = truncated;
            }).catch(() => {
              // Some request types do not expose post data.
            }).finally(() => {
              this._pendingBodyFetches.delete(requestBodyFetch);
            });
            this._pendingBodyFetches.add(requestBodyFetch);
          }
        }
      });

      // Step 2: Fill in response metadata on responseReceived
      this.bridge.on('Network.responseReceived', (params: unknown) => {
        const p = params as { requestId: string; response: { status: number; mimeType?: string } };
        const idx = this._pendingRequests.get(p.requestId);
        if (idx !== undefined) {
          this._networkEntries[idx].responseStatus = p.response.status;
          this._networkEntries[idx].responseContentType = p.response.mimeType || '';
        }
      });

      // Step 3: Fetch body on loadingFinished (body is only reliably available after this)
      this.bridge.on('Network.loadingFinished', (params: unknown) => {
        const p = params as { requestId: string };
        const idx = this._pendingRequests.get(p.requestId);
        if (idx !== undefined) {
          const bodyFetch = this.bridge.send('Network.getResponseBody', { requestId: p.requestId }).then((result: unknown) => {
            const r = result as { body?: string; base64Encoded?: boolean } | undefined;
            if (typeof r?.body === 'string') {
              const fullSize = r.body.length;
              const truncated = fullSize > CDP_RESPONSE_BODY_CAPTURE_LIMIT;
              const body = truncated ? r.body.slice(0, CDP_RESPONSE_BODY_CAPTURE_LIMIT) : r.body;
              this._networkEntries[idx].responsePreview = r.base64Encoded ? `base64:${body}` : body;
              this._networkEntries[idx].responseBodyFullSize = fullSize;
              this._networkEntries[idx].responseBodyTruncated = truncated;
            }
          }).catch((err) => {
            // Body unavailable for some requests (e.g. uploads) — non-fatal
            if (process.env.OPENCLI_VERBOSE) {
              // eslint-disable-next-line no-console
              console.error(`[cdp] getResponseBody failed for ${p.requestId}:`, err instanceof Error ? err.message : err);
            }
          }).finally(() => {
            this._pendingBodyFetches.delete(bodyFetch);
          });
          this._pendingBodyFetches.add(bodyFetch);
          this._pendingRequests.delete(p.requestId);
        }
      });

      this._networkCapturing = true;
    }
    return true;
  }

  async readNetworkCapture(): Promise<unknown[]> {
    // Await all in-flight body fetches so entries have responsePreview populated
    if (this._pendingBodyFetches.size > 0) {
      await Promise.all([...this._pendingBodyFetches]);
    }
    const entries = [...this._networkEntries];
    this._networkEntries = [];
    return entries;
  }

  async consoleMessages(level: string = 'all'): Promise<Array<{ type: string; text: string; timestamp: number }>> {
    if (!this._consoleCapturing) {
      await this.bridge.send('Runtime.enable');
      this.bridge.on('Runtime.consoleAPICalled', (params: unknown) => {
        const p = params as { type: string; args: Array<{ value?: unknown; description?: string }>; timestamp: number };
        const text = (p.args || []).map(a => a.value !== undefined ? String(a.value) : (a.description || '')).join(' ');
        this._consoleMessages.push({ type: p.type, text, timestamp: Date.now() });
        if (this._consoleMessages.length > 500) this._consoleMessages.shift();
      });
      // Capture uncaught exceptions as error-level messages
      this.bridge.on('Runtime.exceptionThrown', (params: unknown) => {
        const p = params as { timestamp: number; exceptionDetails?: { exception?: { description?: string }; text?: string } };
        const desc = p.exceptionDetails?.exception?.description || p.exceptionDetails?.text || 'Unknown exception';
        this._consoleMessages.push({ type: 'error', text: desc, timestamp: Date.now() });
        if (this._consoleMessages.length > 500) this._consoleMessages.shift();
      });
      this._consoleCapturing = true;
    }
    if (level === 'all') return [...this._consoleMessages];
    // 'error' level includes both console.error() and uncaught exceptions
    if (level === 'error') return this._consoleMessages.filter(m => m.type === 'error');
    return this._consoleMessages.filter(m => m.type === level);
  }

  async tabs(): Promise<unknown[]> {
    const targets = await this.bridge.listTargets();
    return targets.map((target) => ({
      page: target.targetId,
      url: target.url ?? '',
      title: target.title ?? '',
    }));
  }

  getActivePage(): string | undefined {
    return this.bridge.getActiveTarget();
  }

  setActivePage(page?: string): void {
    if (page && page !== this.bridge.getActiveTarget()) {
      throw new Error(`Direct CDP page is not attached to target ${page}; use selectTab first.`);
    }
  }

  async newTab(url?: string): Promise<string | undefined> {
    const targetId = await this.bridge.createTarget();
    await this.bridge.selectTarget(targetId);
    this.resetTargetState();
    await this.bridge.send('Page.addScriptToEvaluateOnNewDocument', { source: generateStealthJs() });
    if (url) await this.goto(url);
    return targetId;
  }

  async closeTab(target?: number | string): Promise<void> {
    const targetId = await this.resolveTarget(target);
    const wasActive = targetId === this.bridge.getActiveTarget();
    await this.bridge.closeTarget(targetId);
    if (wasActive) this.resetTargetState();
  }

  async selectTab(target: number | string): Promise<void> {
    const targetId = await this.resolveTarget(target);
    await this.bridge.selectTarget(targetId);
    this.resetTargetState();
  }

  private async resolveTarget(target?: number | string): Promise<string> {
    if (typeof target === 'string' && target) return target;
    if (target === undefined) {
      const active = this.bridge.getActiveTarget();
      if (active) return active;
    }
    const targets = await this.bridge.listTargets();
    const index = typeof target === 'number' ? target : 0;
    const resolved = targets[index]?.targetId;
    if (!resolved) throw new Error(`CDP page target ${String(target)} was not found`);
    return resolved;
  }

  private resetTargetState(): void {
    this._pageEnabled = false;
    this._networkCapturing = false;
    this._networkEntries = [];
    this._pendingRequests.clear();
    this._pendingBodyFetches.clear();
    this._consoleMessages = [];
    this._consoleCapturing = false;
    this._lastUrl = null;
  }

  async cdp(method: string, params: Record<string, unknown> = {}): Promise<unknown> {
    return this.bridge.send(method, params);
  }

  async insertText(text: string): Promise<void> {
    await this.nativeType(text);
  }
}

function isCookie(value: unknown): value is BrowserCookie {
  return isRecord(value)
    && typeof value.name === 'string'
    && typeof value.value === 'string'
    && typeof value.domain === 'string';
}

function matchesCookieDomain(cookieDomain: string, targetDomain: string): boolean {
  const normalizedCookieDomain = cookieDomain.replace(/^\./, '').toLowerCase();
  const normalizedTargetDomain = targetDomain.replace(/^\./, '').toLowerCase();
  return normalizedTargetDomain === normalizedCookieDomain
    || normalizedTargetDomain.endsWith(`.${normalizedCookieDomain}`);
}

interface BrowserTargetInfo {
  targetId: string;
  type?: string;
  url?: string;
  title?: string;
}

function isBrowserScopedCdpMethod(method: string): boolean {
  return method.startsWith('Target.') || method.startsWith('Browser.');
}

function selectBrowserPageTarget(targetInfos: unknown[]): BrowserTargetInfo | undefined {
  const mapped: Array<{ target: BrowserTargetInfo; cdp: CDPTarget }> = [];
  for (const raw of targetInfos) {
    if (!isRecord(raw) || typeof raw.targetId !== 'string' || !raw.targetId) {
      continue;
    }
    const target: BrowserTargetInfo = {
      targetId: raw.targetId,
      type: typeof raw.type === 'string' ? raw.type : undefined,
      url: typeof raw.url === 'string' ? raw.url : undefined,
      title: typeof raw.title === 'string' ? raw.title : undefined,
    };
    mapped.push({
      target,
      cdp: {
        type: target.type,
        url: target.url,
        title: target.title,
        webSocketDebuggerUrl: target.targetId,
      },
    });
  }
  const selected = selectCDPTarget(mapped.map((entry) => entry.cdp));
  return mapped.find((entry) => entry.cdp === selected)?.target;
}

function selectCDPTarget(targets: CDPTarget[]): CDPTarget | undefined {
  const preferredPattern = compilePreferredPattern(process.env.OPENCLI_CDP_TARGET);

  const candidates = targets
    .map((target, index) => ({ target, index, score: scoreCDPTarget(target, preferredPattern) }))
    .filter(({ score }) => Number.isFinite(score));

  // Electron apps route auxiliary windows onto the main document through a
  // query: Codex ships its avatar overlay as
  // `app://-/index.html?initialRoute=%2Favatar-overlay`, which answers `/json`
  // first and scores exactly like the main window, so document order used to
  // send every command to a surface with no app UI (#2242). Only break that
  // tie; a routed window that outscores its plain sibling is still the better
  // target, and on http(s) a query is ordinary page state.
  const plainDocuments = new Set<string>();
  for (const { target } of candidates) {
    const url = parseLocalDocumentUrl(target.url);
    if (url && !url.search) plainDocuments.add(toDocumentKey(url));
  }

  const ranked = candidates
    .map((entry) => {
      const url = parseLocalDocumentUrl(entry.target.url);
      return { ...entry, routed: !!url && !!url.search && plainDocuments.has(toDocumentKey(url)) };
    })
    .sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score;
      if (a.routed !== b.routed) return a.routed ? 1 : -1;
      return a.index - b.index;
    });

  return ranked[0]?.target;
}

function scoreCDPTarget(target: CDPTarget, preferredPattern?: RegExp): number {
  if (!target.webSocketDebuggerUrl) return Number.NEGATIVE_INFINITY;

  const type = (target.type ?? '').toLowerCase();
  const url = (target.url ?? '').toLowerCase();
  const title = (target.title ?? '').toLowerCase();
  const haystack = `${title} ${url}`;

  if (!haystack.trim() && !type) return Number.NEGATIVE_INFINITY;
  if (haystack.includes('devtools')) return Number.NEGATIVE_INFINITY;
  if (type === 'background_page' || type === 'service_worker') return Number.NEGATIVE_INFINITY;

  let score = 0;

  if (preferredPattern && preferredPattern.test(haystack)) score += 1000;

  if (type === 'app') score += 120;
  else if (type === 'webview') score += 100;
  else if (type === 'page') score += 80;
  else if (type === 'iframe') score += 20;

  if (url.startsWith('http://localhost') || url.startsWith('https://localhost')) score += 90;
  if (url.startsWith('file://')) score += 60;
  if (url.startsWith('http://127.0.0.1') || url.startsWith('https://127.0.0.1')) score += 50;
  if (url.startsWith('about:blank')) score -= 120;
  if (url === '' || url === 'about:blank') score -= 40;

  if (title && title !== 'devtools') score += 25;

  // Boost score for known Electron app names from the registry (builtin + user-defined)
  const appNames = Object.values(getAllElectronApps()).map(a => (a.displayName ?? a.processName).toLowerCase());
  for (const name of appNames) {
    if (title.includes(name)) { score += 120; break; }
  }
  for (const name of appNames) {
    if (url.includes(name)) { score += 100; break; }
  }

  return score;
}

// Keep this explicit: these are the schemes Electron serves a shared local
// document over, where a query is a window route (#2242). A "not http(s)"
// check would also sweep in schemes we know nothing about.
const LOCAL_DOCUMENT_SCHEMES = new Set(['app:', 'file:']);

/**
 * Parse a URL that names a local app document eligible for routed-window
 * demotion (#2242).
 *
 * Only allowlisted schemes qualify: on http(s), and on any scheme we cannot
 * vouch for, a query is ordinary page state, so returning null there keeps
 * plain document-order selection.
 */
function parseLocalDocumentUrl(raw: string | undefined): URL | null {
  try {
    const url = new URL(raw ?? '');
    return LOCAL_DOCUMENT_SCHEMES.has(url.protocol) ? url : null;
  } catch {
    return null;
  }
}

function toDocumentKey(url: URL): string {
  return `${url.protocol}//${url.host}${url.pathname}`;
}

function compilePreferredPattern(raw: string | undefined): RegExp | undefined {
  const value = raw?.trim();
  if (!value) return undefined;
  return new RegExp(escapeRegExp(value.toLowerCase()));
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function isolatedAdapterTargetError(cause: unknown): Error {
  const error = new Error(ISOLATED_ADAPTER_TARGET_ERROR);
  error.cause = cause;
  return error;
}

async function resolveBrowserWebSocketUrl(endpoint: string): Promise<string> {
  try {
    const parsed = new URL(endpoint);
    const pathname = parsed.pathname.replace(/\/+$/, '');
    if (pathname.endsWith('/json/version')) {
      parsed.pathname = pathname;
    } else {
      const basePath = pathname.endsWith('/json') ? pathname.slice(0, -5) : pathname;
      parsed.pathname = `${basePath}/json/version`;
    }
    const version = await fetchJsonDirect(parsed.toString());
    if (isRecord(version) && typeof version.webSocketDebuggerUrl === 'string' && version.webSocketDebuggerUrl) {
      return version.webSocketDebuggerUrl;
    }
  } catch (err) {
    throw isolatedAdapterTargetError(err);
  }
  throw isolatedAdapterTargetError(new Error('CDP browser WebSocket URL is unavailable'));
}

export const __test__ = {
  selectCDPTarget,
  scoreCDPTarget,
  selectBrowserPageTarget,
};

function fetchJsonDirect(url: string): Promise<unknown> {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const request = (parsed.protocol === 'https:' ? httpsRequest : httpRequest)(parsed, (res) => {
      const statusCode = res.statusCode ?? 0;
      if (statusCode < 200 || statusCode >= 300) {
        res.resume();
        reject(new Error(`Failed to fetch CDP targets: HTTP ${statusCode}`));
        return;
      }

      const chunks: Buffer[] = [];
      res.on('data', (chunk) => chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)));
      res.on('end', () => {
        try {
          resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
        } catch (error) {
          reject(error instanceof Error ? error : new Error(String(error)));
        }
      });
    });

    request.on('error', reject);
    request.setTimeout(10_000, () => request.destroy(new Error('Timed out fetching CDP targets')));
    request.end();
  });
}
