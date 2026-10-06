import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, cpSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('..', import.meta.url));
const PKG = JSON.parse(readFileSync(join(ROOT, 'package.json'), 'utf8'));

// Simulates npx skills add: copy only the skill directory to an isolated place and confirm the bundle needs no repository file.
test('bundle: the skill directory still renders after it is copied out alone', () => {
  const dir = mkdtempSync(join(tmpdir(), 'am-bundle-'));
  try {
    cpSync(join(ROOT, 'skills/answer-me-with-html'), join(dir, 'answer-me-with-html'), { recursive: true });
    const cli = join(dir, 'answer-me-with-html/scripts/am.mjs');
    const env = { ...process.env, AM_NO_OPEN: '1' };

    const version = spawnSync(process.execPath, [cli, '--version'], { encoding: 'utf8', env });
    assert.equal(version.stdout.trim(), PKG.version, 'bundle version must match package.json (forgot npm run build?)');

    const out = join(dir, 'out.html');
    const r = spawnSync(process.execPath, [cli, 'render', '-', '-o', out], {
      input: '---\ntitle: 打包测试\n---\n## A\n```flow\nA -> B\n```\n', encoding: 'utf8', env, cwd: dir,
    });
    assert.equal(r.status, 0, r.stderr);
    const html = readFileSync(out, 'utf8');
    assert.match(html, /<h1>打包测试<\/h1>/);
    assert.match(html, /class="am-node /);
    assert.match(html, /--font-mono/, 'CSS is inlined');

    const patched = spawnSync(process.execPath, [cli, 'patch', out, '--panel', 'A'], {
      input: '## A\n只改这一格。\n', encoding: 'utf8', env, cwd: dir,
    });
    assert.equal(patched.status, 0, patched.stderr);
    assert.match(patched.stdout, new RegExp(out.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    const after = readFileSync(out, 'utf8');
    assert.match(after, /只改这一格/);
    assert.doesNotMatch(after, /class="am-node /);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
