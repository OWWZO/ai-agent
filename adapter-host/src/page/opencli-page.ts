/**
 * OpenCliPage — default backend adapter.
 *
 * A deliberately thin wrapper around the existing `Page` implementation in
 * `../browser/page.js`. It adds no behavior: every method forwards straight
 * through so the `BROWSER_BACKEND=opencli` path stays byte-for-byte equivalent
 * to the pre-021 runtime.
 *
 * `src/browser/page.ts` is NOT modified by this round — this wrapper exists so
 * both backends can be addressed through the new `./ipage.js` contract.
 */

import type { BrowserCookie, BrowserEvaluateFunction } from '../types.js';
import type { Page } from '../browser/page.js';
import type { IPage } from './ipage.js';

type ClickResult = Awaited<ReturnType<IPage['click']>>;

export class OpenCliPage implements IPage {
  constructor(private readonly inner: Page) {}

  // ── Required methods ────────────────────────────────────────────────

  async goto(
    url: string,
    options?: { waitUntil?: 'load' | 'none'; settleMs?: number },
  ): Promise<void> {
    return this.inner.goto(url, options);
  }

  async evaluate<T = any>(js: string): Promise<T>;
  async evaluate<Args extends unknown[], T>(fn: BrowserEvaluateFunction<Args, T>, ...args: Args): Promise<Awaited<T>>;
  async evaluate(input: string | BrowserEvaluateFunction<unknown[], unknown>, ...args: unknown[]): Promise<any> {
    // `Page.evaluate` is overloaded; the implementation signature accepts the
    // union, so cast through it rather than picking one overload.
    const evaluate = this.inner.evaluate as unknown as (
      input: string | BrowserEvaluateFunction<unknown[], unknown>,
      ...rest: unknown[]
    ) => Promise<unknown>;
    return evaluate.call(this.inner, input, ...args);
  }

  async fetchJson(url: string, opts?: Parameters<IPage['fetchJson']>[1]): Promise<unknown> {
    return this.inner.fetchJson(url, opts);
  }

  async getCookies(opts?: { domain?: string; url?: string }): Promise<BrowserCookie[]> {
    return this.inner.getCookies(opts);
  }

  async snapshot(opts?: Parameters<IPage['snapshot']>[0]): Promise<any> {
    return this.inner.snapshot(opts);
  }

  async click(ref: string, opts?: { nth?: number; firstOnMulti?: boolean }): Promise<ClickResult> {
    return this.inner.click(ref, opts);
  }

  async typeText(
    ref: string,
    text: string,
    opts?: { nth?: number; firstOnMulti?: boolean },
  ): Promise<{ matches_n: number; match_level: 'exact' | 'stable' | 'reidentified' }> {
    return this.inner.typeText(ref, text, opts);
  }

  async fillText(
    ref: string,
    text: string,
    opts?: { nth?: number; firstOnMulti?: boolean },
  ): Promise<{
    filled: boolean;
    verified: boolean;
    expected: string;
    actual: string;
    length: number;
    matches_n: number;
    match_level: 'exact' | 'stable' | 'reidentified';
    mode?: 'input' | 'textarea' | 'contenteditable';
  }> {
    return this.inner.fillText(ref, text, opts);
  }

  async pressKey(key: string): Promise<void> {
    return this.inner.pressKey(key);
  }

  async scrollTo(ref: string, opts?: { nth?: number; firstOnMulti?: boolean }): Promise<any> {
    return this.inner.scrollTo(ref, opts);
  }

  async getFormState(): Promise<any> {
    return this.inner.getFormState();
  }

  async wait(options: Parameters<IPage['wait']>[0]): Promise<void> {
    return this.inner.wait(options);
  }

  async sleep(seconds: number): Promise<void> {
    return this.inner.sleep(seconds);
  }

  async tabs(): Promise<any> {
    return this.inner.tabs();
  }

  async selectTab(target: number | string): Promise<void> {
    return this.inner.selectTab(target);
  }

  async networkRequests(includeStatic?: boolean): Promise<any> {
    return this.inner.networkRequests(includeStatic);
  }

  async consoleMessages(level?: string): Promise<any> {
    return this.inner.consoleMessages(level);
  }

