/**
 * Standalone BrowserSkill CLI for the remote relay.
 *
 * This file is intentionally only a Commander adapter. Argument validation,
 * wire-shape construction, timeouts, and protocol calls remain in
 * `src/bsk/commands/**`; the CLI owns transport setup and presentation only.
 * It never starts a local daemon or spawns a child process.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { InvalidArgumentError, Command, Option as CommanderOption } from 'commander';

import {
  BskClient,
  type BskCallOptions,
  type BskClientOptions,
} from './bsk/client.js';
import * as commandLayer from './bsk/commands/index.js';
import { validateScreenshotChunk } from './bsk/commands/screenshot.js';
import type { BskCaller } from './bsk/commands/types.js';
import { parseDurationMs, parsePositiveInt, parseTimeoutMs } from './bsk/commands/args.js';
import { BskRpcError, BskUsageError, classifyFailure } from './bsk/errors.js';
import type { MouseButton, ScreenshotScope, TabScope, WaitUntil } from './bsk/protocol.js';

/** A stream-like sink used by the CLI and by unit tests. */
export interface BskCliOutput {
  write(chunk: string): unknown;
}

/** Minimal client surface needed by the CLI runtime. */
export type BskCliClient = BskCaller & {
  close?: () => Promise<void>;
};

/** Factory seam used by tests; the production factory is `BskClient.connect`. */
export type BskCliClientFactory = (options: {
  url: string;
  browserId?: string;
}) => Promise<BskCliClient>;

type CommandLayer = Pick<
  typeof commandLayer,
  | 'navigate'
  | 'navigateBack'
  | 'navigateForward'
  | 'reload'
  | 'click'
  | 'hover'
  | 'wheel'
  | 'scrollTo'
  | 'focus'
  | 'blur'
  | 'fill'
  | 'press'
  | 'select'
  | 'upload'
  | 'download'
  | 'snapshot'
  | 'observe'
  | 'getHtml'
  | 'evaluate'
  | 'screenshot'
  | 'screenshotFullPage'
  | 'screenshotRead'
  | 'screenshotRelease'
  | 'console'
  | 'network'
  | 'cookies'
  | 'frames'
  | 'cdp'
  | 'waitForNavigation'
  | 'waitMs'
  | 'requestHelp'
  | 'sessionStart'
  | 'sessionStartDaemon'
  | 'sessionStop'
  | 'sessionStopDaemon'
  | 'sessionStopAll'
  | 'sessionList'
  | 'tabList'
  | 'tabCreate'
  | 'tabClose'
  | 'tabSelect'
  | 'tabBorrow'
  | 'tabReturn'
  | 'browserList'
  | 'emulate'
  | 'windowResize'
>;

/** Optional dependency seams. `buildBskCli()` remains useful without them. */
export interface BskCliBuildOptions {
  client?: BskCliClient;
  clientFactory?: BskCliClientFactory;
  commandLayer?: Partial<CommandLayer>;
  stdout?: BskCliOutput;
  stderr?: BskCliOutput;
}

interface RootOptions {
  relay?: string;
  browser?: string;
  session?: string;
  /** {@code --session} 是命令行显式给的（cli）还是环境变量注入的（env）。 */
  sessionSource?: 'cli' | 'env';
  tabId?: number;
  json?: boolean;
}

/**
 * How long the CLI keeps racing after SIGINT before it gives up on the peer.
 *
 * `BskClient` already owns the wire behaviour: aborting a call sends `cancel`
 * and settles it within `CANCEL_RESPONSE_GRACE` (2s). This budget is
 * deliberately shorter so the CLI can report the interruption while the
 * client's own grace timer is still the authority on the call's outcome.
 */
export const SIGINT_GRACE_MS = 1_800;

type LocalOptions = Record<string, unknown>;
type CommandHandler = (
  positional: readonly unknown[],
  local: LocalOptions,
  client: BskCaller,
  root: RootOptions,
  command: Command,
) => Promise<unknown>;

const DEFAULT_STDOUT: BskCliOutput = process.stdout;
const DEFAULT_STDERR: BskCliOutput = process.stderr;

/** 校验合法的鼠标按键（对齐 Rust 的 `CliMouseButton` value_enum）。 */
const MOUSE_BUTTONS: readonly MouseButton[] = ['left', 'middle', 'right'];

function parseInteger(value: string, label: string): number {
  const parsed = Number(value);
  if (!Number.isInteger(parsed)) {
    throw new InvalidArgumentError(`${label} must be an integer`);
  }
  return parsed;
}

function parseFiniteNumber(value: string, label: string): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    throw new InvalidArgumentError(`${label} must be a finite number`);
  }
  return parsed;
}

function parsePositiveInteger(value: string, label: string): number {
  try {
    return parsePositiveInt(value, label);
  } catch (error) {
    throw new InvalidArgumentError(error instanceof Error ? error.message : String(error));
  }
}

function parseTimeout(value: string): number {
  try {
    return parseTimeoutMs(value);
  } catch (error) {
    throw new InvalidArgumentError(error instanceof Error ? error.message : String(error));
  }
}

/** Parse a `--params` flag: a JSON object, since CDP params are always one. */
function parseJsonObject(value: string, label: string): Record<string, unknown> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(value);
  } catch (error) {
    throw new InvalidArgumentError(`${label} must be valid JSON: ${String(error)}`);
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new InvalidArgumentError(`${label} must be a JSON object`);
  }
  return parsed as Record<string, unknown>;
}

function parseDuration(value: string): number {
  try {
    return parseDurationMs(value);
  } catch (error) {
    throw new InvalidArgumentError(error instanceof Error ? error.message : String(error));
  }
}

function collect(value: string, previous: string[]): string[] {
  return [...previous, value];
}

/**
 * 生成一个会话 id。
 *
 * <p>扩展层的 {@code tool.session_start} 要求客户端自带 id（daemon 层的
 * {@code session.start} 才是服务端分配），所以这里自己生成一个。</p>
 */
