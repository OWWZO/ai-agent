/**
 * Hand-written bsk protocol payload types.
 *
 * Source of truth: `BrowserSkill/crates/bsk-protocol/src/tools/*.rs` plus
 * the pre-generated JSON Schemas in `crates/bsk-protocol/schema/`.
 * Only fields the adapter actually sends or reads are modelled; optional
 * fields mirror `#[serde(default, skip_serializing_if = "Option::is_none")]`
 * so `undefined` means "omit from the wire".
 */

// ── Shared enums ─────────────────────────────────────────────────────────────

export type KeyModifier = 'alt' | 'ctrl' | 'meta' | 'shift';
export type MouseButton = 'left' | 'middle' | 'right';
export type WaitUntil = 'load' | 'domcontentloaded' | 'networkidle' | 'commit';
export type TabScope = 'user' | 'agent' | 'all';
export type ScreenshotScope = 'follow' | 'current';
export type UploadMode = 'input' | 'drop';

export type JavaScriptDialogType = 'alert' | 'confirm' | 'prompt' | 'beforeunload';
export type JavaScriptDialogHandledAction = 'accepted' | 'dismissed';

export interface JavaScriptDialogInfo {
  tab_id: number;
  type: JavaScriptDialogType;
  message: string;
  url?: string;
  default_prompt?: string;
  has_browser_handler?: boolean;
  handled: JavaScriptDialogHandledAction;
  sequence: number;
}

/** Every element-targeted tool carries an optional `ref` XOR `selector`. */
export interface TargetParams {
  ref?: string;
  selector?: string;
  tab_id?: number;
  timeout_ms?: number;
}

// ── System / handshake ───────────────────────────────────────────────────────

export interface BrowserPeerInfo {
  name: string;
  version: string;
}

export interface HandshakeParams {
  client: string;
  version: string;
  protocol_version: string;
  instance_id: string;
  browser: BrowserPeerInfo;
  label: string;
  min_compatible_peer?: string;
  min_compatible_protocol?: string;
}

export interface HandshakeResult {
  server: string;
  version: string;
  protocol_version: string;
  min_compatible_peer?: string;
  min_compatible_protocol?: string;
}

export interface PingParams {}

export interface PingResult {
  pong: boolean;
}

export interface BrowserStatusEntry {
  instance_id: string;
  browser_name: string;
  browser_version: string;
  extension_version: string;
  label: string;
  session_count: number;
  connected_at_ms: number;
  version_skew: boolean;
  extension_protocol_version: string;
  unresponsive: boolean;
}

export interface SessionStatusEntry {
  session_id: string;
  browser_instance_id: string;
  agent_window_id?: number;
  interaction?: InteractionPolicy;
  created_at_ms: number;
}

export interface VersionSkewEntry {
  instance_id: string;
  browser_name: string;
  label: string;
  server_version: string;
  client_version: string;
  server_protocol_version: string;
  client_protocol_version: string;
}

export interface StatusResult {
  daemon_version: string;
  protocol_version: string;
  pid: number;
  uptime_secs: number;
  ws_port: number;
  sock_path: string;
  browsers: BrowserStatusEntry[];
  sessions: SessionStatusEntry[];
  version_skew_browsers: VersionSkewEntry[];
}

export interface BrowserListParams {
  wait_for_browser_ms?: number;
}

export interface BrowserListResult {
  browsers: BrowserStatusEntry[];
}

export interface SessionListResult {
  sessions: SessionStatusEntry[];
}

// ── Session lifecycle ────────────────────────────────────────────────────────

export type BorrowConfirmationPolicy = 'always' | 'never';
export type RequestHelpPolicy = 'enabled' | 'disabled';

export interface InteractionPolicy {
  borrow_confirmation: BorrowConfirmationPolicy;
  request_help: RequestHelpPolicy;
}

/** Wire method is `tool.session_start` (NOT `session.start`). */
export interface ToolSessionStartParams {
  session_id: string;
  browser_instance_id?: string;
  width?: number;
  height?: number;
  focused?: boolean;
}