  async scroll(direction?: string, amount?: number): Promise<void> {
    return this.inner.scroll(direction, amount);
  }

  async autoScroll(options?: { times?: number; delayMs?: number }): Promise<void> {
    return this.inner.autoScroll(options);
  }

  async installInterceptor(pattern: string): Promise<void> {
    return this.inner.installInterceptor(pattern);
  }

  async getInterceptedRequests(): Promise<any[]> {
    return this.inner.getInterceptedRequests();
  }

  async waitForCapture(timeout?: number): Promise<void> {
    return this.inner.waitForCapture(timeout);
  }

  async screenshot(options?: Parameters<IPage['screenshot']>[0]): Promise<string> {
    return this.inner.screenshot(options);
  }

  // ── Optional methods ────────────────────────────────────────────────
  // Getters keep the property `undefined` when the wrapped Page does not
  // expose it, matching the optional-method contract exactly.

  get evaluateWithArgs(): IPage['evaluateWithArgs'] {
    return this.inner.evaluateWithArgs?.bind(this.inner);
  }

  get dblClick(): IPage['dblClick'] {
    return this.inner.dblClick?.bind(this.inner);
  }

  get hover(): IPage['hover'] {
    return this.inner.hover?.bind(this.inner);
  }

  get focus(): IPage['focus'] {
    return this.inner.focus?.bind(this.inner);
  }

  get setChecked(): IPage['setChecked'] {
    return this.inner.setChecked?.bind(this.inner);
  }

  get uploadFiles(): IPage['uploadFiles'] {
    return this.inner.uploadFiles?.bind(this.inner);
  }

  get drag(): IPage['drag'] {
    return this.inner.drag?.bind(this.inner);
  }

  get waitForDownload(): IPage['waitForDownload'] {
    return this.inner.waitForDownload?.bind(this.inner);
  }

  get closeTab(): IPage['closeTab'] {
    return this.inner.closeTab?.bind(this.inner);
  }

  get newTab(): IPage['newTab'] {
    return this.inner.newTab?.bind(this.inner);
  }

  get annotatedScreenshot(): IPage['annotatedScreenshot'] {
    return this.inner.annotatedScreenshot?.bind(this.inner);
  }

  get startNetworkCapture(): IPage['startNetworkCapture'] {
    return this.inner.startNetworkCapture?.bind(this.inner);
  }

  get readNetworkCapture(): IPage['readNetworkCapture'] {
    return this.inner.readNetworkCapture?.bind(this.inner);
  }

  get setFileInput(): IPage['setFileInput'] {
    return this.inner.setFileInput?.bind(this.inner);
  }

  get insertText(): IPage['insertText'] {
    return this.inner.insertText?.bind(this.inner);
  }

  get closeWindow(): IPage['closeWindow'] {
    return this.inner.closeWindow?.bind(this.inner);
  }

  get getCurrentUrl(): IPage['getCurrentUrl'] {
    return this.inner.getCurrentUrl?.bind(this.inner);
  }

  get getActivePage(): IPage['getActivePage'] {
    return this.inner.getActivePage?.bind(this.inner);
  }

  get setActivePage(): IPage['setActivePage'] {
    return this.inner.setActivePage?.bind(this.inner);
  }

  get cdp(): IPage['cdp'] {
    return this.inner.cdp?.bind(this.inner);
  }

  get handleJavaScriptDialog(): IPage['handleJavaScriptDialog'] {
    return this.inner.handleJavaScriptDialog?.bind(this.inner);
  }

  get frames(): IPage['frames'] {
    return this.inner.frames?.bind(this.inner);
  }

  get evaluateInFrame(): IPage['evaluateInFrame'] {
    return this.inner.evaluateInFrame?.bind(this.inner);
  }

  get nativeClick(): IPage['nativeClick'] {
    return this.inner.nativeClick?.bind(this.inner);
  }

  get nativeType(): IPage['nativeType'] {
    return this.inner.nativeType?.bind(this.inner);
  }

  get nativeKeyPress(): IPage['nativeKeyPress'] {
    return this.inner.nativeKeyPress?.bind(this.inner);
  }
}