function generateSessionId(): string {
  return `bsk-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function rootFor(command: Command): Command {
  let current = command;
  while (current.parent) current = current.parent;
  return current;
}

function readRootOptions(command: Command): RootOptions {
  const root = rootFor(command);
  const values = root.opts() as Record<string, unknown>;
  return {
    relay: typeof values.relay === 'string' ? values.relay : undefined,
    browser: typeof values.browser === 'string' ? values.browser : undefined,
    session: typeof values.session === 'string' ? values.session : undefined,
    // 区分「agent 显式指定」和「Java 注入的默认值」：session start 只有前者
    // 才复用同一个 id，后者应当新建会话（agent 要能开多个）。
    sessionSource: root.getOptionValueSource('session') === 'cli' ? 'cli' : 'env',
    tabId: typeof values.tabId === 'number' ? values.tabId : undefined,
    json: values.json === true,
  };
}

function requiredSession(root: RootOptions, commandName: string): string {
  const session = root.session?.trim();
  if (!session) {
    // 对齐 OpenCLI 的 `<session>` 位置参数：会话名是必需的身份，不是可选修饰。
    throw new BskUsageError(
      `${commandName} requires --session <name>; pick a stable name for this task`
      + ' (e.g. --session xiaohongshu) and pass it to every command',
    );
  }
  return session;
}

/**
 * 根级选项与各自的取值个数。
 *
 * <p>只收「根级独有」的选项。{@code --url} 不在其中：{@code tab create --url <页面地址>}
 * 是子命令自己的选项，搬动会改变语义。根级的 relay 地址叫 {@code --relay}，
 * 就是为了不和它撞名（commander 里同名选项会被 program 层先吃掉，
 * 子命令的 {@code --url} 永远收不到值）。</p>
 */
const ROOT_OPTION_ARITY: Record<string, number> = {
  '--session': 1,
  '--browser': 1,
  '--tab-id': 1,
  '--relay': 1,
  '--json': 0,
};

/**
 * 把散落在子命令后面的根级选项前移到子命令之前。
 *
 * <p>commander 只在子命令<b>之前</b>识别 program 级选项，而模型写出来的 argv
 * 常常是 {@code click @e3 --session s1} 这种形式；不前移的话 commander 会报
 * "unknown option '--session'"，命令根本到不了协议层。</p>
 */
export function hoistRootOptions(argv: readonly string[]): string[] {
  const hoisted: string[] = [];
  const rest: string[] = [];
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    const arity = ROOT_OPTION_ARITY[token];
    if (arity === undefined) {
      rest.push(token);
      continue;
    }
    hoisted.push(token);
    if (arity === 1) {
      const value = argv[index + 1];
      if (value !== undefined) {
        hoisted.push(value);
        index += 1;
      }
    }
  }
  return [...hoisted, ...rest];
}

function requiredTabId(positional: readonly unknown[], root: RootOptions, commandName: string): number {
  const value = positional.length > 0 && positional[0] !== undefined ? positional[0] : root.tabId;
  // commander 的位置参数是字符串（Rust 的 i64 由 clap 转），这里统一成数字。
  const parsed = typeof value === 'string' ? parseInteger(value, `${commandName} tab id`) : value;
  if (typeof parsed !== 'number' || !Number.isInteger(parsed)) {
    throw new BskUsageError(`${commandName} requires a tab id (positional or --tab-id)`);
  }
  return parsed;
}

function asString(value: unknown, label: string): string {
  if (typeof value !== 'string' || value.length === 0) {
    throw new BskUsageError(`${label} is required`);
  }
  return value;
}

function asBooleanOrUndefined(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined;
}

/**
 * 未指定 `--out` 时的落盘位置：cwd 下的普通文件，与 Rust 版的 `--out` 默认值对齐但落在工作区内。
 *
 * <p>序号而不是毫秒时间戳：同一轮里模型会先截图看首屏、再截图验证结果，两个调用
 * 可能落在同一毫秒，也可能只差几毫秒。同进程内自增的序号保证「先拿到的 path」
 * 永远指向先写下的那张图，后一次截图不会把前一次覆盖掉——模型在上下文里持有的是
 * 它自己那次调用返回的 path，被覆盖就读不到当时那张了。</p>
 */
function defaultScreenshotPath(): string {
  return `screenshot-${Date.now()}-${++screenshotSeq}.png`;
}

interface WrittenScreenshot {
  /** 相对 cwd 的路径，直接进 `artifacts[].relativePath` 与 CLI 输出。 */
  relativePath: string;
  byteSize: number;
  meta: Record<string, unknown>;
}

/** 进程内截图计数器，保证默认文件名互不覆盖（见 {@link defaultScreenshotPath}）。 */
let screenshotSeq = 0;

/**
 * 把截图字节写到 `outPath`，返回 cwd 相对路径。
 *
 * <p>Java 侧只接受 workspace 内相对路径：绝对路径和 `..` 会在
 * {@code CliArtifactCandidateParser} 被丢弃，所以这里统一转相对路径后再上报。</p>
 *
 * <p>落盘失败必须与「截图失败」区分：图片字节此刻已经完整拿到（base64 已返回），
 * 只是没写到盘上。裸 IO 错误冒泡出去，Java 会把整条工具结果判成截图失败，
 * 模型收到的是模糊的 EBADF/ENOSPC，不知道该改路径还是该重试。所以这里包成
 * {@link BskUsageError}，让分类器给出「改 --out 再重试」而不是「别重试」。</p>
 */
async function writeScreenshot(outPath: string, bytes: Uint8Array): Promise<WrittenScreenshot> {
  const absolute = path.resolve(outPath);
  try {
    await fs.promises.mkdir(path.dirname(absolute), { recursive: true });
    await fs.promises.writeFile(absolute, bytes);
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    throw new BskUsageError(
      `screenshot captured (${bytes.length} bytes) but could not be written to "${outPath}": ${reason}; `
      + 'retry with a writable relative --out path',
    );
  }
  return {
    relativePath: path.relative(process.cwd(), absolute).split(path.sep).join('/'),
    byteSize: bytes.length,
    meta: {},
  };
}

/** 视口截图：协议回 base64，解码后写盘。 */
async function captureViewportToFile(
  runtime: BskCliRuntime,
  client: BskCaller,
  args: Parameters<typeof commandLayer.screenshot>[1],
  outPath: string,
): Promise<WrittenScreenshot> {
  const result = await runtime.command('screenshot')(client, args);
  const bytes = Buffer.from(result.image_base64, 'base64');
  const written = await writeScreenshot(outPath, bytes);
  written.meta = {
    tab_id: result.tab_id,
    width: result.width,
    height: result.height,
    format: result.format,
    ...(result.capture_id === undefined ? {} : { capture_id: result.capture_id }),
    ...(result.capture_unavailable === undefined ? {} : { capture_unavailable: result.capture_unavailable }),
  };
  return written;
}

/**
 * 全页截图：分片拉取后一次性写盘。
 *
 * <p>{@code tool.screenshot_full_page} 只回 capture id，图本身要按 offset 分块读，
 * 读完必须 release 让扩展丢掉缓存。这里自己走 read/release 循环而不是复用
 * {@code readScreenshotCapture}，是为了让两个调用都能被 {@code commandLayer} 替换
 * （测试与宿主注入），与 Rust 版 {@code write_full_page} 的分工一致。</p>
 */
async function captureFullPageToFile(
  runtime: BskCliRuntime,
  client: BskCaller,
  args: Parameters<typeof commandLayer.screenshotFullPage>[1],
  outPath: string,
): Promise<WrittenScreenshot> {
  const capture = await runtime.command('screenshotFullPage')(client, args);
  const chunks: Buffer[] = [];
  let offset = 0;
  try {
    for (;;) {
      const chunk = await runtime.command('screenshotRead')(client, {
        sessionId: args.sessionId,
        captureId: capture.capture_id,
        offset,
      });
      chunks.push(validateScreenshotChunk(chunk, offset, capture.byte_size));
      offset = chunk.next_offset;
      if (chunk.eof) break;
    }
  } finally {
    // release 是尽力而为：读取失败不能掩盖真正的错误。
    await runtime.command('screenshotRelease')(client, {
      sessionId: args.sessionId,
      captureId: capture.capture_id,
    }).catch(() => undefined);
  }
  const written = await writeScreenshot(outPath, Buffer.concat(chunks));
  written.meta = {
    tab_id: capture.tab_id,
    width: capture.width,
    height: capture.height,
    format: capture.format,
    ...(capture.scope === undefined ? {} : { scope: capture.scope }),
  };
  return written;
}

function humanLine(label: string, result: unknown, sessionId?: string): string {
  if (result === undefined) return `${label} ok`;
  if (result === null) return `${label} ok null`;
  if (typeof result !== 'object') return `${label}: ${String(result)}`;

  const record = result as Record<string, unknown>;
  const fields: string[] = [];
  for (const key of [
    'session_id',
    'tab_id',
    'url',
    'final_url',
    'reached',
    'outcome',
    'waited_ms',
    'count',
  ]) {
    const value = record[key];
    if (value !== undefined && (typeof value === 'string' || typeof value === 'number')) {
      fields.push(`${key}=${value}`);
    }
  }
  // 结果里没带 session_id 时补上实际用的那个：agent 同时开多个会话时，
  // 每一条输出都能让它核对「我刚才操作的是哪个会话」。
  if (sessionId && record.session_id === undefined) {
    fields.unshift(`session_id=${sessionId}`);
  }
  return fields.length > 0 ? `${label} ok ${fields.join(' ')}` : `${label} ok`;
}

function errorText(error: unknown): string {
  if (error instanceof BskRpcError) {
    return `${error.code}: ${error.message}`;
  }
  if (error instanceof Error) return error.message;
  return String(error);
}

/**
 * Render a failure for the agent.
 *
 * The first line is unchanged from before (`Error: code: message`) so nothing
 * that parses it today breaks. The two extra lines tell the agent whether a
 * retry is safe and what to do instead — without them a blind re-run can
 * repeat a write that already applied.
 */
function formatFailure(error: unknown): string {
  const { class: retry, hint } = classifyFailure(error);
  return `Error: ${errorText(error)}\nHint: ${hint}\nRetry: ${retry}\n`;
}

/** Structured counterpart of {@link formatFailure} for `--json` callers. */
function failureEnvelope(error: unknown): Record<string, unknown> {
  const { class: retry, hint } = classifyFailure(error);
  return {
    ...(error instanceof BskRpcError ? { code: error.code } : {}),
    message: error instanceof BskRpcError ? error.message : errorText(error),
    retry,
    hint,
  };
}

/**
 * 扩展侧的「会话不存在」错误。
 *
 * <p>对齐 {@code BrowserSkill/apps/extension/src/tools/shared.ts} 的
 * {@code lookupSession()}：找不到会话时回
 * {@code {code:"not_found", message:"session <id> unknown"}}。</p>
 */
function isUnknownSessionError(error: unknown): boolean {
  return error instanceof BskRpcError
    && error.code === 'not_found'
    && /session .* unknown/.test(error.message);
}

/**
 * 扩展侧的「会话已存在」错误。
 *
 * <p>{@code BrowserSkill/apps/extension/src/session-manager/manager.ts} 的
 * {@code start()} 对重复 id 直接 throw，被 {@code tools/session.ts} 收敛成
 * {@code protocol_error} + "[bh] session &lt;id&gt; already exists"。</p>
 *
 * <p>Java 侧按一次 agent run 注入固定 session id，所以「显式 session start」
 * 撞上已有会话是常态而不是异常：这是幂等语义，应当复用既有会话。</p>
 */
function isExistingSessionError(error: unknown): boolean {
  return error instanceof BskRpcError
    && error.code === 'protocol_error'
    && /session .* already exists/.test(error.message);
}

function isCommanderControlFlow(error: unknown): boolean {
  const code = (error as { code?: unknown } | null)?.code;
  return code === 'commander.helpDisplayed' || code === 'commander.version';
}

function exitCodeFor(error: unknown): number {
  if (error instanceof BskUsageError) return 2;
  if (error instanceof BskRpcError && error.code === 'cancelled') return 130;
  return 1;
}

/**
 * Per-CLI runtime. The signal wrapper is deliberately outside the command
 * layer: every command still receives the same `BskCaller` contract, while
 * `BskClient` gets the AbortSignal that causes it to send `cancel`.
 */
class BskCliRuntime {
  private readonly providedClient?: BskCliClient;
  private readonly factory: BskCliClientFactory;
  private readonly commandOverrides: Partial<CommandLayer>;
  private readonly stdout: BskCliOutput;
  private readonly stderr: BskCliOutput;
  private clientPromise?: Promise<BskCliClient>;
  private closePromise?: Promise<void>;
  private activeController?: AbortController;
  private interrupted = false;

  constructor(options: BskCliBuildOptions) {
    this.providedClient = options.client;
    this.factory = options.clientFactory ?? (async (clientOptions) => {
      const connectOptions: BskClientOptions = {
        url: clientOptions.url,
        ...(clientOptions.browserId === undefined ? {} : { browserId: clientOptions.browserId }),
      };
      return BskClient.connect(connectOptions);
    });
    this.commandOverrides = options.commandLayer ?? {};
    this.stdout = options.stdout ?? DEFAULT_STDOUT;
    this.stderr = options.stderr ?? DEFAULT_STDERR;
  }

  command<K extends keyof CommandLayer>(name: K): CommandLayer[K] {
    return (this.commandOverrides[name] ?? commandLayer[name]) as CommandLayer[K];
  }

  writeOut(text: string): void {
    this.stdout.write(text);
  }

  writeErr(text: string): void {
    this.stderr.write(text);
  }

  private async getClient(root: RootOptions): Promise<BskCliClient> {
    if (this.providedClient) return this.providedClient;
    if (!this.clientPromise) {
      const url = root.relay?.trim();
      if (!url) {
        throw new BskUsageError(
          'missing relay URL: pass --relay <ws-url> or set OPENCLI_BSK_RELAY_URL',
        );
      }
      let parsed: URL;
      try {
        parsed = new URL(url);
      } catch {
        throw new BskUsageError(`invalid relay URL: ${url}`);
      }
      if (parsed.protocol !== 'ws:' && parsed.protocol !== 'wss:') {
        throw new BskUsageError(`relay URL must use ws:// or wss:// (got ${parsed.protocol})`);
      }
      const browser = root.browser?.trim();
      this.clientPromise = this.factory({
        url,
        ...(browser ? { browserId: browser } : {}),
      });
    }
    return this.clientPromise;
  }

  private abortableCaller(client: BskCliClient, signal: AbortSignal): BskCaller {
    return {
      call: <T = unknown>(method: Parameters<BskCaller['call']>[0], params?: unknown, options?: BskCallOptions) =>
        client.call<T>(method, params, { ...options, signal }),
      cancel: (rpcId: string) => client.cancel(rpcId),
    };
  }

  private async waitForAbortable<T>(operation: Promise<T>, signal: AbortSignal): Promise<T> {
    let timer: ReturnType<typeof setTimeout> | undefined;
    let removeAbortListener: (() => void) | undefined;
    const interrupted = new Promise<never>((_, reject) => {
      const onAbort = (): void => {
        // Leave enough of the two-second cancellation budget for the client's
        // cancel frame to reach the relay before the CLI gives up.
        timer = setTimeout(() => {
          reject(new BskRpcError({ code: 'cancelled', message: 'interrupted by SIGINT' }));
        }, SIGINT_GRACE_MS);
      };
      removeAbortListener = (): void => signal.removeEventListener('abort', onAbort);
      if (signal.aborted) onAbort();
      else signal.addEventListener('abort', onAbort, { once: true });
    });

    try {
      return await Promise.race([operation, interrupted]);
    } finally {
      if (timer !== undefined) clearTimeout(timer);
      removeAbortListener?.();
      // If the race ended on SIGINT, the command may still be unwinding after
      // its peer acknowledges cancellation. Avoid an unhandled rejection.
      void operation.catch(() => undefined);
    }
  }

  async execute(
    command: Command,
    label: string,
    handler: (client: BskCaller, root: RootOptions) => Promise<unknown>,
  ): Promise<unknown> {
    const root = readRootOptions(command);
    const controller = new AbortController();
    this.activeController = controller;
    const onSigint = (): void => {
      this.interrupted = true;
      controller.abort();
    };
    process.once('SIGINT', onSigint);

    try {
      const client = await this.getClient(root);
      if (controller.signal.aborted) {
        throw new BskRpcError({ code: 'cancelled', message: 'interrupted by SIGINT' });
      }
      const caller = this.abortableCaller(client, controller.signal);
      const result = await this.runWithSessionRecovery(handler, caller, root, controller.signal);
      if (controller.signal.aborted) {
        throw new BskRpcError({ code: 'cancelled', message: 'interrupted by SIGINT' });
      }
      if (root.json) {
        this.writeOut(`${JSON.stringify(result, null, 2)}\n`);
      } else {
        this.writeOut(`${humanLine(label, result, root.session?.trim())}\n`);
      }
      return result;
    } finally {
      process.removeListener('SIGINT', onSigint);
      if (this.activeController === controller) this.activeController = undefined;
    }
  }

  /**
   * 执行命令；会话在扩展侧不存在时用<b>同一个名字</b>补建一次再重试。
   *
   * <p>这是我们对 OpenCLI {@code createOwnedTabLease} 的等价物：会话名即身份，
   * 名字不存在就自动建，调用方不需要先 {@code session start}。缺少这一步的话，
   * agent 直接发 {@code navigate} 会拿到 {@code not_found}。</p>
   *
   * <p>扩展的 session 存在 MV3 service worker 的内存里，transport 断开时会被
   * 整体清掉（{@code background.ts} 的 disconnect cleanup），所以即便是之前
   * 建过、用到一半的会话也会回 {@code not_found}；这种情况下补建用的是同一个
   * 名字，agent 侧看到的身份不变。</p>
   */
  private async runWithSessionRecovery(
    handler: (client: BskCaller, root: RootOptions) => Promise<unknown>,
    caller: BskCaller,
    root: RootOptions,
    signal: AbortSignal,
  ): Promise<unknown> {
    try {
      return await this.waitForAbortable(handler(caller, root), signal);
    } catch (error) {
      const sessionId = root.session?.trim();
      if (!sessionId || !isUnknownSessionError(error)) throw error;
      await this.waitForAbortable(
        this.command('sessionStart')(caller, {
          sessionId,
          ...(root.browser ? { browserInstanceId: root.browser } : {}),
        }),
        signal,
      );
      return this.waitForAbortable(handler(caller, root), signal);
    }
  }

  async close(): Promise<void> {
    if (this.closePromise) return this.closePromise;
    this.closePromise = (async () => {
      const client = this.providedClient ?? (this.clientPromise ? await this.clientPromise.catch(() => undefined) : undefined);
      if (client?.close) await client.close();
    })();
    return this.closePromise;
  }
}