export interface ToolSessionStartResult {
  interaction?: InteractionPolicy;
  agent_window_id?: number;
}

export interface ToolSessionStopParams {
  session_id: string;
}

export interface ReturnFailure {
  tab_id: number;
  code: string;
  message: string;
}

export interface ToolSessionStopResult {
  returned_tab_ids?: number[];
  return_failures?: ReturnFailure[];
}

/** Legacy `session.start` CLI params forwarded by the daemon. */
export interface SessionStartParams {
  request_id?: string;
  task_name?: string;
  browser_instance_id?: string;
  width?: number;
  height?: number;
  focused?: boolean;
}

export interface SessionStopParams {
  session_id?: string;
  all?: boolean;
}

export interface StopFailure {
  session_id: string;
  code: string;
  message: string;
}

export interface SessionStopResult {
  stopped: string[];
  failed?: StopFailure[];
  returned_tab_ids?: number[];
  return_failures?: ReturnFailure[];
}

export interface SessionRequestParams {
  request_id: string;
  action?: 'status' | 'prepare' | 'claim' | 'cancel';
}

// ── Tabs ─────────────────────────────────────────────────────────────────────

export interface TabInfo {
  tab_id: number;
  title?: string;
  url?: string;
  window_id?: number;
  active?: boolean;
  scope?: TabScope;
}

export interface TabListParams {
  session_id: string;
  scope?: TabScope;
}

export interface TabListResult {
  tabs: TabInfo[];
}

export interface TabCreateParams {
  session_id: string;
  url?: string;
  active?: boolean;
  index?: number;
}

export interface TabCreateResult {
  tab_id: number;
  window_id: number;
  url: string;
}

export interface TabCloseParams {
  session_id: string;
  tab_id: number;
}

export interface TabCloseResult {
  tab_id: number;
}

export interface TabSelectParams {
  session_id: string;
  tab_id: number;
}

export interface TabSelectResult {
  tab_id: number;
  window_id: number;
}

export interface TabBorrowParams {
  session_id: string;
  tab_id: number;
  confirmation_timeout_ms?: number;
  confirm?: boolean;
}

export interface TabBorrowResult {
  tab_id: number;
  original_window_id: number;
  original_index: number;
  agent_window_id: number;
}

export interface TabReturnParams {
  session_id: string;
  tab_id: number;
}

export interface TabReturnResult {
  tab_id: number;
  returned_to_window_id: number;
  returned_to_index: number;
  fallback?: boolean;
}

// ── Window / emulate ────────────────────────────────────────────────────────

export interface WindowResizeParams {
  session_id: string;
  width: number;
  height: number;
}

export interface WindowResizeResult {
  window_id: number;
  width: number;
  height: number;
}

export interface UserAgentBrandVersion {
  brand: string;
  version: string;
}

export interface UserAgentMetadata {
  brands?: UserAgentBrandVersion[];
  full_version?: string;
  platform?: string;
  platform_version?: string;
  architecture?: string;
  model?: string;
  mobile?: boolean;
}

export interface EmulateOverrides {
  width?: number;
  height?: number;
  device_scale_factor?: number;
  mobile?: boolean;
  user_agent?: string;
  accept_language?: string;
  user_agent_metadata?: UserAgentMetadata;
  touch?: boolean;
  max_touch_points?: number;
}

export interface EmulateParams {
  session_id: string;
  tab_id?: number;
  off?: boolean;
  overrides?: EmulateOverrides;
}

export interface EmulateResult {
  tab_id: number;
  cleared: boolean;
  applied?: EmulateOverrides;
  note?: string;
}

// ── Navigation ──────────────────────────────────────────────────────────────

export interface NavigateParams {
  session_id: string;
  url: string;
  tab_id?: number;
  wait_until?: WaitUntil;
  timeout_ms?: number;
}

