/**
 * `bsk upload` / `bsk download` / transfer helpers.
 * Mirrors `crates/bsk-cli/src/cli/upload.rs` and `download.rs`.
 *
 * The library takes/returns bytes: staging files, base64 chunking, and
 * atomic disk writes belong to the CLI wrapper.
 */

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import type {
  DownloadParams,
  DownloadResult,
  TransferBeginParams,
  TransferBeginResult,
  TransferChunkParams,
  TransferChunkResult,
  TransferIdParams,
  TransferReadyResult,
  TransferReleaseResult,
  UploadFile,
  UploadParams,
  UploadResult,
} from '../protocol.js';
import { splitTarget } from './args.js';
import type {
  BskCaller,
  DownloadArgs,
  TransferIdArgs,
  UploadArgs,
  UploadFileContent,
} from './types.js';

/** Matches `daemon/file_transfer.rs::MAX_UPLOAD_FILES`. */
export const MAX_UPLOAD_FILES = 20;

const DEFAULT_FILES_TIMEOUT_MS = 120_000;

export interface StagedTransfer {
  transferId: string;
  name: string;
  byteSize: number;
}

/**
 * Stage one file into daemon-owned transfer storage. Ownership passes to the
 * daemon as soon as `transfer.begin` succeeds, so callers must release it on
 * any later failure (`releaseTransfers`).
 */
export async function stageTransfer(
  client: BskCaller,
  args: { sessionId: string; file: UploadFileContent },
): Promise<StagedTransfer> {
  const { sessionId, file } = args;
  if (file.name.length === 0) throw new BskUsageError('upload file has no valid basename');
  const beginParams: TransferBeginParams = {
    session_id: sessionId,
    name: file.name,
    byte_size: file.bytes.byteLength,
  };
  const begin = await client.call<TransferBeginResult>(METHODS.transferBegin, beginParams, {
    timeoutMs: 10_000,
  });
  try {
    let offset = 0;
    while (offset < file.bytes.byteLength) {
      const end = Math.min(offset + begin.chunk_size, file.bytes.byteLength);
      const slice = file.bytes.subarray(offset, end);
      const chunkParams: TransferChunkParams = {
        transfer_id: begin.transfer_id,
        offset,
        data_base64: Buffer.from(slice).toString('base64'),
      };
      const reply = await client.call<TransferChunkResult>(METHODS.transferChunk, chunkParams, {
        timeoutMs: 30_000,
      });
      offset = reply.next_offset;
    }
    await client.call<TransferReadyResult>(
      METHODS.transferFinish,
      { transfer_id: begin.transfer_id } satisfies TransferIdParams,
      { timeoutMs: 10_000 },
    );
    return { transferId: begin.transfer_id, name: file.name, byteSize: file.bytes.byteLength };
  } catch (error) {
    await releaseTransfers(client, [begin.transfer_id]).catch(() => undefined);
    throw error;
  }
}

/** Best-effort release of staged transfers; never throws. */
export async function releaseTransfers(
  client: BskCaller,
  transferIds: readonly string[],
): Promise<void> {
  for (const transferId of transferIds) {
    try {
      await client.call<TransferReleaseResult>(
        METHODS.transferRelease,
        { transfer_id: transferId } satisfies TransferIdParams,
        { timeoutMs: 5_000 },
      );
    } catch {
      // Session teardown is the final cleanup boundary.
    }
  }
}

export async function upload(client: BskCaller, args: UploadArgs): Promise<UploadResult> {
  if (args.files.length === 0) throw new BskUsageError('upload requires at least one file');
  if (args.files.length > MAX_UPLOAD_FILES) {
    throw new BskUsageError(`upload accepts at most ${MAX_UPLOAD_FILES} files`);
  }
  const target = splitTarget(args.target, args.ref, args.selector);
  const timeoutMs = args.timeoutMs ?? DEFAULT_FILES_TIMEOUT_MS;
  const staged: StagedTransfer[] = [];
  let dispatched = false;
  try {
    for (const file of args.files) {
      staged.push(await stageTransfer(client, { sessionId: args.sessionId, file }));
    }
    const files: UploadFile[] = staged.map((entry) => ({
      transfer_id: entry.transferId,
      name: entry.name,
    }));
    const params: UploadParams = {
      session_id: args.sessionId,
      ...(target.ref === undefined ? {} : { ref: target.ref }),
      ...(target.selector === undefined ? {} : { selector: target.selector }),
      ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
      files,
      ...(args.mode === undefined ? {} : { mode: args.mode }),
      timeout_ms: timeoutMs,
    };
    // Once `tool.upload` is dispatched, ownership belongs to the session: a
    // transport timeout cannot prove the file was not attached, so we never
    // release after this point.
    dispatched = true;
    return await client.call<UploadResult>(METHODS.toolUpload, params, {
      timeoutMs: timeoutMs + 5_000,
    });
  } catch (error) {
    if (!dispatched && staged.length > 0) {
      await releaseTransfers(
        client,
        staged.map((entry) => entry.transferId),
      );
    }
    throw error;
  }
}

export async function download(client: BskCaller, args: DownloadArgs): Promise<DownloadResult> {
  const target = splitTarget(args.target, args.ref, args.selector);
  const timeoutMs = args.timeoutMs ?? DEFAULT_FILES_TIMEOUT_MS;
  const params: DownloadParams = {
    session_id: args.sessionId,
    ...(target.ref === undefined ? {} : { ref: target.ref }),
    ...(target.selector === undefined ? {} : { selector: target.selector }),
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    timeout_ms: timeoutMs,
  };
  return client.call<DownloadResult>(METHODS.toolDownload, params, {
    timeoutMs: timeoutMs + 5_000,
  });
}

/** Read a completed download transfer into memory. */
export async function readTransfer(
  client: BskCaller,
  args: TransferIdArgs,
): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let offset = 0;
  for (;;) {
    const chunk = await client.call<TransferChunkResult>(
      METHODS.transferRead,
      { transfer_id: args.transferId, offset, data_base64: '' } satisfies TransferChunkParams,
      { timeoutMs: 30_000 },
    );
    const encoded = chunk.data_base64 ?? '';
    if (encoded.length > 0) chunks.push(Buffer.from(encoded, 'base64'));
    offset = chunk.next_offset;
    if (chunk.eof) break;
  }
  return Buffer.concat(chunks);
}

/** Read a download and release its staging, regardless of read outcome. */
export async function downloadBytes(
  client: BskCaller,
  args: DownloadArgs,
): Promise<{ result: DownloadResult; bytes: Buffer }> {
  const result = await download(client, args);
  if (!result.transfer_id) {
    throw new BskUsageError('daemon returned no download transfer id');
  }
  try {
    const bytes = await readTransfer(client, { transferId: result.transfer_id });
    return { result, bytes };
  } finally {
    await releaseTransfers(client, [result.transfer_id]);
  }
}
