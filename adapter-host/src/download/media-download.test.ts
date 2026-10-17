import * as fs from 'node:fs';
import * as http from 'node:http';
import * as os from 'node:os';
import * as path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { downloadMedia } from './media-download.js';

const servers: http.Server[] = [];
const tempDirs: string[] = [];

afterEach(async () => {
  await Promise.all(servers.map((server) => new Promise<void>((resolve, reject) => {
    server.close((err) => (err ? reject(err) : resolve()));
  })));
  servers.length = 0;

  for (const dir of tempDirs) {
    try { fs.rmSync(dir, { recursive: true, force: true }); } catch { /* ignore */ }
  }
  tempDirs.length = 0;
});

async function startServer(handler: http.RequestListener): Promise<string> {
  const server = http.createServer(handler);
  servers.push(server);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  if (!address || typeof address === 'string') {
    throw new Error('Failed to start test server');
  }
  return `http://127.0.0.1:${address.port}`;
}

// Windows runners occasionally exceed the default 5s timeout on the first
// http.createServer + downloadMedia roundtrip (cold-start cost on a loaded VM).
describe('media downloads', { retry: process.platform === 'win32' ? 2 : 0 }, () => {
  it('keeps custom filenames inside the output directory', async () => {
    const baseUrl = await startServer((_req, res) => {
      res.statusCode = 200;
      res.end('image');
    });
    const parentDir = await fs.promises.mkdtemp(path.join(os.tmpdir(), 'opencli-media-parent-'));
    tempDirs.push(parentDir);
    const outputDir = path.join(parentDir, 'downloads');

    const results = await downloadMedia([
      { type: 'image', url: `${baseUrl}/image.jpg`, filename: '../escape.jpg' },
    ], {
      output: outputDir,
      verbose: false,
    });

    expect(results.files).toEqual([
      { index: 1, type: 'image', status: 'success', size: '5.0 B' },
    ]);
    expect(fs.readFileSync(path.join(outputDir, 'escape.jpg'), 'utf8')).toBe('image');
    expect(fs.existsSync(path.join(parentDir, 'escape.jpg'))).toBe(false);
  });

  it('reports artifact candidates relative to the cli working directory', async () => {
    const baseUrl = await startServer((_req, res) => {
      res.statusCode = 200;
      res.end('image-bytes');
    });
    const parentDir = await fs.promises.mkdtemp(path.join(os.tmpdir(), 'opencli-media-cwd-'));
    tempDirs.push(parentDir);
    const outputDir = path.join(parentDir, 'downloads', 'note-1');
    const previousCwd = process.cwd();

    try {
      process.chdir(parentDir);
      const results = await downloadMedia([
        { type: 'image', url: `${baseUrl}/a.jpg` },
        { type: 'image', url: `${baseUrl}/b.jpg` },
      ], {
        output: 'downloads',
        subdir: 'note-1',
        filenamePrefix: 'note-1',
        verbose: false,
      });

      // Contract: forward slashes, workspace-relative, no absolute path, no ..
      expect(results.artifacts).toEqual([
        { relativePath: 'downloads/note-1/note-1_1.jpg', size: 11, mime: 'image/jpeg' },
        { relativePath: 'downloads/note-1/note-1_2.jpg', size: 11, mime: 'image/jpeg' },
      ]);
      for (const candidate of results.artifacts ?? []) {
        expect(candidate.relativePath.includes('\\')).toBe(false);
        expect(candidate.relativePath.startsWith('/')).toBe(false);
        expect(candidate.relativePath.includes('..')).toBe(false);
        expect(fs.existsSync(path.join(parentDir, candidate.relativePath))).toBe(true);
      }
    } finally {
      process.chdir(previousCwd);
    }
  });

  it('omits the artifacts key when nothing was downloaded', async () => {
    const parentDir = await fs.promises.mkdtemp(path.join(os.tmpdir(), 'opencli-media-empty-'));
    tempDirs.push(parentDir);

    const results = await downloadMedia([], {
      output: path.join(parentDir, 'downloads'),
      verbose: false,
    });

    expect(results.files).toEqual([
      { index: 0, type: '-', status: 'failed', size: 'No media found' },
    ]);
    expect(results.artifacts).toBeUndefined();
    expect('artifacts' in results).toBe(false);
  });

  it('strips nested and absolute path components from custom filenames', async () => {
    const baseUrl = await startServer((_req, res) => {
      res.statusCode = 200;
      res.end('image');
    });
    const parentDir = await fs.promises.mkdtemp(path.join(os.tmpdir(), 'opencli-media-parent-'));
    tempDirs.push(parentDir);
    const outputDir = path.join(parentDir, 'downloads');

    const results = await downloadMedia([
      { type: 'image', url: `${baseUrl}/one.jpg`, filename: '../../etc/passwd' },
      { type: 'image', url: `${baseUrl}/two.jpg`, filename: '/tmp/evil.jpg' },
      { type: 'image', url: `${baseUrl}/three.jpg`, filename: 'nested/path/photo.png' },
      { type: 'image', url: `${baseUrl}/four.jpg`, filename: '..\\windows\\escape.jpg' },
    ], {
      output: outputDir,
      verbose: false,
    });

    expect(results.files).toEqual([
      { index: 1, type: 'image', status: 'success', size: '5.0 B' },
      { index: 2, type: 'image', status: 'success', size: '5.0 B' },
      { index: 3, type: 'image', status: 'success', size: '5.0 B' },
      { index: 4, type: 'image', status: 'success', size: '5.0 B' },
    ]);
    expect(fs.readFileSync(path.join(outputDir, 'passwd'), 'utf8')).toBe('image');
    expect(fs.readFileSync(path.join(outputDir, 'evil.jpg'), 'utf8')).toBe('image');
    expect(fs.readFileSync(path.join(outputDir, 'photo.png'), 'utf8')).toBe('image');
    expect(fs.readFileSync(path.join(outputDir, 'escape.jpg'), 'utf8')).toBe('image');
    expect(fs.existsSync(path.join(parentDir, 'etc', 'passwd'))).toBe(false);
    expect(fs.existsSync(path.join(parentDir, 'tmp', 'evil.jpg'))).toBe(false);
  });

  it('falls back to generated names for empty dot-directory filenames', async () => {
    const baseUrl = await startServer((_req, res) => {
      res.statusCode = 200;
      res.end('image');
    });
    const parentDir = await fs.promises.mkdtemp(path.join(os.tmpdir(), 'opencli-media-parent-'));
    tempDirs.push(parentDir);
    const outputDir = path.join(parentDir, 'downloads');

    const results = await downloadMedia([
      { type: 'image', url: `${baseUrl}/dot.jpg`, filename: '.' },
      { type: 'image', url: `${baseUrl}/dotdot.jpg`, filename: '..' },
      { type: 'image', url: `${baseUrl}/blank.jpg`, filename: '   ' },
    ], {
      output: outputDir,
      filenamePrefix: '../unsafe prefix',
      verbose: false,
    });

    expect(results.files).toEqual([
      { index: 1, type: 'image', status: 'success', size: '5.0 B' },
      { index: 2, type: 'image', status: 'success', size: '5.0 B' },
      { index: 3, type: 'image', status: 'success', size: '5.0 B' },
    ]);
    expect(fs.readFileSync(path.join(outputDir, 'unsafe_prefix_1.jpg'), 'utf8')).toBe('image');
    expect(fs.readFileSync(path.join(outputDir, 'unsafe_prefix_2.jpg'), 'utf8')).toBe('image');
    expect(fs.readFileSync(path.join(outputDir, 'unsafe_prefix_3.jpg'), 'utf8')).toBe('image');
    expect(fs.existsSync(path.join(parentDir, 'unsafe prefix_1.jpg'))).toBe(false);
  });
});
