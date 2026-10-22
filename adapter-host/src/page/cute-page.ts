/**
 * CutePage — bsk-backed `IPage` implementation (spec 021, Phase 4).
 *
 * This adapter deliberately delegates wire construction to `src/bsk/commands/**`.
 * It only translates the IPage contract to bsk command arguments and adapts the
 * richer OpenCLI result shapes where the bsk protocol is intentionally smaller.
 */

import type {
  BrowserCookie,
  BrowserDownloadWaitResult,
  BrowserEvaluateFunction,
  FetchJsonOptions,
  ScreenshotOptions,
  SnapshotOptions,
  WaitOptions,
} from '../types.js';
import { buildEvaluateExpression } from '../browser/utils.js';
import { saveBase64ToFile } from '../utils.js';
import {
  click as bskClick,
  console as bskConsole,
  cookies as bskCookies,
  evaluate as bskEvaluate,
  fill as bskFill,
  focus as bskFocus,
  hover as bskHover,
  navigate as bskNavigate,
  network as bskNetwork,
  press as bskPress,
  readScreenshotCapture,
  screenshot as bskScreenshot,
  screenshotFullPage as bskScreenshotFullPage,
  scrollTo as bskScrollTo,
  snapshot as bskSnapshot,
  tabClose,
  tabCreate,
  tabList,
  tabSelect,
  wheel as bskWheel,
  type BskCaller,
} from '../bsk/commands/index.js';
import type { IPage } from './ipage.js';

/** Minimal RPC surface CutePage needs from a bsk session. */
export interface BskSessionHandle {
  call<T>(method: string, params: unknown, opts?: { timeoutMs?: number; signal?: AbortSignal }): Promise<T>;
  sessionId: string;
}

/**
 * Thrown by `CutePage` methods that have no bsk implementation.
 *
 * Carries the method name and the suggested replacement so callers can surface
 * an actionable message instead of a bare "not implemented".
 */
export class NotImplementedError extends Error {
  constructor(
    public readonly method: string,
    public readonly suggestion: string,
  ) {
    super(`IPage.${method} is not implemented by the bsk backend yet. Suggested alternative: ${suggestion}`);
    this.name = 'NotImplementedError';
  }
}

const DEFAULT_SUGGESTION = 'set BROWSER_BACKEND=opencli to use the existing OpenCLI backend';

/** Per-method guidance for operations that remain outside the bsk seam. */
const METHOD_SUGGESTIONS: Record<string, string> = {
  goto: 'bsk RPC tool.navigate',
  evaluate: 'bsk RPC tool.evaluate (await_promise=true, return result.value)',
  snapshot: 'bsk RPC tool.snapshot',
  click: 'bsk RPC tool.click',
  fillText: 'bsk RPC tool.fill',
  typeText: 'bsk RPC tool.fill with clear_before=false',
  pressKey: 'bsk RPC tool.press',
  wait: 'client-side sleep plus tool.evaluate polling; tool.wait_ms is daemon-only',
  getCookies: 'bsk exposes no cookie RPC; document.cookie cannot read httpOnly cookies, so use the OpenCLI backend',
  uploadFiles: 'file staging is daemon-local; remote bsk sessions reject tool.upload',
  setFileInput: 'file staging is daemon-local; remote bsk sessions do not expose CDP file input',
  waitForDownload: 'file staging is daemon-local; remote bsk sessions reject tool.download',
  dblClick: 'bsk RPC tool.click with click_count=2',
  hover: 'bsk RPC tool.hover',
  focus: 'bsk RPC tool.focus',
  scrollTo: 'bsk RPC tool.scroll_to',
  scroll: 'bsk RPC tool.wheel',
  tabs: 'bsk RPC tool.tab_list',
  newTab: 'bsk RPC tool.tab_create',
  closeTab: 'bsk RPC tool.tab_close',
  selectTab: 'bsk RPC tool.tab_select',
  screenshot: 'bsk RPC tool.screenshot',
  consoleMessages: 'bsk RPC tool.console',
  networkRequests: 'bsk RPC tool.network',
  installInterceptor: 'bsk has no page interceptor API; use tool.network',
  getInterceptedRequests: 'bsk has no page interceptor API; use tool.network',
  waitForCapture: 'bsk has no page interceptor API; use tool.network polling',
};

function notImplemented(method: string): never {
  throw new NotImplementedError(method, METHOD_SUGGESTIONS[method] ?? DEFAULT_SUGGESTION);
}