function addCommonTargetOptions(command: Command): void {
  command
    .option('--ref <ref>', 'snapshot reference')
    .option('--selector <css>', 'CSS selector');
}

function addAction(runtime: BskCliRuntime, command: Command, label: string, handler: CommandHandler): void {
  command.action(async (...raw: unknown[]) => {
    const actionCommand = raw[raw.length - 1] as Command;
    const local = (raw[raw.length - 2] ?? {}) as LocalOptions;
    const positional = raw.slice(0, -2);
    await runtime.execute(actionCommand, label, (client, root) =>
      handler(positional, local, client, root, actionCommand),
    );
  });
}

/**
 * Construct the CLI without parsing argv or opening a socket.
 *
 * The returned command is intentionally exported so tests can inspect its
 * exact command tree and inject a recording client through `BskCliBuildOptions`.
 */
export function buildBskCli(options: BskCliBuildOptions = {}): Command {
  return createBskCli(options).cli;
}

/**
 * Internal factory that also hands back the runtime, so `runBskCli` can close
 * the relay connection after the command settles.
 */
function createBskCli(options: BskCliBuildOptions): { cli: Command; runtime: BskCliRuntime } {
  const runtime = new BskCliRuntime(options);
  const program = new Command('bsk');
  program
    .description('browser-skill remote relay client')
    .exitOverride()
    .option(
      '--relay <ws-url>',
      'relay WebSocket URL',
      // 优先用 bsk 专用变量：OPENCLI_RELAY_URL 在 opencli 侧是 relay 的 HTTP 语义，
      // 拿来当 WS 地址会直接失败（见 specs/021 KNOWN-FIXES P0-3）。
      process.env.OPENCLI_BSK_RELAY_URL ?? process.env.OPENCLI_RELAY_URL,
    )
    .option('--browser <id>', 'browser instance id', process.env.OPENCLI_RELAY_BROWSER_ID)
    .option(
      '--session <id>',
      // 对齐 OpenCLI：会话名由调用方（agent）给，名字即身份，不存在就自动建。
      // 没有默认值——Java 侧不再注入，CLI 也不猜。环境变量仅为外部脚本保留。
      'session name used by session-scoped commands; required by every tool command',
      process.env.OPENCLI_BSK_SESSION_ID,
    )
    .option('--tab-id <n>', 'tab id used by tab-scoped commands', (value: string) =>
      parseInteger(value, '--tab-id'),
    )
    .option('--json', 'emit the command result as JSON')
    .configureOutput({
      writeOut: (text) => runtime.writeOut(text),
      writeErr: (text) => runtime.writeErr(text),
      outputError: (text, write) => write(text),
    });

  // ── Navigation ────────────────────────────────────────────────────────────
  const navigate = program.command('navigate <url>').description('navigate the active tab');
  navigate
    .option('--wait-until <phase>', 'load, domcontentloaded, networkidle, or commit')
    .option('--timeout <duration>', 'navigation timeout', parseTimeout);
  addAction(runtime, navigate, 'navigate', (positional, local, client, root) =>
    runtime.command('navigate')(client, {
      sessionId: requiredSession(root, 'navigate'),
      url: asString(positional[0], 'navigate url'),
      tabId: root.tabId,
      ...(local.waitUntil === undefined ? {} : { waitUntil: local.waitUntil as WaitUntil }),
      ...(local.timeout === undefined ? {} : { timeoutMs: local.timeout as number }),
    }),
  );

  for (const [name, label, commandName] of [
    ['navigate-back', 'navigate-back', 'navigateBack'],
    ['navigate-forward', 'navigate-forward', 'navigateForward'],
  ] as const) {
    const command = program.command(name).description(`${label} in the active tab`);
    command
      .option('--wait-until <phase>', 'load, domcontentloaded, networkidle, or commit')
      .option('--timeout <duration>', 'navigation timeout', parseTimeout);
    addAction(runtime, command, label, (positional, local, client, root) =>
      runtime.command(commandName)(client, {
        sessionId: requiredSession(root, label),
        tabId: root.tabId,
        ...(local.waitUntil === undefined ? {} : { waitUntil: local.waitUntil as WaitUntil }),
        ...(local.timeout === undefined ? {} : { timeoutMs: local.timeout as number }),
      }),
    );
  }

  const reload = program.command('reload').description('reload the active tab');
  reload
    .option('--wait-until <phase>', 'load, domcontentloaded, networkidle, or commit')
    .option('--hard', 'bypass the browser cache')
    .option('--timeout <duration>', 'reload timeout', parseTimeout);
  addAction(runtime, reload, 'reload', (_positional, local, client, root) =>
    runtime.command('reload')(client, {
      sessionId: requiredSession(root, 'reload'),
      tabId: root.tabId,
      ...(local.waitUntil === undefined ? {} : { waitUntil: local.waitUntil as WaitUntil }),
      ...(local.hard ? { hard: true } : {}),
      ...(local.timeout === undefined ? {} : { timeoutMs: local.timeout as number }),
    }),
  );

  // ── Element interaction ───────────────────────────────────────────────────
  const click = program.command('click [target]').description('click an element');
  addCommonTargetOptions(click);
  click
    .option('--capture <capture-id>')
    .option('--image-x <n>', 'screenshot x coordinate', (value: string) => parseFiniteNumber(value, '--image-x'))
    .option('--image-y <n>', 'screenshot y coordinate', (value: string) => parseFiniteNumber(value, '--image-y'))
    // choices 在 Option 上而不是 Command 上：非法值要在客户端就拒绝，
    // 否则一路透传到扩展再撞上 CDP 的 "Invalid mouse button"。
    // `Option` 取别名，本工程 tsconfig 未排除 DOM lib，全局 Option 是 HTMLOptionElement。
    click.addOption(
      new CommanderOption('--button <button>', 'left, middle, or right')
        .choices(MOUSE_BUTTONS)
        .default('left'),
    )
    .option('--click-count <n>', 'number of clicks', (value: string) => parsePositiveInteger(value, '--click-count'))
    .option('--modifiers <list>', 'comma-separated modifier names')
    .option('--timeout <duration>', 'interaction timeout', parseTimeout);
  addAction(runtime, click, 'click', (positional, local, client, root) =>
    runtime.command('click')(client, {
      sessionId: requiredSession(root, 'click'),
      tabId: root.tabId,
      target: typeof positional[0] === 'string' ? positional[0] : undefined,
      ref: typeof local.ref === 'string' ? local.ref : undefined,
      selector: typeof local.selector === 'string' ? local.selector : undefined,
      captureId: typeof local.capture === 'string' ? local.capture : undefined,
      imageX: typeof local.imageX === 'number' ? local.imageX : undefined,
      imageY: typeof local.imageY === 'number' ? local.imageY : undefined,
      button: local.button as MouseButton | undefined,
      clickCount: typeof local.clickCount === 'number' ? local.clickCount : undefined,
      modifiers: typeof local.modifiers === 'string' ? local.modifiers : undefined,
      timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    }),
  );

  const hover = program.command('hover [target]').description('hover an element');
  addCommonTargetOptions(hover);
  hover
    .option('--modifiers <list>', 'comma-separated modifier names')
    .option('--settle <duration>', 'pointer settle delay', parseTimeout)
    .option('--timeout <duration>', 'interaction timeout', parseTimeout);
  addAction(runtime, hover, 'hover', (positional, local, client, root) =>
    runtime.command('hover')(client, {
      sessionId: requiredSession(root, 'hover'),
      tabId: root.tabId,
      target: typeof positional[0] === 'string' ? positional[0] : undefined,
      ref: typeof local.ref === 'string' ? local.ref : undefined,
      selector: typeof local.selector === 'string' ? local.selector : undefined,
      modifiers: typeof local.modifiers === 'string' ? local.modifiers : undefined,
      settleMs: typeof local.settle === 'number' ? local.settle : undefined,
      timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    }),
  );

  for (const name of ['focus', 'blur'] as const) {
    const command = program.command(`${name} [target]`).description(`${name} an element`);
    addCommonTargetOptions(command);
    command.option('--timeout <duration>', 'interaction timeout', parseTimeout);
    addAction(runtime, command, name, (positional, local, client, root) =>
      runtime.command(name)(client, {
        sessionId: requiredSession(root, name),
        tabId: root.tabId,
        target: typeof positional[0] === 'string' ? positional[0] : undefined,
        ref: typeof local.ref === 'string' ? local.ref : undefined,
        selector: typeof local.selector === 'string' ? local.selector : undefined,
        timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
      }),
    );
  }

  const fill = program.command('fill [target]').description('fill an input element');
  addCommonTargetOptions(fill);
  fill
    .requiredOption('--value <text>')
    .option('--no-clear', 'do not clear the existing value first')
    .option('--timeout <duration>', 'interaction timeout', parseTimeout);
  addAction(runtime, fill, 'fill', (positional, local, client, root, command) =>
    runtime.command('fill')(client, {
      sessionId: requiredSession(root, 'fill'),
      tabId: root.tabId,
      value: asString(local.value, '--value'),
      target: typeof positional[0] === 'string' ? positional[0] : undefined,
      ref: typeof local.ref === 'string' ? local.ref : undefined,
      selector: typeof local.selector === 'string' ? local.selector : undefined,
      // `--no-clear` defaults to `clear: true`; only forward an explicit flag.
      noClear: command.getOptionValueSource('clear') === 'cli' && local.clear === false,
      timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    }),
  );

  const press = program.command('press <key>').description('press a key');
  addCommonTargetOptions(press);
  press
    .option('--modifiers <list>', 'comma-separated modifier names')
    .option('--hold-ms <n>', 'hold duration in milliseconds', (value: string) =>
      parseInteger(value, '--hold-ms'),
    )
    .option('--timeout <duration>', 'interaction timeout', parseTimeout);
  addAction(runtime, press, 'press', (positional, local, client, root) =>
    runtime.command('press')(client, {
      sessionId: requiredSession(root, 'press'),
      tabId: root.tabId,
      key: asString(positional[0], 'press key'),
      modifiers: typeof local.modifiers === 'string' ? local.modifiers : undefined,
      ref: typeof local.ref === 'string' ? local.ref : undefined,
      selector: typeof local.selector === 'string' ? local.selector : undefined,
      holdMs: typeof local.holdMs === 'number' ? local.holdMs : undefined,
      timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    }),
  );

  const select = program.command('select [target]').description('select option values');
  addCommonTargetOptions(select);
  select
    .requiredOption('--value <value>', 'option value (repeatable)', collect, [])
    .option('--timeout <duration>', 'interaction timeout', parseTimeout);
  addAction(runtime, select, 'select', (positional, local, client, root) =>
    runtime.command('select')(client, {
      sessionId: requiredSession(root, 'select'),
      tabId: root.tabId,
      values: (local.value as string[] | undefined) ?? [],
      target: typeof positional[0] === 'string' ? positional[0] : undefined,
      ref: typeof local.ref === 'string' ? local.ref : undefined,
      selector: typeof local.selector === 'string' ? local.selector : undefined,
      timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    }),
  );

  // ── Scroll / observation / evaluation ────────────────────────────────────
  const wheel = program.command('wheel [target]').description('dispatch a mouse-wheel gesture');
  addCommonTargetOptions(wheel);
  wheel
    .option('--delta-x <n>', 'horizontal delta', (value: string) => parseFiniteNumber(value, '--delta-x'))
    .option('--delta-y <n>', 'vertical delta', (value: string) => parseFiniteNumber(value, '--delta-y'))
    .option('--modifiers <list>', 'comma-separated modifier names')
    .option('--timeout <duration>', 'wheel timeout', parseTimeout);
  addAction(runtime, wheel, 'wheel', (positional, local, client, root) =>
    runtime.command('wheel')(client, {
      sessionId: requiredSession(root, 'wheel'),
      tabId: root.tabId,
      target: typeof positional[0] === 'string' ? positional[0] : undefined,
      ref: typeof local.ref === 'string' ? local.ref : undefined,
      selector: typeof local.selector === 'string' ? local.selector : undefined,
      deltaX: typeof local.deltaX === 'number' ? local.deltaX : undefined,
      deltaY: typeof local.deltaY === 'number' ? local.deltaY : undefined,
      modifiers: typeof local.modifiers === 'string' ? local.modifiers : undefined,
      timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    }),
  );

  const scrollTo = program.command('scroll-to [target]').description('scroll an element into view');
  addCommonTargetOptions(scrollTo);
  scrollTo.option('--timeout <duration>', 'scroll timeout', parseTimeout);
  addAction(runtime, scrollTo, 'scroll-to', (positional, local, client, root) =>
    runtime.command('scrollTo')(client, {
      sessionId: requiredSession(root, 'scroll-to'),
      tabId: root.tabId,
      target: typeof positional[0] === 'string' ? positional[0] : undefined,
      ref: typeof local.ref === 'string' ? local.ref : undefined,
      selector: typeof local.selector === 'string' ? local.selector : undefined,
      timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    }),
  );

  const snapshot = program.command('snapshot').description('capture an accessibility snapshot');
  snapshot
    .option('--max-depth <n>', 'maximum tree depth', (value: string) => parseInteger(value, '--max-depth'))
    .option('--max-tokens <n>', 'maximum output tokens', (value: string) => parseInteger(value, '--max-tokens'));
  addAction(runtime, snapshot, 'snapshot', (_positional, local, client, root) =>
    runtime.command('snapshot')(client, {
      sessionId: requiredSession(root, 'snapshot'),
      tabId: root.tabId,
      maxDepth: typeof local.maxDepth === 'number' ? local.maxDepth : undefined,
      maxTokens: typeof local.maxTokens === 'number' ? local.maxTokens : undefined,
    }),
  );

  const observe = program.command('observe').description('observe the page surface');
  observe
    .option('--cursor <cursor>')
    .option('--max-depth <n>', 'maximum tree depth', (value: string) => parseInteger(value, '--max-depth'))
    .option('--max-tokens <n>', 'maximum output tokens', (value: string) => parseInteger(value, '--max-tokens'))
    .option('--debug-surfaces')
    .option('--probe-hover');
  addAction(runtime, observe, 'observe', (_positional, local, client, root) =>
    runtime.command('observe')(client, {
      sessionId: requiredSession(root, 'observe'),
      tabId: root.tabId,
      cursor: typeof local.cursor === 'string' ? local.cursor : undefined,
      maxDepth: typeof local.maxDepth === 'number' ? local.maxDepth : undefined,
      maxTokens: typeof local.maxTokens === 'number' ? local.maxTokens : undefined,
      debugSurfaces: local.debugSurfaces === true,
      probeHover: local.probeHover === true,
    }),
  );

  const getHtml = program.command('get-html').description('return page HTML');
  getHtml
    .option('--ref <ref>')
    .option('--max-bytes <n>', 'maximum bytes', (value: string) => parseInteger(value, '--max-bytes'));
  addAction(runtime, getHtml, 'get-html', (_positional, local, client, root) =>
    runtime.command('getHtml')(client, {
      sessionId: requiredSession(root, 'get-html'),
      tabId: root.tabId,
      ref: typeof local.ref === 'string' ? local.ref : undefined,
      maxBytes: typeof local.maxBytes === 'number' ? local.maxBytes : undefined,
    }),
  );

  const evaluate = program.command('evaluate <expression>').description('evaluate JavaScript in the page');
  evaluate
    .option('--frame-id <id>', 'frame id from "bsk frames"; defaults to the main frame')
    .option('--await-promise')
    .option('--no-await-promise')
    .option('--return-by-value')
    .option('--no-return-by-value')
    .option('--timeout <duration>', 'evaluation timeout', parseTimeout);
  addAction(runtime, evaluate, 'evaluate', (positional, local, client, root) =>
    runtime.command('evaluate')(client, {
      sessionId: requiredSession(root, 'evaluate'),
      tabId: root.tabId,
      expression: asString(positional[0], 'evaluate expression'),
      ...(typeof local.frameId === 'string' ? { frameId: local.frameId } : {}),
      awaitPromise: asBooleanOrUndefined(local.awaitPromise),
      returnByValue: asBooleanOrUndefined(local.returnByValue),
      timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    }),
  );

  const screenshot = program.command('screenshot').description('capture a screenshot');
  screenshot
    .option('--ref <ref>')
    .option('--width <n>', 'capture width in CSS pixels (requires --height)', (value: string) => parseInteger(value, '--width'))
    .option('--height <n>', 'capture height in CSS pixels (requires --width)', (value: string) => parseInteger(value, '--height'))
    .option('--full-page')
    .option('--scope <scope>', 'follow or current')
    .option('--timeout <duration>', 'full-page timeout', parseTimeout)
    .option('--out <path>', 'output PNG path (relative to cwd); defaults to ./screenshot-<unix-ms>.png');
  addAction(runtime, screenshot, 'screenshot', async (_positional, local, client, root) => {
    const sessionId = requiredSession(root, 'screenshot');
    const outPath = typeof local.out === 'string' ? local.out : defaultScreenshotPath();
    const written = local.fullPage === true
      ? await captureFullPageToFile(runtime, client, {
        sessionId,
        tabId: root.tabId,
        scope: typeof local.scope === 'string' ? local.scope as ScreenshotScope : undefined,
        timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
      }, outPath)
      : await captureViewportToFile(runtime, client, {
        sessionId,
        tabId: root.tabId,
        ref: typeof local.ref === 'string' ? local.ref : undefined,
        width: typeof local.width === 'number' ? local.width : undefined,
        height: typeof local.height === 'number' ? local.height : undefined,
      }, outPath);
    // `artifacts` 是 Java 侧的产物登记协议（CliArtifactCandidateParser）：
    // 路径必须是 cwd 相对路径，绝对路径会被协议层直接丢弃。
    return {
      ...written.meta,
      path: written.relativePath,
      byte_size: written.byteSize,
      artifacts: [{ relativePath: written.relativePath, size: written.byteSize, mime: 'image/png' }],
    };
  });

  const waitForNavigation = program.command('wait-for-navigation').description('wait for navigation to settle');
  waitForNavigation
    .option('--wait-until <phase>', 'load, domcontentloaded, networkidle, or commit')
    .option('--timeout <duration>', 'wait timeout', parseTimeout);
  addAction(runtime, waitForNavigation, 'wait-for-navigation', (_positional, local, client, root) =>
    runtime.command('waitForNavigation')(client, {
      sessionId: requiredSession(root, 'wait-for-navigation'),
      tabId: root.tabId,
      waitUntil: typeof local.waitUntil === 'string' ? local.waitUntil as WaitUntil : undefined,
      timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    }),
  );

  const waitMs = program.command('wait-ms <duration>').description('wait for a duration');
  addAction(runtime, waitMs, 'wait-ms', (positional, _local, client) =>
    runtime.command('waitMs')(client, { durationMs: parseDuration(asString(positional[0], 'duration')) }),
  );

  const evaluateConsole = program.command('console').description('read buffered console entries');
  evaluateConsole
    .option('--since <n>', 'sequence cursor', (value: string) => parseInteger(value, '--since'))
    .option('--limit <n>', 'maximum entries', (value: string) => parsePositiveInteger(value, '--limit'))
    .option('--max-text-chars <n>', 'maximum text size', (value: string) => parsePositiveInteger(value, '--max-text-chars'))
    .option('--include-stack');
  addAction(runtime, evaluateConsole, 'console', (_positional, local, client, root) =>
    runtime.command('console')(client, {
      sessionId: requiredSession(root, 'console'),
      tabId: root.tabId,
      since: typeof local.since === 'number' ? local.since : undefined,
      limit: typeof local.limit === 'number' ? local.limit : undefined,
      maxTextChars: typeof local.maxTextChars === 'number' ? local.maxTextChars : undefined,
      includeStack: local.includeStack === true,
    }),
  );

  const network = program.command('network').description('read buffered network entries');
  network
    .option('--since <n>', 'sequence cursor', (value: string) => parseInteger(value, '--since'))
    .option('--limit <n>', 'maximum entries', (value: string) => parsePositiveInteger(value, '--limit'))
    .option('--max-text-chars <n>', 'maximum text size', (value: string) => parsePositiveInteger(value, '--max-text-chars'));
  addAction(runtime, network, 'network', (_positional, local, client, root) =>
    runtime.command('network')(client, {
      sessionId: requiredSession(root, 'network'),
      tabId: root.tabId,
      since: typeof local.since === 'number' ? local.since : undefined,
      limit: typeof local.limit === 'number' ? local.limit : undefined,
      maxTextChars: typeof local.maxTextChars === 'number' ? local.maxTextChars : undefined,
    }),
  );

  const cookies = program.command('cookies').description('read cookies for a domain or url');
  cookies
    .option('--domain <domain>', 'cookie domain filter')
    .option('--url <url>', 'cookie scope by url (wins over --domain)');
  addAction(runtime, cookies, 'cookies', (_positional, local, client, root) =>
    runtime.command('cookies')(client, {
      sessionId: requiredSession(root, 'cookies'),
      tabId: root.tabId,
      domain: typeof local.domain === 'string' ? local.domain : undefined,
      url: typeof local.url === 'string' ? local.url : undefined,
    }),
  );

  const frames = program.command('frames').description('list the tab frame graph');
  addAction(runtime, frames, 'frames', (_positional, _local, client, root) =>
    runtime.command('frames')(client, {
      sessionId: requiredSession(root, 'frames'),
      tabId: root.tabId,
    }),
  );

  const cdp = program.command('cdp <method>').description('dispatch one allowlisted CDP method');
  cdp.option('--params <json>', 'CDP params as a JSON object');
  addAction(runtime, cdp, 'cdp', (positional, local, client, root) =>
    runtime.command('cdp')(client, {
      sessionId: requiredSession(root, 'cdp'),
      tabId: root.tabId,
      method: asString(positional[0], 'method'),
      params: typeof local.params === 'string' ? parseJsonObject(local.params, '--params') : undefined,
    }),
  );

  const requestHelp = program.command('request-help [prompt]').description('request human assistance');
  requestHelp
    .option('--prompt <text>')
    .option('--title <title>')
    .option('--target <target>', 'target (repeatable)', collect, [])
    .option('--completion-criteria <json>')
    .option('--timeout <duration>', 'human-help timeout', parseTimeout);
  addAction(runtime, requestHelp, 'request-help', (positional, local, client, root) => {
    const prompt = typeof local.prompt === 'string' ? local.prompt : positional[0];
    return runtime.command('requestHelp')(client, {
      sessionId: requiredSession(root, 'request-help'),
      tabId: root.tabId,
      prompt: asString(prompt, 'request-help prompt'),
      title: typeof local.title === 'string' ? local.title : undefined,
      targets: (local.target as string[] | undefined) ?? [],
      completionCriteria: typeof local.completionCriteria === 'string' ? local.completionCriteria : undefined,
      timeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    });
  });

  // ── Remote-only file commands ─────────────────────────────────────────────
  const unsupportedRemote = (name: string): BskUsageError =>
    new BskUsageError(
      `${name} is unsupported over remote relay sessions; the browser extension rejects tool.${name}`,
    );

  const addUnsupportedRemoteAction = (command: Command, name: string): void => {
    // Registered so `--help` documents them, but never dispatched: the extension
    // rejects `tool.upload` / `tool.download` for remote sessions. Fail before
    // resolving a relay client so no socket is opened.
    command.action(() => {
      throw unsupportedRemote(name);
    });
  };

  const upload = program.command('upload [target]').description('upload files (unsupported over remote relay)');
  addCommonTargetOptions(upload);
  upload
    .option('--file <path>', 'file (repeatable)', collect, [])
    .option('--mode <mode>', 'input or drop')
    .option('--timeout <duration>', 'upload timeout', parseTimeout);
  addUnsupportedRemoteAction(upload, 'upload');

  const download = program.command('download [target]').description('download a file (unsupported over remote relay)');
  addCommonTargetOptions(download);
  download
    .option('--out <path>')
    .option('--overwrite')
    .option('--timeout <duration>', 'download timeout', parseTimeout);
  addUnsupportedRemoteAction(download, 'download');

  // ── Session lifecycle ─────────────────────────────────────────────────────
  const session = program.command('session').description('manage browser sessions');
  const sessionStart = session.command('start').description('start a session');
  sessionStart
    .option('--width <n>', 'agent window width', (value: string) => parseInteger(value, '--width'))
    .option('--height <n>', 'agent window height', (value: string) => parseInteger(value, '--height'))
    .option('--no-focus', 'open the agent window in the background');
  addAction(runtime, sessionStart, 'session start', async (_positional, local, client, root, command) => {
    // 走扩展层的方法（tool.session_start），不是 daemon 层的 session.start：
    // 后者扩展不实现，经中继访问只会得到 unknown_method。
    //
    // id 的选择规则：
    // - agent 显式传 `--session <id>`：复用它（重复 start 幂等，见下面的 catch）。
    // - 否则（包括只有 Java 注入的默认值）：**生成新 id**，开一个新会话。
    //   这样 agent 能主动开多个会话，而不是永远挤在注入的那一个里。
    const explicitSession = root.sessionSource === 'cli' ? root.session?.trim() : undefined;
    const sessionId = explicitSession || generateSessionId();
    const params = {
      sessionId,
      browserInstanceId: root.browser,
      width: typeof local.width === 'number' ? local.width : undefined,
      height: typeof local.height === 'number' ? local.height : undefined,
      // `--no-focus` defaults to `focused: true`; only forward an explicit flag.
      focused: command.getOptionValueSource('focus') === 'cli' ? local.focus === true : undefined,
    };
    let result: unknown;
    try {
      result = await runtime.command('sessionStart')(client, params);
    } catch (error) {
      // 会话 id 已存在（Java 按一次 run 注入固定 id，所以这是常态）：
      // 幂等返回成功，<b>不</b> stop 再重建。
      //
      // 之前是 stop→start，那是错的：session stop 会关掉 Agent Window 及其标签页，
      // 用户正在看的那一页会被关掉、窗口闪一下再开。而且 agent 反复调用时
      // 会反复关开。复用不动现有会话，refStore 也保持原样。
      //
      // 代价：这条路径拿不到 agent_window_id（扩展的 already exists 错误里没有）。
      // agent 后续命令只用 session_id，不依赖 window id；需要时用 session list 查。
      if (!isExistingSessionError(error)) throw error;
      result = { session_id: sessionId, reused: true };
    }
    // 扩展层的应答里没有 session id（id 是客户端自己定的），补回去，
    // 否则调用方拿不到后续命令要用的 id。
    return { ...(result as Record<string, unknown>), session_id: sessionId };
  });

  const sessionStop = session.command('stop [session-id]').description('stop a session');
  sessionStop.option('--all', 'stop all sessions');
  addAction(runtime, sessionStop, 'session stop', (positional, local, client, root) => {
    if (local.all === true) return runtime.command('sessionStopAll')(client, {});
    const sessionId = typeof positional[0] === 'string' ? positional[0] : root.session?.trim();
    if (!sessionId) {
      throw new BskUsageError('session stop requires <session-id> (or --session <id>)');
    }
    return runtime.command('sessionStop')(client, { sessionId });
  });

  const sessionList = session.command('list').description('list sessions');
  addAction(runtime, sessionList, 'session list', (_positional, _local, client) =>
    runtime.command('sessionList')(client),
  );

  // ── Tabs ──────────────────────────────────────────────────────────────────
  const tab = program.command('tab').description('manage session tabs');
  const tabList = tab.command('list').description('list tabs');
  tabList.option('--scope <scope>', 'user, agent, or all', 'all');
  addAction(runtime, tabList, 'tab list', (_positional, local, client, root) =>
    runtime.command('tabList')(client, {
      sessionId: requiredSession(root, 'tab list'),
      scope: (local.scope as TabScope | undefined) ?? 'all',
    }),
  );

  const tabCreate = tab.command('create').description('create a tab');
  tabCreate
    .option('--url <url>')
    .option('--no-active', 'create in the background')
    .option('--index <n>', 'tab insertion index', (value: string) => parseInteger(value, '--index'));
  addAction(runtime, tabCreate, 'tab create', (_positional, local, client, root, command) =>
    runtime.command('tabCreate')(client, {
      sessionId: requiredSession(root, 'tab create'),
      url: typeof local.url === 'string' ? local.url : undefined,
      // `--no-active` defaults to `active: true`; only forward an explicit flag.
      active: command.getOptionValueSource('active') === 'cli' ? local.active === true : undefined,
      index: typeof local.index === 'number' ? local.index : undefined,
    }),
  );

  for (const name of ['close', 'select'] as const) {
    const command = tab.command(`${name} [tab-id]`).description(`${name} a tab`);
    addAction(runtime, command, `tab ${name}`, (positional, _local, client, root) =>
      runtime.command(name === 'close' ? 'tabClose' : 'tabSelect')(client, {
        sessionId: requiredSession(root, `tab ${name}`),
        tabId: requiredTabId(positional, root, `tab ${name}`),
      }),
    );
  }

  // borrow / return 是用户标签页 ↔ Agent Window 的往返。borrow 会弹扩展的
  // 确认浮层，等待时间由扩展的 borrow_confirmation 偏好决定（always 时每次
  // 都要用户点确认），--timeout 只决定我们愿意等多久。
  const tabBorrow = tab
    .command('borrow [tab-id]')
    .description('borrow a user tab into the Agent Window (asks the user to confirm)')
    .option('--timeout <duration>', 'how long to wait for the user to confirm', parseTimeout)
    .option('--no-confirm', 'deprecated; the extension decides whether confirmation is required');
  addAction(runtime, tabBorrow, 'tab borrow', (positional, local, client, root, command) => {
    if (command.getOptionValueSource('confirm') === 'cli') {
      // 对齐 Rust 的 warn_legacy_override：这个开关早已无效，但静默吞掉会让人
      // 以为「我关掉确认了」，实际扩展仍会弹浮层。
      runtime.writeErr(
        '--no-confirm is deprecated and has no effect; '
        + 'Automation settings in the browser extension control confirmation\n',
      );
    }
    return runtime.command('tabBorrow')(client, {
      sessionId: requiredSession(root, 'tab borrow'),
      tabId: requiredTabId(positional, root, 'tab borrow'),
      confirmationTimeoutMs: typeof local.timeout === 'number' ? local.timeout : undefined,
    });
  });

  const tabReturn = tab.command('return [tab-id]').description('return a borrowed tab to its origin window');
  addAction(runtime, tabReturn, 'tab return', (positional, _local, client, root) =>
    runtime.command('tabReturn')(client, {
      sessionId: requiredSession(root, 'tab return'),
      tabId: requiredTabId(positional, root, 'tab return'),
    }),
  );

  // ── Browser / device / window commands ────────────────────────────────────
  const browsers = program.command('browsers').description('list connected browsers');
  browsers.option('--wait-for-browser-ms <n>', 'wait for a browser', (value: string) =>
    parseInteger(value, '--wait-for-browser-ms'),
  );
  addAction(runtime, browsers, 'browsers', (_positional, local, client) =>
    runtime.command('browserList')(client, {
      waitForBrowserMs: typeof local.waitForBrowserMs === 'number' ? local.waitForBrowserMs : undefined,
    }),
  );

  const emulate = program.command('emulate').description('override device and viewport properties');
  emulate
    .option('--device <preset>')
    .option('--width <n>', 'viewport width', (value: string) => parseInteger(value, '--width'))
    .option('--height <n>', 'viewport height', (value: string) => parseInteger(value, '--height'))
    .option('--dpr <n>', 'device pixel ratio', (value: string) => parseFiniteNumber(value, '--dpr'))
    .option('--mobile')
    .option('--no-mobile')
    .option('--ua <user-agent>')
    .option('--accept-language <language>')
    .option('--touch')
    .option('--no-touch')
    .option('--max-touch-points <n>', 'maximum touch points', (value: string) => parseInteger(value, '--max-touch-points'))
    .option('--off', 'clear all emulation overrides');
  addAction(runtime, emulate, 'emulate', (_positional, local, client, root, command) => {
    const mobileSource = command.getOptionValueSource('mobile');
    const touchSource = command.getOptionValueSource('touch');
    return runtime.command('emulate')(client, {
      sessionId: requiredSession(root, 'emulate'),
      tabId: root.tabId,
      device: typeof local.device === 'string' ? local.device : undefined,
      width: typeof local.width === 'number' ? local.width : undefined,
      height: typeof local.height === 'number' ? local.height : undefined,
      dpr: typeof local.dpr === 'number' ? local.dpr : undefined,
      mobile: mobileSource === 'cli' && local.mobile === true,
      noMobile: mobileSource === 'cli' && local.mobile === false,
      ua: typeof local.ua === 'string' ? local.ua : undefined,
      acceptLanguage: typeof local.acceptLanguage === 'string' ? local.acceptLanguage : undefined,
      touch: touchSource === 'cli' && local.touch === true,
      noTouch: touchSource === 'cli' && local.touch === false,
      maxTouchPoints: typeof local.maxTouchPoints === 'number' ? local.maxTouchPoints : undefined,
      off: local.off === true,
    });
  });

  const windowCommand = program.command('window').description('manage the agent window');
  const windowResize = windowCommand.command('resize').description('resize the agent window');
  windowResize
    .requiredOption('--width <n>', 'window width', (value: string) => parseInteger(value, '--width'))
    .requiredOption('--height <n>', 'window height', (value: string) => parseInteger(value, '--height'));
  addAction(runtime, windowResize, 'window resize', (_positional, local, client, root) =>
    runtime.command('windowResize')(client, {
      sessionId: requiredSession(root, 'window resize'),
      width: local.width as number,
      height: local.height as number,
    }),
  );

  return { cli: program, runtime };
}