export interface NavigateResult {
  tab_id: number;
  url: string;
  final_url?: string;
  reached: string;
  error_text?: string;
  dialogs?: JavaScriptDialogInfo[];
}

export interface NavigateHistoryParams {
  session_id: string;
  tab_id?: number;
  wait_until?: WaitUntil;
  timeout_ms?: number;
}

export interface NavigateBackResult {
  tab_id: number;
  previous_url?: string;
  final_url?: string;
  reached: string;
  error_text?: string;
  dialogs?: JavaScriptDialogInfo[];
}

export type NavigateForwardResult = NavigateBackResult;

export interface ReloadParams extends NavigateHistoryParams {
  hard?: boolean;
}

export type ReloadResult = NavigateBackResult;

// ── Interaction ─────────────────────────────────────────────────────────────

export interface ClickParams {
  session_id: string;
  capture_id?: string;
  image_x?: number;
  image_y?: number;
  ref?: string;
  selector?: string;
  tab_id?: number;
  button?: MouseButton;
  click_count?: number;
  modifiers?: KeyModifier[];
  timeout_ms?: number;
}

export interface ClickResult {
  tab_id: number;
  used_ref?: string;
  used_selector?: string;
  x: number;
  y: number;
  dialogs?: JavaScriptDialogInfo[];
}

export interface HoverParams {
  session_id: string;
  ref?: string;
  selector?: string;
  tab_id?: number;
  modifiers?: KeyModifier[];
  settle_ms?: number;
  timeout_ms?: number;
}

export interface HoverResult {
  tab_id: number;
  used_ref?: string;
  used_selector?: string;
  x: number;
  y: number;
  dialogs?: JavaScriptDialogInfo[];
}

export interface FocusParams {
  session_id: string;
  ref?: string;
  selector?: string;
  tab_id?: number;
  timeout_ms?: number;
}

export interface FocusResult {
  tab_id: number;
  used_ref?: string;
  used_selector?: string;
  focused: boolean;
  dialogs?: JavaScriptDialogInfo[];
}

export interface BlurResult {
  tab_id: number;
  used_ref?: string;
  used_selector?: string;
  was_focused: boolean;
  focused: boolean;
  dialogs?: JavaScriptDialogInfo[];
}

export interface FillParams {
  session_id: string;
  value: string;
  ref?: string;
  selector?: string;
  tab_id?: number;
  clear_before?: boolean;
  timeout_ms?: number;
}

export interface FillResult {
  tab_id: number;
  used_ref?: string;
  used_selector?: string;
  value_length: number;
  dialogs?: JavaScriptDialogInfo[];
}

export interface PressParams {
  session_id: string;
  key: string;
  modifiers?: KeyModifier[];
  ref?: string;
  selector?: string;
  tab_id?: number;
  hold_ms?: number;
  timeout_ms?: number;
}

export interface PressResult {
  tab_id: number;
  key: string;
  code: string;
  modifiers?: KeyModifier[];
  dialogs?: JavaScriptDialogInfo[];
}

export interface SelectParams {
  session_id: string;
  values: string[];
  ref?: string;
  selector?: string;
  tab_id?: number;
  timeout_ms?: number;
}

export interface SelectResult {
  tab_id: number;
  used_ref?: string;
  used_selector?: string;
  multiple: boolean;
  selected_values: string[];
  selected_labels: string[];
  dialogs?: JavaScriptDialogInfo[];
}

// ── Scroll / wheel ──────────────────────────────────────────────────────────

export interface ScrollToParams {
  session_id: string;
  ref?: string;
  selector?: string;
  tab_id?: number;
  timeout_ms?: number;
}

export interface ScrollToResult {
  tab_id: number;
  used_ref?: string;
  used_selector?: string;
  x: number;
  y: number;
  width: number;
  height: number;
  dialogs?: JavaScriptDialogInfo[];
}

export interface WheelParams {
  session_id: string;
  ref?: string;
  selector?: string;
  tab_id?: number;
  delta_x?: number;
  delta_y?: number;
  modifiers?: KeyModifier[];
  timeout_ms?: number;
}