type MatchLevel = 'exact' | 'stable' | 'reidentified';

type TargetResolution = {
  used_ref?: string;
  used_selector?: string;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function tabIdFrom(value: unknown): number | undefined {
  if (!isRecord(value)) return undefined;
  const tabId = value.tab_id;
  return typeof tabId === 'number' && Number.isInteger(tabId) ? tabId : undefined;
}

function normaliseRef(value: string): string {
  return value.startsWith('@') ? value.slice(1) : value;
}

/**
 * The bsk resolver always returns one target on a successful interaction but
 * does not carry OpenCLI's resolver envelope. Synthesize that envelope from
 * the target bsk actually used; a changed used target is a re-identification.
 */
function synthesizeMatch(target: string, result: TargetResolution): {
  matches_n: number;
  match_level: MatchLevel;
} {
  const used = result.used_ref ?? result.used_selector;
  if (used === undefined) return { matches_n: 1, match_level: 'exact' };
  const exact = result.used_selector === target
    || normaliseRef(used) === normaliseRef(target);
  return { matches_n: 1, match_level: exact ? 'exact' : 'reidentified' };
}

function tabIdForTarget(target: number | string): number {
  if (typeof target === 'number' && Number.isInteger(target)) return target;
  const parsed = Number(String(target).trim());
  if (Number.isSafeInteger(parsed)) return parsed;
  throw new Error(`bsk tab ids are numeric; received ${String(target)}`);
}

interface FetchEnvelope {
  ok: boolean;
  status: number;
  statusText: string;
  text: string;
  url: string;
  error?: string;
}

function fetchEnvelopeExpression(request: {
  url: string;
  method: string;
  headers: Record<string, string>;
  body?: unknown;
  timeoutMs: number;
}): string {
  const serialised = JSON.stringify(request);
  if (serialised === undefined) throw new Error('fetchJson options must be JSON-serializable');
  return `(async () => {
    const request = ${serialised};
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), request.timeoutMs);
    try {
      const headers = { Accept: 'application/json', ...(request.headers || {}) };
      const init = {
        method: request.method,
        credentials: 'include',
        headers,
        signal: controller.signal,
      };
      if (Object.prototype.hasOwnProperty.call(request, 'body')) {
        if (!Object.keys(headers).some((key) => key.toLowerCase() === 'content-type')) {
          headers['Content-Type'] = 'application/json';
        }
        init.body = typeof request.body === 'string' ? request.body : JSON.stringify(request.body);
      }
      const response = await fetch(request.url, init);
      return {
        ok: response.ok,
        status: response.status,
        statusText: response.statusText,
        text: await response.text(),
        url: response.url || request.url,
      };
    } catch (error) {
      return {
        ok: false,
        status: 0,
        statusText: '',
        text: '',
        url: request.url,
        error: error instanceof Error ? error.message : String(error),
      };
    } finally {
      clearTimeout(timer);
    }
  })()`;
}

function staticNetworkEntry(entry: unknown): boolean {
  const type = isRecord(entry)
    ? String(entry.resource_type ?? entry.type ?? '').toLowerCase()
    : '';
  return type === 'image'
    || type === 'img'
    || type === 'font'
    || type === 'css'
    || type === 'stylesheet'
    || type === 'script';
}

export class CutePage implements IPage {
  private activeTabId: number | undefined;

  constructor(readonly handle: BskSessionHandle) {}

  private caller(): BskCaller {
    // BskSessionHandle intentionally keeps a string method name so the page
    // seam can remain independent from the command-layer Method union.
    return this.handle as unknown as BskCaller;
  }

  private rememberTab(result: unknown): void {
    const tabId = tabIdFrom(result);
    if (tabId !== undefined) this.activeTabId = tabId;
  }

  private async pollExpression(
    expression: string,
    timeoutSeconds: number,
    description: string,
  ): Promise<void> {
    const timeoutMs = Math.max(0, timeoutSeconds * 1000);
    const deadline = Date.now() + timeoutMs;
    let first = true;
    while (first || Date.now() <= deadline) {
      first = false;
      if (await this.evaluate<boolean>(expression)) return;
      const remaining = deadline - Date.now();
      if (remaining <= 0) break;
      await this.sleep(Math.min(0.1, remaining / 1000));
    }
    throw new Error(`Timed out waiting for ${description} after ${timeoutSeconds}s`);
  }

  // ── Core methods required by the pipeline ────────────────────────────────

  async goto(
    url: string,
    options?: { waitUntil?: 'load' | 'none'; settleMs?: number },
  ): Promise<void> {
    const result = await bskNavigate(this.caller(), {
      sessionId: this.handle.sessionId,
      url,
      // bsk has no "none" checkpoint; `commit` is the earliest lifecycle event
      // and is the closest equivalent to OpenCLI's settle-free navigation.
      ...(options?.waitUntil === 'none' ? { waitUntil: 'commit' as const } : {}),
    });
    this.rememberTab(result);
    if (result?.error_text) throw new Error(result.error_text);
    if (result?.reached === 'timeout') {
      throw new Error(`Navigation timed out while waiting for ${url}`);
    }
  }

  async evaluate<T = any>(js: string): Promise<T>;
  async evaluate<Args extends unknown[], T>(fn: BrowserEvaluateFunction<Args, T>, ...args: Args): Promise<Awaited<T>>;
  async evaluate(_input: string | BrowserEvaluateFunction<unknown[], unknown>, ..._args: unknown[]): Promise<any> {
    const expression = buildEvaluateExpression(_input as never, _args);
    const result = await bskEvaluate(this.caller(), {
      sessionId: this.handle.sessionId,
      expression,
    });
    if (!result || result.ok !== true) {
      const errorText = result?.error?.text ?? 'evaluate failed';
      throw new Error(errorText);
    }
    this.rememberTab(result);
    return result.value;
  }

  async snapshot(opts: SnapshotOptions = {}): Promise<any> {
    const result = await bskSnapshot(this.caller(), {
      sessionId: this.handle.sessionId,
      ...(opts.maxDepth === undefined ? {} : { maxDepth: opts.maxDepth }),
    });
    this.rememberTab(result);
    if (!result || typeof result.text !== 'string') {
      throw new Error('bsk snapshot returned an invalid result');
    }
    return result;
  }

  async click(
    ref: string,
    _opts?: { nth?: number; firstOnMulti?: boolean },
  ): Promise<{
    matches_n: number;
    match_level: MatchLevel;
    click_method?: 'cdp' | 'js' | 'ax';
    hit?: 'target' | 'ancestor' | 'other' | 'none';
    retargeted?: boolean;
  }> {
    const result = await bskClick(this.caller(), {
      sessionId: this.handle.sessionId,
      target: ref,
    });
    this.rememberTab(result);
    // bsk reports the resolved target and click coordinates, not OpenCLI's
    // resolver envelope. A successful bsk click is a native CDP click on one target.
    return { ...synthesizeMatch(ref, result), click_method: 'cdp' };
  }

  async fillText(
    ref: string,
    text: string,
    _opts?: { nth?: number; firstOnMulti?: boolean },
  ): Promise<{
    filled: boolean;
    verified: boolean;
    expected: string;
    actual: string;
    length: number;
    matches_n: number;
    match_level: MatchLevel;
    mode?: 'input' | 'textarea' | 'contenteditable';
  }> {
    const result = await bskFill(this.caller(), {
      sessionId: this.handle.sessionId,
      target: ref,
      value: text,
    });
    this.rememberTab(result);
    // tool.fill only exposes value_length; success already means the extension
    // verified the value, so restore the richer OpenCLI fillText envelope.
    const match = synthesizeMatch(ref, result);
    const length = typeof result.value_length === 'number' ? result.value_length : text.length;
    return {
      ...match,
      filled: true,
      verified: true,
      expected: text,
      actual: text,
      length,
    };
  }

  async typeText(
    ref: string,
    text: string,
    _opts?: { nth?: number; firstOnMulti?: boolean },
  ): Promise<{ matches_n: number; match_level: MatchLevel }> {
    const result = await bskFill(this.caller(), {
      sessionId: this.handle.sessionId,
      target: ref,
      value: text,
      // OpenCLI typeText appends; bsk fill otherwise clears by default.
      noClear: true,
    });
    this.rememberTab(result);
    return synthesizeMatch(ref, result);
  }

  async pressKey(key: string): Promise<void> {
    const result = await bskPress(this.caller(), {
      sessionId: this.handle.sessionId,
      key,
    });
    this.rememberTab(result);
  }

  async wait(options: number | WaitOptions): Promise<void> {
    // `tool.wait_ms` is daemon-only and the extension returns unknown_method.
    // Waits therefore stay client-side, with evaluate polling for page state.
    if (typeof options === 'number') {
      await this.sleep(options);
      return;
    }
    if (typeof options.time === 'number') {
      await this.sleep(options.time);
      return;
    }
    if (options.selector !== undefined) {
      const selector = JSON.stringify(options.selector);
      await this.pollExpression(
        `(() => document.querySelector(${selector}) !== null)()`,
        options.timeout ?? 10,
        `selector ${JSON.stringify(options.selector)}`,
      );
      return;
    }
    if (options.text !== undefined) {
      const text = JSON.stringify(options.text);
      await this.pollExpression(
        `(() => document.body?.innerText?.includes(${text}) ?? false)()`,
        options.timeout ?? 30,
        `text ${JSON.stringify(options.text)}`,
      );
    }
  }

  // ── Additional bsk-backed methods ─────────────────────────────────────────

  async fetchJson(url: string, opts: FetchJsonOptions = {}): Promise<unknown> {
    const request = {
      url,
      method: opts.method ?? 'GET',
      headers: opts.headers ?? {},
      ...(opts.body === undefined ? {} : { body: opts.body }),
      timeoutMs: opts.timeoutMs ?? 15_000,
    };
    const result = await this.evaluate<FetchEnvelope>(fetchEnvelopeExpression(request));
    if (!result || result.error) {
      throw new Error(`Browser fetch failed for ${result?.url ?? url}: ${result?.error ?? 'unknown error'}`);
    }
    if (!result.ok) {
      throw new Error(`HTTP ${result.status}${result.statusText ? ` ${result.statusText}` : ''} from ${result.url || url}`);
    }
    if (!result.text.trim()) return null;
    try {
      return JSON.parse(result.text);
    } catch {
      throw new Error(`Expected JSON from ${result.url || url}`);
    }
  }

  async getCookies(opts?: { domain?: string; url?: string }): Promise<BrowserCookie[]> {
    const result = await bskCookies(this.caller(), {
      sessionId: this.handle.sessionId,
      ...(opts?.domain === undefined ? {} : { domain: opts.domain }),
      ...(opts?.url === undefined ? {} : { url: opts.url }),
    });
    if (!result || !Array.isArray(result.cookies)) {
      throw new Error('bsk cookies returned an invalid result');
    }
    return result.cookies.map((cookie) => ({
      name: cookie.name,
      value: cookie.value,
      domain: cookie.domain,
      path: cookie.path,
      secure: cookie.secure,
      httpOnly: cookie.http_only,
      ...(cookie.expiration_date === undefined ? {} : { expirationDate: cookie.expiration_date }),
    }));
  }

  async dblClick(
    ref: string,
    _opts?: { nth?: number; firstOnMulti?: boolean },
  ): Promise<{ matches_n: number; match_level: MatchLevel }> {
    const result = await bskClick(this.caller(), {
      sessionId: this.handle.sessionId,
      target: ref,
      clickCount: 2,
    });
    this.rememberTab(result);
    return synthesizeMatch(ref, result);
  }

  async hover(
    ref: string,
    _opts?: { nth?: number; firstOnMulti?: boolean },
  ): Promise<{ matches_n: number; match_level: MatchLevel }> {
    const result = await bskHover(this.caller(), {
      sessionId: this.handle.sessionId,
      target: ref,
    });
    this.rememberTab(result);
    return synthesizeMatch(ref, result);
  }

  async focus(
    ref: string,
    _opts?: { nth?: number; firstOnMulti?: boolean },
  ): Promise<{ focused: boolean; matches_n: number; match_level: MatchLevel }> {
    const result = await bskFocus(this.caller(), {
      sessionId: this.handle.sessionId,
      target: ref,
    });
    this.rememberTab(result);
    return { focused: result.focused, ...synthesizeMatch(ref, result) };
  }

  async setChecked(
    _ref: string,
    _checked: boolean,
    _opts?: { nth?: number; firstOnMulti?: boolean },
  ): Promise<{ checked: boolean; changed: boolean; matches_n: number; match_level: MatchLevel; kind?: string }> {
    notImplemented('setChecked');
  }

  async uploadFiles(
    _ref: string,
    _files: string[],
    _opts?: { nth?: number; firstOnMulti?: boolean },
  ): Promise<{ uploaded: boolean; files: number; file_names: string[]; target: string; matches_n: number; match_level: MatchLevel; multiple?: boolean; accept?: string }> {
    // Do not call tool.upload: file staging is daemon-local and remote sessions
    // are explicitly rejected before the extension receives the request.
    notImplemented('uploadFiles');
  }

  async drag(
    _source: string,
    _target: string,
    _opts?: { from?: { nth?: number; firstOnMulti?: boolean }; to?: { nth?: number; firstOnMulti?: boolean } },
  ): Promise<{ dragged: boolean; source: string; target: string; source_matches_n: number; target_matches_n: number; source_match_level: MatchLevel; target_match_level: MatchLevel }> {
    notImplemented('drag');
  }

  async scrollTo(ref: string, _opts?: { nth?: number; firstOnMulti?: boolean }): Promise<any> {
    const result = await bskScrollTo(this.caller(), {
      sessionId: this.handle.sessionId,
      target: ref,
    });
    this.rememberTab(result);
    // Preserve geometry while adding the resolver fields expected by OpenCLI callers.
    return { ...result, ...synthesizeMatch(ref, result) };
  }

  async getFormState(): Promise<any> {
    notImplemented('getFormState');
  }

  async sleep(seconds: number): Promise<void> {
    // Pure local sleep: unlike tool.wait_ms this must never issue an RPC.
    const delayMs = Math.max(0, seconds * 1000);
    await new Promise<void>((resolve) => setTimeout(resolve, delayMs));
  }

  async waitForDownload(_pattern?: string, _timeoutMs?: number): Promise<BrowserDownloadWaitResult> {
    // tool.download is daemon-local file staging and is rejected for remote sessions.
    notImplemented('waitForDownload');
  }

  async tabs(): Promise<any> {
    const result = await tabList(this.caller(), { sessionId: this.handle.sessionId });
    if (!result || !Array.isArray(result.tabs)) throw new Error('bsk tab_list returned an invalid result');
    const active = result.tabs.find((tab) => tab.active === true);
    if (active) this.activeTabId = active.tab_id;
    // OpenCLI callers (e.g. src/cli.ts tab validation) key tabs by the `page`
    // target id; bsk uses numeric `tab_id`, so expose it under both names.
    return result.tabs.map((tab) => ({ ...tab, page: String(tab.tab_id) }));
  }

  async closeTab(target?: number | string): Promise<void> {
    let tabId: number | undefined = target === undefined ? this.activeTabId : tabIdForTarget(target);
    if (tabId === undefined) {
      const tabs: Array<Record<string, unknown>> = await this.tabs();
      const candidate = tabs.find((tab) => tab.active === true) ?? tabs[0];
      tabId = tabIdFrom(candidate);
    }
    if (tabId === undefined) throw new Error('cannot close a bsk tab without a tab id');
    await tabClose(this.caller(), { sessionId: this.handle.sessionId, tabId });
    if (this.activeTabId === tabId) this.activeTabId = undefined;
  }

  async newTab(_url?: string): Promise<string | undefined> {
    const result = await tabCreate(this.caller(), {
      sessionId: this.handle.sessionId,
      ...(_url === undefined ? {} : { url: _url }),
    });
    if (!result || !Number.isInteger(result.tab_id)) throw new Error('bsk tab_create returned an invalid tab id');
    this.activeTabId = result.tab_id;
    return String(result.tab_id);
  }

  async selectTab(target: number | string): Promise<void> {
    const tabId = tabIdForTarget(target);
    const result = await tabSelect(this.caller(), { sessionId: this.handle.sessionId, tabId });
    this.activeTabId = tabIdFrom(result) ?? tabId;
  }

  async networkRequests(includeStatic = false): Promise<any> {
    const result = await bskNetwork(this.caller(), { sessionId: this.handle.sessionId });
    this.rememberTab(result);
    if (!result || !Array.isArray(result.entries)) throw new Error('bsk network returned an invalid result');
    return includeStatic ? result.entries : result.entries.filter((entry) => !staticNetworkEntry(entry));
  }

  async consoleMessages(level = 'info'): Promise<any> {
    const result = await bskConsole(this.caller(), { sessionId: this.handle.sessionId });
    this.rememberTab(result);
    if (!result || !Array.isArray(result.entries)) throw new Error('bsk console returned an invalid result');
    if (level === 'all') return result.entries;
    const wanted = level.toLowerCase();
    return result.entries.filter((entry) => String(entry.level ?? '').toLowerCase() === wanted);
  }

  async scroll(direction = 'down', amount = 500): Promise<void> {
    const normalised = direction.toLowerCase();
    let deltaX = 0;
    let deltaY = 0;
    if (normalised === 'up') deltaY = -amount;
    else if (normalised === 'down') deltaY = amount;
    else if (normalised === 'left') deltaX = -amount;
    else if (normalised === 'right') deltaX = amount;
    else throw new Error(`unknown scroll direction: ${direction}`);
    const result = await bskWheel(this.caller(), {
      sessionId: this.handle.sessionId,
      deltaX,
      deltaY,
    });
    this.rememberTab(result);
  }

  // ── Methods intentionally outside the bsk remote protocol ─────────────────

  async evaluateWithArgs(js: string, args: Record<string, unknown>): Promise<any> {
    // Same contract as BasePage.evaluateWithArgs: declare each argument as a
    // const so caller JS never string-interpolates untrusted values.
    const declarations = Object.entries(args)
      .map(([key, value]) => {
        if (!/^[a-zA-Z_$][a-zA-Z0-9_$]*$/.test(key)) {
          throw new Error(`evaluateWithArgs: invalid key "${key}"`);
        }
        return `const ${key} = ${JSON.stringify(value)};`;
      })
      .join('\n');
    return this.evaluate(`{\n${declarations}\n${js}\n}`);
  }

  async autoScroll(_options?: { times?: number; delayMs?: number }): Promise<void> {
    notImplemented('autoScroll');
  }

  async installInterceptor(_pattern: string): Promise<void> {
    notImplemented('installInterceptor');
  }

  async getInterceptedRequests(): Promise<any[]> {
    notImplemented('getInterceptedRequests');
  }

  async waitForCapture(_timeout?: number): Promise<void> {
    notImplemented('waitForCapture');
  }

  async screenshot(options: ScreenshotOptions = {}): Promise<string> {
    let base64: string;
    if (options.fullPage) {
      const capture = await bskScreenshotFullPage(this.caller(), { sessionId: this.handle.sessionId });
      this.rememberTab(capture);
      if (!capture.capture_id) throw new Error('bsk full-page screenshot returned no capture id');
      const bytes = await readScreenshotCapture(this.caller(), {
        sessionId: this.handle.sessionId,
        captureId: capture.capture_id,
        byteSize: capture.byte_size,
      });
      // tool.screenshot_full_page is a streamed capture; IPage still promises base64.
      base64 = bytes.toString('base64');
    } else {
      const result = await bskScreenshot(this.caller(), { sessionId: this.handle.sessionId });
      this.rememberTab(result);
      // bsk wraps image data in an envelope; IPage.screenshot must return the base64 string.
      if (!result || typeof result.image_base64 !== 'string') {
        throw new Error('bsk screenshot returned no image_base64');
      }
      base64 = result.image_base64;
    }
    if (options.path) await saveBase64ToFile(base64, options.path);
    return base64;
  }

  async annotatedScreenshot(_options?: ScreenshotOptions): Promise<string> {
    notImplemented('annotatedScreenshot');
  }

  async startNetworkCapture(_pattern?: string): Promise<boolean> {
    notImplemented('startNetworkCapture');
  }

  async readNetworkCapture(): Promise<unknown[]> {
    notImplemented('readNetworkCapture');
  }

  async setFileInput(_files: string[], _selector?: string): Promise<void> {
    // The remote bsk extension cannot stage daemon-local file paths.
    notImplemented('setFileInput');
  }

  async insertText(_text: string): Promise<void> {
    notImplemented('insertText');
  }

  async closeWindow(): Promise<void> {
    notImplemented('closeWindow');
  }

  async getCurrentUrl(): Promise<string | null> {
    const value = await this.evaluate<unknown>('location.href');
    return typeof value === 'string' && value.length > 0 ? value : null;
  }

  getActivePage(): string | undefined {
    notImplemented('getActivePage');
  }

  setActivePage(_page?: string): void {
    notImplemented('setActivePage');
  }

  async cdp(_method: string, _params?: Record<string, unknown>): Promise<unknown> {
    notImplemented('cdp');
  }

  async handleJavaScriptDialog(_accept: boolean, _promptText?: string): Promise<void> {
    notImplemented('handleJavaScriptDialog');
  }

  async frames(): Promise<Array<{ index: number; frameId: string; url: string; name: string }>> {
    notImplemented('frames');
  }

  async evaluateInFrame(_js: string, _frameIndex: number): Promise<unknown> {
    notImplemented('evaluateInFrame');
  }

  async nativeClick(_x: number, _y: number): Promise<void> {
    notImplemented('nativeClick');
  }

  async nativeType(_text: string): Promise<void> {
    notImplemented('nativeType');
  }

  async nativeKeyPress(_key: string, _modifiers?: string[]): Promise<void> {
    notImplemented('nativeKeyPress');
  }
}