/** Run the standalone CLI and return the process exit code. */
export async function runBskCli(
  argv: readonly string[] = process.argv,
  options: BskCliBuildOptions = {},
): Promise<number> {
  const { cli, runtime } = createBskCli(options);
  const args = hoistRootOptions(argv.slice(2)) as string[];
  try {
    await cli.parseAsync(args, { from: 'user' });
    return 0;
  } catch (error) {
    if (isCommanderControlFlow(error)) return 0;
    const stderr = options.stderr ?? process.stderr;
    // `--json` asks for machine-readable output; the failure has to honour that
    // too, otherwise a caller that only parses JSON gets a bare English line.
    if (args.includes('--json')) {
      stderr.write(`${JSON.stringify({ error: failureEnvelope(error) }, null, 2)}\n`);
    } else {
      stderr.write(formatFailure(error));
    }
    return exitCodeFor(error);
  } finally {
    // Always release the relay socket, including after SIGINT cancellation.
    await runtime.close();
  }
}

function isMainModule(): boolean {
  const entry = process.argv[1];
  if (!entry) return false;
  try {
    return fileURLToPath(import.meta.url) === fileURLToPath(pathToFileURL(entry));
  } catch {
    return false;
  }
}

if (isMainModule()) {
  void runBskCli().then((code) => {
    process.exitCode = code;
  });
}
