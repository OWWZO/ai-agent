/**
 * bsk protocol method names.
 *
 * Verbatim transcription of every `#[serde(rename = "...")]` value in
 * `BrowserSkill/crates/bsk-protocol/src/method.rs`. The Rust enum has 59
 * variants (the spec's "47" predates the record/transfer additions);
 * `methods.test.ts` diffs this table against the Rust source so drift is
 * loud.
 */

export const METHODS = {
  auditRequest: 'audit.request',
  systemHandshake: 'system.handshake',
  systemPing: 'system.ping',
  systemStatus: 'system.status',

  sessionStart: 'session.start',
  sessionStartTracked: 'session.start_tracked',
  sessionRequest: 'session.request',
  sessionStop: 'session.stop',
  sessionStopAll: 'session.stop_all',
  sessionList: 'session.list',

  browserList: 'browser.list',

  toolSessionStart: 'tool.session_start',
  toolSessionStop: 'tool.session_stop',
  toolWindowResize: 'tool.window_resize',
  toolEmulate: 'tool.emulate',
  toolTabList: 'tool.tab_list',
  toolTabCreate: 'tool.tab_create',
  toolTabClose: 'tool.tab_close',
  toolTabBorrow: 'tool.tab_borrow',
  toolTabReturn: 'tool.tab_return',
  toolTabSelect: 'tool.tab_select',
  toolNavigate: 'tool.navigate',
  toolNavigateBack: 'tool.navigate_back',
  toolNavigateForward: 'tool.navigate_forward',
  toolReload: 'tool.reload',
  toolClick: 'tool.click',
  toolHover: 'tool.hover',
  toolWheel: 'tool.wheel',
  toolScrollTo: 'tool.scroll_to',
  toolFocus: 'tool.focus',
  toolBlur: 'tool.blur',
  toolFill: 'tool.fill',
  toolPress: 'tool.press',
  toolSelect: 'tool.select',
  toolUpload: 'tool.upload',
  toolDownload: 'tool.download',
  toolSnapshot: 'tool.snapshot',
  toolObserve: 'tool.observe',
  toolGetHtml: 'tool.get_html',
  toolScreenshot: 'tool.screenshot',
  toolScreenshotFullPage: 'tool.screenshot_full_page',
  toolScreenshotRead: 'tool.screenshot_read',
  toolScreenshotRelease: 'tool.screenshot_release',
  toolConsole: 'tool.console',
  toolDebug: 'tool.debug',
  toolNetwork: 'tool.network',
  toolEvaluate: 'tool.evaluate',
  toolWaitForNavigation: 'tool.wait_for_navigation',
  toolWaitMs: 'tool.wait_ms',
  toolRequestHelp: 'tool.request_help',
  toolRecordStart: 'tool.record_start',
  toolRecordStop: 'tool.record_stop',
  toolRecordAwait: 'tool.record_await',

  transferBegin: 'transfer.begin',
  transferChunk: 'transfer.chunk',
  transferFinish: 'transfer.finish',
  transferRead: 'transfer.read',
  transferRelease: 'transfer.release',

  cancel: 'cancel',
} as const;

/**
 * Methods this adapter adds on top of the upstream Rust protocol.
 *
 * They are deliberately kept out of {@link METHODS}: `methods.test.ts` diffs
 * that table against `method.rs` verbatim, and these three have no Rust
 * counterpart. The extension implements them; the Java gateway forwards any
 * unknown `tool.*` frame as-is, so no gateway change is involved either.
 *
 * * `tool.cookies` / `tool.frames` / `tool.cdp` mirror the OpenCLI
 *   extension's `cookies` / `frames` / `cdp` actions.
 */
export const EXTENSION_METHODS = {
  toolCookies: 'tool.cookies',
  toolFrames: 'tool.frames',
  toolCdp: 'tool.cdp',
} as const;

export type UpstreamMethod = (typeof METHODS)[keyof typeof METHODS];
export type ExtensionMethod = (typeof EXTENSION_METHODS)[keyof typeof EXTENSION_METHODS];

/**
 * Any method this adapter may send. Kept as the wide type so `client.call`
 * accepts extension-only methods without a cast at every call site.
 */
export type Method = UpstreamMethod | ExtensionMethod;

/** Declaration order mirrors the Rust enum, which keeps the diff test simple. */
export const ALL_METHODS: readonly UpstreamMethod[] = Object.values(METHODS);

/** Upstream methods plus the extension-only additions this adapter sends. */
export const ALL_KNOWN_METHODS: readonly Method[] = [
  ...ALL_METHODS,
  ...Object.values(EXTENSION_METHODS),
];

const METHOD_SET: ReadonlySet<string> = new Set<string>(ALL_KNOWN_METHODS);

export function isMethod(value: unknown): value is Method {
  return typeof value === 'string' && METHOD_SET.has(value);
}
