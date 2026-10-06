#!/usr/bin/env node
// Builds the demo video: docs/demo/demo.mp4 (1920×1080, music cut to the beat) and demo.gif (960 wide, silent).
// Usage: node docs/demo/make-demo.mjs <frame directory>
// The frame directory holds frame-0000.jpg …: open demo.html in a browser (served over HTTP together with the rendered tcp.html,
// URL parameters from the data in timeline.json), wait for window.ready, then call window.render(t) for t = i / fps and take a screenshot each time.
// Needs ffmpeg. music.mjs synthesizes the music on the spot; the beat cuts come from the cuts in timeline.json.
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join, resolve } from 'node:path';

const here = fileURLToPath(new URL('.', import.meta.url));
const dir = process.argv[2] && resolve(process.argv[2]);
if (!dir) {
  process.stderr.write('Usage: node docs/demo/make-demo.mjs <frame directory>\n');
  process.exit(2);
}
const tl = JSON.parse(readFileSync(join(here, 'timeline.json'), 'utf8'));

const run = (cmd, args) => {
  const r = spawnSync(cmd, args, { stdio: 'inherit' });
  if (r.status !== 0) process.exit(r.status ?? 1);
};
const ff = (args) => run('ffmpeg', ['-y', '-loglevel', 'error', ...args]);

const music = join(dir, 'music.wav');
run(process.execPath, [join(here, 'music.mjs'), music, String(tl.total), tl.cuts.join(',')]);

ff(['-framerate', String(tl.fps), '-i', join(dir, 'frame-%04d.jpg'), '-i', music,
  '-vf', 'format=yuv420p', '-c:v', 'libx264', '-crf', '20', '-preset', 'slow',
  '-c:a', 'aac', '-b:a', '160k', '-t', String(tl.total), '-movflags', '+faststart', join(here, 'demo.mp4')]);
ff(['-i', join(here, 'demo.mp4'), '-filter_complex',
  'fps=10,scale=800:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle',
  '-loop', '0', join(here, 'demo.gif')]);
process.stdout.write(`✓ ${join(here, 'demo.mp4')}\n✓ ${join(here, 'demo.gif')}\n`);
