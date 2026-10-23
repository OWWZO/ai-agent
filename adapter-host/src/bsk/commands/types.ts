/**
 * Command-layer argument types.
 *
 * Each interface mirrors the matching `#[derive(Args)]` struct in
 * `BrowserSkill/crates/bsk-cli/src/cli/*.rs`, translated to camelCase and
 * without presentation-only flags (output paths, `--json`, `--out`).
 *
 * Commands are pure functions: they never print, never exit, and never touch
 * the filesystem. A CLI wrapper is responsible for all of that.
 */

import type { BskCallOptions } from '../client.js';
import type { Method } from '../methods.js';
import type {
  KeyModifier,
  MouseButton,
  ScreenshotScope,
  TabScope,
  UploadMode,
  WaitUntil,
} from '../protocol.js';

/**
 * Minimal client surface the command layer depends on. `BskClient` satisfies
 * it; tests can pass a recording stub without opening a socket.
 */
export interface BskCaller {
  call<T = unknown>(method: Method, params?: unknown, options?: BskCallOptions): Promise<T>;
  cancel(rpcId: string): Promise<unknown>;
}

/** Fields shared by every session-scoped command. */
export interface SessionScopedArgs {
  sessionId: string;
  tabId?: number;
}

/** Element target selector shared by click/fill/press/... */
export interface TargetArgs {
  /** Positional `<ref-or-selector>`. */
  target?: string;
  /** Force-treat as a snapshot ref. */
  ref?: string;
  /** Force-treat as a CSS selector. */
  selector?: string;
}

export interface TimeoutArgs {
  timeoutMs?: number;
}

export interface InteractionTargetArgs extends SessionScopedArgs, TargetArgs, TimeoutArgs {}

// ── interaction.ts ──────────────────────────────────────────────────────────

export interface ClickArgs extends InteractionTargetArgs {
  /** Bind a Canvas point click to a screenshot capture. */
  captureId?: string;
  /** X in the original screenshot PNG coordinate space. */
  imageX?: number;
  /** Y in the original screenshot PNG coordinate space. */
  imageY?: number;
  button?: MouseButton;
  /** Consecutive presses; double-click = 2. */
  clickCount?: number;
  /** `"alt,ctrl"` or an already-parsed array. */
  modifiers?: string | KeyModifier[];
}

export interface HoverArgs extends InteractionTargetArgs {
  modifiers?: string | KeyModifier[];
  /** Settle delay after the pointer move (default 200ms). */
  settleMs?: number;
}

export interface FocusArgs extends InteractionTargetArgs {}

export interface BlurArgs extends InteractionTargetArgs {}

export interface FillArgs extends InteractionTargetArgs {
  value: string;
  /** Skip the default "wipe the field first" pass. */
  noClear?: boolean;
}

export interface PressArgs extends SessionScopedArgs, TimeoutArgs {
  key: string;
  modifiers?: string | KeyModifier[];
  /** Optional target to focus before dispatching the key. */
  ref?: string;
  selector?: string;
  holdMs?: number;
}

export interface SelectArgs extends InteractionTargetArgs {
  values: string[];
}

// ── navigate.ts ─────────────────────────────────────────────────────────────

export interface NavigateArgs extends SessionScopedArgs, TimeoutArgs {
  url: string;
  waitUntil?: WaitUntil;
}

export interface NavigateHistoryArgs extends SessionScopedArgs, TimeoutArgs {
  waitUntil?: WaitUntil;
}

export interface ReloadArgs extends NavigateHistoryArgs {
  hard?: boolean;
}

// ── observe.ts ──────────────────────────────────────────────────────────────

export interface SnapshotArgs extends SessionScopedArgs {
  maxDepth?: number;
  maxTokens?: number;
}

export interface ObserveArgs extends SessionScopedArgs {
  /** Continue a retained observation; conflicts with capture options. */
  cursor?: string;
  maxDepth?: number;
  maxTokens?: number;
  debugSurfaces?: boolean;
  probeHover?: boolean;
}

export interface GetHtmlArgs extends SessionScopedArgs {
  ref?: string;
  maxBytes?: number;
}

// ── evaluate.ts ─────────────────────────────────────────────────────────────

export interface EvaluateArgs extends SessionScopedArgs, TimeoutArgs {
  expression: string;
  /** Frame id from `bsk frames`; omitted means the main frame. */
  frameId?: string;
  awaitPromise?: boolean;
  returnByValue?: boolean;
}

// ── screenshot.ts ───────────────────────────────────────────────────────────

export interface ScreenshotArgs extends SessionScopedArgs {
  ref?: string;
  /** Capture at this CSS-pixel size; both must be given together. */
  width?: number;
  height?: number;
}

