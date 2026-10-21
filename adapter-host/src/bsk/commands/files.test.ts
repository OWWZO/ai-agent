import { describe, expect, it } from 'vitest';

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import {
  MAX_UPLOAD_FILES,
  download,
  downloadBytes,
  readTransfer,
  releaseTransfers,
  stageTransfer,
  upload,
} from './files.js';
import { RecordingCaller } from './test-support.js';

const bytes = (text: string): Uint8Array => new TextEncoder().encode(text);

describe('stageTransfer', () => {
  it('chunks by the daemon chunk_size and finishes', async () => {
    const client = new RecordingCaller()
      .reply(METHODS.transferBegin, { transfer_id: 'tr-1', chunk_size: 3 })
      .reply(METHODS.transferChunk, (params: unknown) => {
        const { offset } = params as { offset: number };
        return { next_offset: Math.min(offset + 3, 7), eof: false };
      })
      .reply(METHODS.transferFinish, { transfer_id: 'tr-1', byte_size: 7 });
    const staged = await stageTransfer(client, {
      sessionId: 's1',
      file: { name: 'a.txt', bytes: bytes('abcdefg') },
    });
    expect(staged).toEqual({ transferId: 'tr-1', name: 'a.txt', byteSize: 7 });
    expect(client.last(METHODS.transferBegin).params).toEqual({
      session_id: 's1',
      name: 'a.txt',
      byte_size: 7,
    });
    const chunks = client.callsOf(METHODS.transferChunk).map((call) => call.params);
    expect(chunks).toEqual([
      { transfer_id: 'tr-1', offset: 0, data_base64: Buffer.from('abc').toString('base64') },
      { transfer_id: 'tr-1', offset: 3, data_base64: Buffer.from('def').toString('base64') },
      { transfer_id: 'tr-1', offset: 6, data_base64: Buffer.from('g').toString('base64') },
    ]);
  });

  it('releases staging when a chunk fails', async () => {
    const client = new RecordingCaller()
      .reply(METHODS.transferBegin, { transfer_id: 'tr-1', chunk_size: 3 })
      .reply(METHODS.transferChunk, new Error('boom'))
      .reply(METHODS.transferRelease, { released: true });
    await expect(
      stageTransfer(client, { sessionId: 's1', file: { name: 'a.txt', bytes: bytes('abc') } }),
    ).rejects.toThrow('boom');
    expect(client.last(METHODS.transferRelease).params).toEqual({ transfer_id: 'tr-1' });
  });

  it('rejects a file without a basename', async () => {
    await expect(
      stageTransfer(new RecordingCaller(), { sessionId: 's1', file: { name: '', bytes: bytes('') } }),
    ).rejects.toThrow(BskUsageError);
  });
});

describe('releaseTransfers', () => {
  it('swallows per-transfer failures', async () => {
    const client = new RecordingCaller().reply(METHODS.transferRelease, new Error('gone'));
    await expect(releaseTransfers(client, ['a', 'b'])).resolves.toBeUndefined();
    expect(client.callsOf(METHODS.transferRelease)).toHaveLength(2);
  });
});

describe('upload', () => {
  it('stages every file then dispatches tool.upload with opaque ids', async () => {
    const client = new RecordingCaller()
      .reply(METHODS.transferBegin, (params: unknown) => {
        const { name } = params as { name: string };
        return { transfer_id: `tr-${name}`, chunk_size: 64 };
      })
      .reply(METHODS.transferChunk, { next_offset: 1, eof: true })
      .reply(METHODS.transferFinish, {})
      .reply(METHODS.toolUpload, { tab_id: 1, file_names: ['a.txt', 'b.txt'] });
    const result = await upload(client, {
      sessionId: 's1',
      target: '@e5',
      files: [
        { name: 'a.txt', bytes: bytes('a') },
        { name: 'b.txt', bytes: bytes('b') },
      ],
      mode: 'drop',
    });
    expect(result.file_names).toEqual(['a.txt', 'b.txt']);
    expect(client.last(METHODS.toolUpload).params).toEqual({
      session_id: 's1',
      ref: '@e5',
      files: [
        { transfer_id: 'tr-a.txt', name: 'a.txt' },
        { transfer_id: 'tr-b.txt', name: 'b.txt' },
      ],
      mode: 'drop',
      timeout_ms: 120_000,
    });
  });

  it('releases earlier staging when a later file fails before dispatch', async () => {
    let begins = 0;
    const client = new RecordingCaller()
      .reply(METHODS.transferBegin, () => {
        begins += 1;
        if (begins === 2) throw new Error('begin failed');
        return { transfer_id: 'tr-1', chunk_size: 64 };
      })
      .reply(METHODS.transferChunk, { next_offset: 1, eof: true })
      .reply(METHODS.transferFinish, {})
      .reply(METHODS.transferRelease, { released: true });
    await expect(
      upload(client, {
        sessionId: 's1',
        target: '@e5',
        files: [
          { name: 'a.txt', bytes: bytes('a') },
          { name: 'b.txt', bytes: bytes('b') },
        ],
      }),
    ).rejects.toThrow('begin failed');
    expect(client.callsOf(METHODS.toolUpload)).toHaveLength(0);
    expect(client.callsOf(METHODS.transferRelease).map((c) => c.params)).toEqual([
      { transfer_id: 'tr-1' },
    ]);
  });

  it('enforces the file-count cap', async () => {
    const files = Array.from({ length: MAX_UPLOAD_FILES + 1 }, (_, i) => ({
      name: `f${i}.txt`,
      bytes: bytes('x'),
    }));
    await expect(
      upload(new RecordingCaller(), { sessionId: 's1', target: '@e1', files }),
    ).rejects.toThrow(/at most 20 files/);
  });
});

describe('download', () => {
  it('maps target and timeout', async () => {
    const client = new RecordingCaller().reply(METHODS.toolDownload, {});
    await download(client, { sessionId: 's1', target: '#export' });
    expect(client.last(METHODS.toolDownload).params).toEqual({
      session_id: 's1',
      selector: '#export',
      timeout_ms: 120_000,
    });
  });

  it('readTransfer drains chunks and downloadBytes releases staging', async () => {
    const reader = new RecordingCaller()
      .reply(METHODS.transferRead, (params: unknown) => {
        const { offset } = params as { offset: number };
        return offset === 0
          ? { next_offset: 2, eof: false, data_base64: Buffer.from('ab').toString('base64') }
          : { next_offset: 4, eof: true, data_base64: Buffer.from('cd').toString('base64') };
      })
      .reply(METHODS.transferRelease, { released: true });
    expect((await readTransfer(reader, { transferId: 'tr' })).toString()).toBe('abcd');

    const client = new RecordingCaller()
      .reply(METHODS.toolDownload, { transfer_id: 'tr-9' })
      .reply(METHODS.transferRead, { next_offset: 3, eof: true, data_base64: 'YWJj' })
      .reply(METHODS.transferRelease, { released: true });
    const { bytes: payload } = await downloadBytes(client, { sessionId: 's1', target: '@e1' });
    expect(payload.toString()).toBe('abc');
    expect(client.last(METHODS.transferRelease).params).toEqual({ transfer_id: 'tr-9' });
  });

  it('fails when the daemon returns no transfer id', async () => {
    const client = new RecordingCaller().reply(METHODS.toolDownload, {});
    await expect(downloadBytes(client, { sessionId: 's1', target: '@e1' })).rejects.toThrow(
      /no download transfer id/,
    );
  });
});
