import { test } from 'node:test';
import assert from 'node:assert/strict';
import { measure, wrap, esc, isCJK } from '../src/svg/text.js';

test('esc: escapes HTML special characters', () => {
  assert.equal(esc(`<a href="x">&'</a>`), '&lt;a href=&quot;x&quot;&gt;&amp;&#39;&lt;/a&gt;');
  assert.equal(esc(undefined), '');
});

test('isCJK: recognizes CJK characters and fullwidth punctuation', () => {
  assert.ok(isCJK('中'));
  assert.ok(isCJK('，'));
  assert.ok(!isCJK('a'));
});

test('measure: a Chinese character is about as wide as the font size, English is clearly narrower', () => {
  assert.equal(measure('中文', 10), 20);
  const latin = measure('ab', 10);
  assert.ok(latin > 8 && latin < 14, `latin=${latin}`);
  assert.ok(measure('WWW', 10) > measure('iii', 10));
});

test('measure: each Latin character is 0.6em in monospace mode', () => {
  assert.equal(measure('abcd', 10, { mono: true }), 24);
  assert.equal(measure('中', 10, { mono: true }), 10);
});

test('wrap: English wraps by word without splitting words', () => {
  const lines = wrap('the quick brown fox jumps', 60, 10);
  assert.ok(lines.length > 1);
  assert.equal(lines.join(' '), 'the quick brown fox jumps');
  for (const l of lines) assert.ok(!l.startsWith(' ') && !l.endsWith(' '));
});

test('wrap: Chinese wraps by character and no line is too wide', () => {
  const lines = wrap('一二三四五六七八九十', 40, 10);
  assert.deepEqual(lines, ['一二三四', '五六七八', '九十']);
});

test('wrap: an overlong word takes its own line and loses no characters', () => {
  const lines = wrap('supercalifragilistic ok', 50, 10);
  assert.equal(lines[0], 'supercalifragilistic');
  assert.equal(lines[1], 'ok');
});

test('wrap: an empty string returns one empty line', () => {
  assert.deepEqual(wrap('', 50, 10), ['']);
});