export interface ScreenshotFullPageArgs extends SessionScopedArgs, TimeoutArgs {
  scope?: ScreenshotScope;
}

export interface ScreenshotReadArgs {
  sessionId: string;
  captureId: string;
  offset: number;
}

export interface ScreenshotReleaseArgs {
  sessionId: string;
  captureId: string;
}

// ── scroll.ts ───────────────────────────────────────────────────────────────

export interface ScrollToArgs extends InteractionTargetArgs {}

export interface WheelArgs extends SessionScopedArgs, TimeoutArgs {
  target?: string;
  ref?: string;
  selector?: string;
  deltaX?: number;
  deltaY?: number;
  modifiers?: string | KeyModifier[];
}

// ── waits.ts ────────────────────────────────────────────────────────────────

export interface WaitForNavigationArgs extends SessionScopedArgs, TimeoutArgs {
  waitUntil?: WaitUntil;
}

export interface WaitMsArgs {
  durationMs: number;
}

// ── files.ts ────────────────────────────────────────────────────────────────

export interface UploadFileContent {
  name: string;
  /** Raw bytes; the library base64-encodes them per chunk. */
  bytes: Uint8Array;
}

export interface UploadArgs extends SessionScopedArgs, TargetArgs, TimeoutArgs {
  files: UploadFileContent[];
  mode?: UploadMode;
}

export interface DownloadArgs extends InteractionTargetArgs {
  /** Overwrite an existing destination (checked by the CLI wrapper). */
  overwrite?: boolean;
}

export interface TransferIdArgs {
  transferId: string;
}

// ── network.ts ──────────────────────────────────────────────────────────────

export interface NetworkArgs extends SessionScopedArgs {
  since?: number;
  limit?: number;
  maxTextChars?: number;
}

export interface ConsoleArgs extends NetworkArgs {
  includeStack?: boolean;
}

// ── primitives.ts (extension-only: cookies / frames / cdp) ──────────────────

export interface CookiesArgs extends SessionScopedArgs {
  /** Cookie domain filter. */
  domain?: string;
  /** Cookie scope by URL. Takes precedence over `domain`. */
  url?: string;
}

export interface FramesArgs extends SessionScopedArgs {}

export interface CdpArgs extends SessionScopedArgs {
  /** CDP method name. Must be on the extension allowlist. */
  method: string;
  /** CDP params as a JSON object. */
  params?: Record<string, unknown>;
}

// ── session.ts ──────────────────────────────────────────────────────────────

export interface SessionStartArgs {
  sessionId: string;
  browserInstanceId?: string;
  width?: number;
  height?: number;
  focused?: boolean;
}

export interface SessionStopArgs {
  sessionId: string;
}

export interface SessionStopAllArgs {}

export interface SessionRequestArgs {
  requestId: string;
  action?: 'status' | 'prepare' | 'claim' | 'cancel';
}

// ── tab.ts ──────────────────────────────────────────────────────────────────

export interface TabListArgs {
  sessionId: string;
  scope?: TabScope;
}

export interface TabCreateArgs {
  sessionId: string;
  url?: string;
  /** Defaults to true on the extension; pass false for a background tab. */
  active?: boolean;
  index?: number;
}

export interface TabIdArgs {
  sessionId: string;
  tabId: number;
}

export interface TabBorrowArgs extends TabIdArgs {
  confirmationTimeoutMs?: number;
  confirm?: boolean;
}

// ── browser.ts ──────────────────────────────────────────────────────────────

export interface BrowserListArgs {
  waitForBrowserMs?: number;
}

export interface SystemStatusArgs {
  waitForBrowserMs?: number;
}

export interface HandshakeArgs {
  client: string;
  version: string;
  protocolVersion: string;
  instanceId: string;
  browser: { name: string; version: string };
  label: string;
  minCompatiblePeer?: string;
  minCompatibleProtocol?: string;
}

export interface WindowResizeArgs {
  sessionId: string;
  width: number;
  height: number;
}

export interface EmulateArgs extends SessionScopedArgs {
  /** Built-in device preset name. */
  device?: string;
  width?: number;
  height?: number;
  dpr?: number;
  mobile?: boolean;
  noMobile?: boolean;
  ua?: string;
  acceptLanguage?: string;
  touch?: boolean;
  noTouch?: boolean;
  maxTouchPoints?: number;
  /** Clear every override; mutually exclusive with everything else. */
  off?: boolean;
}

// ── human.ts ────────────────────────────────────────────────────────────────

export interface RequestHelpArgs extends SessionScopedArgs, TimeoutArgs {
  prompt: string;
  title?: string;
  /** `@e3` / `e3` / CSS selector; classified with `looksLikeRef`. */
  targets?: string[];
  completionCriteria?: string | Record<string, unknown>;
}