export interface WheelResult {
  tab_id: number;
  used_ref?: string;
  used_selector?: string;
  x: number;
  y: number;
  delta_x: number;
  delta_y: number;
  dialogs?: JavaScriptDialogInfo[];
}

// ── Observation ─────────────────────────────────────────────────────────────

export interface SnapshotParams {
  session_id: string;
  tab_id?: number;
  max_depth?: number;
  max_tokens?: number;
}

export interface SnapshotResult {
  text: string;
  ref_count: number;
  tab_id: number;
  truncated: boolean;
  dialogs?: JavaScriptDialogInfo[];
}

export interface SurfaceProbePoint {
  x: number;
  y: number;
}

export interface SurfaceProbeDebug {
  trigger_backend_node_id: number;
  trigger_point?: SurfaceProbePoint;
  trigger_action: string;
  sub_items: string[];
  confidence?: string;
}

export interface ObserveDebug {
  surface_probes?: SurfaceProbeDebug[];
}

export interface HoverProbeReport {
  performed: boolean;
  revealed_content: boolean;
}

export interface ObserveParams {
  cursor?: string;
  session_id: string;
  tab_id?: number;
  max_depth?: number;
  max_tokens?: number;
  debug_surfaces?: boolean;
  probe_hover?: boolean;
}

export interface ObserveResult {
  next_cursor?: string;
  text: string;
  ref_count: number;
  tab_id: number;
  truncated: boolean;
  dialogs?: JavaScriptDialogInfo[];
  hover_probe?: HoverProbeReport;
  debug?: ObserveDebug;
}

export interface GetHtmlParams {
  session_id: string;
  tab_id?: number;
  ref?: string;
  max_bytes?: number;
}

export interface GetHtmlResult {
  html: string;
  truncated: boolean;
  byte_size: number;
  tab_id: number;
  dialogs?: JavaScriptDialogInfo[];
}

// ── Evaluate ────────────────────────────────────────────────────────────────

export interface EvaluateParams {
  session_id: string;
  expression: string;
  tab_id?: number;
  /** Frame from `bsk frames`; omitted means the main frame. */
  frame_id?: string;
  await_promise?: boolean;
  return_by_value?: boolean;
  timeout_ms?: number;
}

export interface EvaluateError {
  text: string;
  line?: number;
  column?: number;
}

/** `ok:false` is in-band data, not an RPC error. */
export interface EvaluateResult {
  ok: boolean;
  tab_id: number;
  /** Echoed when a frame was requested. */
  frame_id?: string;
  value?: unknown;
  error?: EvaluateError;
  dialogs?: JavaScriptDialogInfo[];
}

// ── Screenshot ──────────────────────────────────────────────────────────────

export interface ScreenshotParams {
  session_id: string;
  tab_id?: number;
  ref?: string;
  /** Capture at this CSS-pixel size instead of the tab's current viewport. */
  width?: number;
  height?: number;
}

export interface ScreenshotResult {
  capture_id?: string;
  capture_unavailable?: string;
  image_base64: string;
  width: number;
  height: number;
  format: string;
  tab_id: number;
  dialogs?: JavaScriptDialogInfo[];
}

export interface ScreenshotFullPageParams {
  session_id: string;
  tab_id?: number;
  scope?: ScreenshotScope;
  timeout_ms?: number;
}

export interface ScreenshotFullPageResult {
  scope?: ScreenshotScope;
  capture_id: string;
  width: number;
  height: number;
  format: string;
  tab_id: number;
  byte_size: number;
  dialogs?: JavaScriptDialogInfo[];
}

export interface ScreenshotReadParams {
  session_id: string;
  capture_id: string;
  offset: number;
}

export interface ScreenshotReadResult {
  data_base64: string;
  next_offset: number;
  eof: boolean;
}

export interface ScreenshotReleaseParams {
  session_id: string;
  capture_id: string;
}

