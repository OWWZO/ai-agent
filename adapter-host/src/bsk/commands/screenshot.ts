/**
 * `bsk screenshot` (+ full-page capture/read/release).
 * Mirrors `crates/bsk-cli/src/cli/screenshot.rs`, minus filesystem writes —
 * the library returns bytes and the CLI wrapper owns paths.
 *
 * <p>这里同样不写盘：{@link screenshot} / {@link screenshotFullPage} 返回原始结果，
 * 写文件与路径推导由 CLI 包装层（{@code src/bsk-cli.ts}）负责，与 Rust 版的
 * {@code --out} 分工一致。</p>
 */

import { METHODS } from '../methods.js';
import type {
  ScreenshotFullPageParams,
  ScreenshotFullPageResult,
  ScreenshotParams,
  ScreenshotReadParams,
  ScreenshotReadResult,
  ScreenshotReleaseParams,
  ScreenshotReleaseResult,
  ScreenshotResult,
} from '../protocol.js';
import type {
  BskCaller,
  ScreenshotArgs,
  ScreenshotFullPageArgs,
  ScreenshotReadArgs,
  ScreenshotReleaseArgs,
} from './types.js';

const TOOL_TIMEOUT_MS = 35_000;
const FULL_PAGE_DEFAULT_TIMEOUT_MS = 120_000;

export async function screenshot(client: BskCaller, args: ScreenshotArgs): Promise<ScreenshotResult> {
  const params: ScreenshotParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    ...(args.ref === undefined ? {} : { ref: args.ref }),
    ...(args.width === undefined ? {} : { width: args.width }),
    ...(args.height === undefined ? {} : { height: args.height }),
  };
  return client.call<ScreenshotResult>(METHODS.toolScreenshot, params, {
    timeoutMs: TOOL_TIMEOUT_MS,
  });
}

export async function screenshotFullPage(
  client: BskCaller,
  args: ScreenshotFullPageArgs,
): Promise<ScreenshotFullPageResult> {
  const timeoutMs = args.timeoutMs ?? FULL_PAGE_DEFAULT_TIMEOUT_MS;
  const params: ScreenshotFullPageParams = {
    ...(args.scope === undefined ? {} : { scope: args.scope }),
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    timeout_ms: timeoutMs,
  };
  // Rust gives the full-page capture its own budget plus 5s of IPC slack.
  return client.call<ScreenshotFullPageResult>(METHODS.toolScreenshotFullPage, params, {
    timeoutMs: timeoutMs + 5_000,
  });
}

export async function screenshotRead(
  client: BskCaller,
  args: ScreenshotReadArgs,
): Promise<ScreenshotReadResult> {
  const params: ScreenshotReadParams = {
    session_id: args.sessionId,
    capture_id: args.captureId,
    offset: args.offset,
  };
  return client.call<ScreenshotReadResult>(METHODS.toolScreenshotRead, params, {
    timeoutMs: TOOL_TIMEOUT_MS,
  });
}

export async function screenshotRelease(
  client: BskCaller,
  args: ScreenshotReleaseArgs,
): Promise<ScreenshotReleaseResult> {
  const params: ScreenshotReleaseParams = {
    session_id: args.sessionId,
    capture_id: args.captureId,
  };
  return client.call<ScreenshotReleaseResult>(METHODS.toolScreenshotRelease, params, {
    timeoutMs: 5_000,
  });
}

/** Max decoded bytes per `tool.screenshot_read` chunk (256 KiB). */
export const SCREENSHOT_CHUNK_BYTES = 256 * 1024;

/** Upper bound on the base64 text for one chunk (`ceil(256 KiB / 3) * 4`). */
export const SCREENSHOT_CHUNK_BASE64_LIMIT = 349_528;

/**
 * Drain a full-page capture into one buffer. Validates every chunk the same
 * way the Rust CLI does (contiguous offsets, non-empty, exact EOF), then
 * releases the capture so the extension can drop it.
 */
export async function readScreenshotCapture(
  client: BskCaller,
  args: { sessionId: string; captureId: string; byteSize: number },
): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let offset = 0;
  try {
    for (;;) {
      const chunk = await screenshotRead(client, {
        sessionId: args.sessionId,
        captureId: args.captureId,
        offset,
      });
      const bytes = validateScreenshotChunk(chunk, offset, args.byteSize);
      chunks.push(bytes);
      offset = chunk.next_offset;
      if (chunk.eof) break;
    }
  } finally {
    // Release is best-effort: a failed release must not mask a read error.
    await screenshotRelease(client, {
      sessionId: args.sessionId,
      captureId: args.captureId,
    }).catch(() => undefined);
  }
  return Buffer.concat(chunks);
}

export function validateScreenshotChunk(
  chunk: ScreenshotReadResult,
  offset: number,
  total: number,
): Buffer {
  if (chunk.data_base64.length > SCREENSHOT_CHUNK_BASE64_LIMIT) {
    throw new Error('screenshot chunk exceeds 256 KiB');
  }
  const bytes = Buffer.from(chunk.data_base64, 'base64');
  if (
    bytes.length > SCREENSHOT_CHUNK_BYTES ||
    bytes.length === 0 ||
    offset + bytes.length !== chunk.next_offset ||
    chunk.next_offset > total ||
    chunk.eof !== (chunk.next_offset === total)
  ) {
    throw new Error('incomplete or invalid screenshot chunk');
  }
  return bytes;
}