export interface ScreenshotReleaseResult {
  released: boolean;
}

// ── Waits ───────────────────────────────────────────────────────────────────

export interface WaitForNavigationParams {
  session_id: string;
  tab_id?: number;
  wait_until?: WaitUntil;
  timeout_ms?: number;
}

export type WaitForNavigationReached = WaitUntil | 'timeout';

export interface WaitForNavigationResult {
  tab_id: number;
  reached: WaitForNavigationReached;
  error_text?: string;
  dialogs?: JavaScriptDialogInfo[];
}

export interface WaitMsParams {
  duration_ms: number;
}

export interface WaitMsResult {
  waited_ms: number;
}

// ── Console / network ───────────────────────────────────────────────────────

export type ConsoleEntryKind = 'console' | 'exception' | 'log';

export interface ConsoleStackFrame {
  function_name?: string;
  url?: string;
  line?: number;
  column?: number;
}

export interface ConsoleEntry {
  sequence: number;
  kind: ConsoleEntryKind;
  level: string;
  text: string;
  url?: string;
  line?: number;
  column?: number;
  timestamp?: number;
  stack_trace?: ConsoleStackFrame[];
  truncated: boolean;
}

export interface ConsoleParams {
  session_id: string;
  tab_id?: number;
  since?: number;
  limit?: number;
  max_text_chars?: number;
  include_stack?: boolean;
}

export interface ConsoleResult {
  tab_id: number;
  entries: ConsoleEntry[];
  next_since: number;
  truncated: boolean;
}

export type NetworkEntryKind = 'response' | 'failure';

export interface NetworkEntry {
  sequence: number;
  kind: NetworkEntryKind;
  method?: string;
  url?: string;
  status?: number;
  status_text?: string;
  mime_type?: string;
  resource_type?: string;
  error_text?: string;
  timestamp?: number;
  truncated: boolean;
}

export interface NetworkParams {
  session_id: string;
  tab_id?: number;
  since?: number;
  limit?: number;
  max_text_chars?: number;
}

export interface NetworkResult {
  tab_id: number;
  entries: NetworkEntry[];
  next_since: number;
  truncated: boolean;
}

// ── Browser primitives (cookies / frames / cdp) ─────────────────────────────
//
// Mirrors the OpenCLI extension's `cookies` / `frames` / `cdp` actions. These
// are extension-level `tool.*` methods: the Java gateway forwards unknown
// `tool.*` frames verbatim, so no gateway change is needed.

export interface CookiesParams {
  session_id: string;
  tab_id?: number;
  /** Cookie domain filter. At least one of `domain` / `url` is required. */
  domain?: string;
  /** Cookie scope by URL. Takes precedence over `domain` when both are set. */
  url?: string;
}

export interface CookieEntry {
  name: string;
  value: string;
  domain: string;
  path: string;
  secure: boolean;
  http_only: boolean;
  /** Seconds since the Unix epoch. Absent for session cookies. */
  expiration_date?: number;
}

export interface CookiesResult {
  tab_id: number;
  /** The scope actually used: requested URL, else domain, else tab origin. */
  scope: string;
  cookies: CookieEntry[];
}

export interface FramesParams {
  session_id: string;
  tab_id?: number;
}

export interface FrameEntry {
  frame_id: string;
  parent_frame_id?: string;
  url?: string;
  /** True when the frame runs in its own CDP target (cross-origin / OOPIF). */
  out_of_process: boolean;
}

export interface FramesResult {
  tab_id: number;
  root_frame_id: string;
  frames: FrameEntry[];
  /** Subtrees the browser refused to describe, kept visible as gaps. */
  unavailable_frames: FrameEntry[];
}

export interface CdpParams {
  session_id: string;
  tab_id?: number;
  /** CDP method name. Must be on the extension's allowlist. */
  method: string;
  /** CDP method params. Opaque to the protocol; forwarded verbatim. */
  params?: Record<string, unknown>;
}

export interface CdpResult {
  tab_id: number;
  method: string;
  /** The raw CDP reply. Its shape is defined by the method that was called. */
  result: unknown;
}

// ── Files (upload / download / transfer) ────────────────────────────────────

export interface UploadFile {
  transfer_id: string;
  name: string;
  staged_path?: string;
}

export interface UploadParams {
  session_id: string;
  ref?: string;
  selector?: string;
  tab_id?: number;
  files: UploadFile[];
  mode?: UploadMode;
  timeout_ms?: number;
}

export interface UploadResult {
  tab_id: number;
  used_ref?: string;
  used_selector?: string;
  file_names: string[];
}

export interface DownloadParams {
  session_id: string;
  ref?: string;
  selector?: string;
  tab_id?: number;
  timeout_ms?: number;
  browser_relative_dir?: string;
  max_byte_size?: number;
}

export interface DownloadResult {
  tab_id: number;
  used_ref?: string;
  used_selector?: string;
  suggested_filename: string;
  byte_size: number;
  mime?: string;
  danger?: string;
  browser_path?: string;
  transfer_id?: string;
}

export interface TransferBeginParams {
  session_id: string;
  name: string;
  byte_size: number;
}

export interface TransferBeginResult {
  transfer_id: string;
  chunk_size: number;
}

export interface TransferChunkParams {
  transfer_id: string;
  offset: number;
  data_base64: string;
}

export interface TransferChunkResult {
  next_offset: number;
  eof: boolean;
  data_base64?: string;
}

export interface TransferIdParams {
  transfer_id: string;
}

export interface TransferReadyResult {
  transfer_id: string;
  byte_size: number;
}

export interface TransferReleaseResult {
  released: boolean;
}

// ── Human in the loop ───────────────────────────────────────────────────────

export interface HelpTarget {
  ref?: string;
  selector?: string;
}

export interface HelpCompletionCondition {
  url_contains?: string;
  url_matches?: string;
  selector_exists?: string;
  selector_missing?: string;
  text_exists?: string;
  text_missing?: string;
}

export interface HelpCompletionCriteria {
  any?: HelpCompletionCondition[];
  all?: HelpCompletionCondition[];
  stable_for_ms?: number;
}

export type HelpOutcome =
  | 'continued'
  | 'cancelled'
  | 'timed_out'
  | 'completed'
  | 'navigated'
  | 'disabled';

export type HelpCompletedBy = 'system';

export interface ResolvedTarget {
  matched: boolean;
  ref?: string;
  selector?: string;
}

export interface RequestHelpParams {
  session_id: string;
  tab_id?: number;
  prompt: string;
  title?: string;
  targets?: HelpTarget[];
  completion_criteria?: HelpCompletionCriteria;
  timeout_ms?: number;
}

export interface RequestHelpResult {
  outcome: HelpOutcome;
  completed_by?: HelpCompletedBy;
  note?: string;
  tab_id: number;
  resolved_targets?: ResolvedTarget[];
}

// ── Cancel ──────────────────────────────────────────────────────────────────

export interface CancelParams {
  rpc_id: string;
}

export interface CancelResult {
  cancelled: boolean;
}

// ── Frames ──────────────────────────────────────────────────────────────────

export type RpcId = string;

/** `params` is omitted when undefined (matches serde `skip_serializing_if`). */
export interface RequestFrame {
  id: RpcId;
  method: string;
  params?: unknown;
}

/** Flat response frame: exactly one of `result` / `error` is present. */
export interface ResponseFrame {
  id: RpcId;
  result?: unknown;
  error?: RpcErrorLike;
}

export interface RpcErrorLike {
  code: string;
  message: string;
  data?: unknown;
}

export type EventKind =
  | 'audit.context'
  | 'system.heartbeat'
  | 'session.activity'
  | 'session.window_closed'
  | 'session.user_interrupt'
  | 'session.interaction_changed'
  | 'browser.disconnected'
  | 'browser.connected';

export interface EventFrame {
  event: EventKind;
  payload?: unknown;
}
