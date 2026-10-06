// SVG layout runs in Node without real font metrics, so widths are estimated by character class.
// Overestimating is safer than underestimating: better spare room in a node than text overflowing its border.

const CJK_RE = /[⺀-鿿가-힯豈-﫿︰-﹏＀-￯　-〿]/;
const NARROW = new Set([...'iljtfrI.,:;|!\'`()[]{}']);
const WIDE = new Set([...'mwMWOQGD@%&']);

// Hiragana, katakana.
export const KANA_RE = /[\u3040-\u30ff]/;
// Japanese is detected by hiragana: Japanese sentences almost always contain hiragana particles and endings (`の`, `は`, `を`, `です`),
// while Chinese quoting foreign words has almost only katakana (e.g. `《ワンピース》`), so katakana cannot mark a text as Japanese.
const HIRAGANA_RE = /[\u3040-\u309f]/;
const HIRAGANA_SHARE = 0.05; // also recognizes short katakana-heavy Japanese titles (`TCP の3ウェイ…`)

export function isJapanese(text) {
  let hira = 0;
  let cjk = 0;
  for (const ch of String(text)) {
    if (HIRAGANA_RE.test(ch)) hira++;
    if (CJK_RE.test(ch)) cjk++;
  }
  return hira > 0 && hira / cjk >= HIRAGANA_SHARE;
}

export function isCJK(ch) {
  return CJK_RE.test(ch);
}

function charWidth(ch, mono) {
  if (isCJK(ch)) return 1;
  if (mono) return 0.6;
  if (ch === ' ') return 0.3;
  if (NARROW.has(ch)) return 0.32;
  if (WIDE.has(ch)) return 0.86;
  if (ch >= 'A' && ch <= 'Z') return 0.68;
  return 0.56;
}

export function measure(str, size = 13, { mono = false } = {}) {
  let units = 0;
  for (const ch of String(str ?? '')) units += charWidth(ch, mono);
  return Math.round(units * size * 100) / 100;
}

// Split into unbreakable layout units: one Han character is a unit, a run of non-space Latin characters is a unit.
function tokenize(str) {
  return String(str).match(/[⺀-鿿가-힯豈-﫿︰-﹏＀-￯　-〿]|[^\s⺀-鿿가-힯豈-﫿︰-﹏＀-￯　-〿]+|\s+/g) ?? [];
}

export function wrap(str, maxWidth, size = 13, opts = {}) {
  const lines = [];
  let line = '';
  for (const tok of tokenize(str)) {
    if (/^\s+$/.test(tok)) {
      if (line) line += ' ';
      continue;
    }
    const candidate = line + tok;
    if (line.trim() && measure(candidate, size, opts) > maxWidth) {
      lines.push(line.trimEnd());
      line = tok;
    } else {
      line = candidate;
    }
  }
  lines.push(line.trimEnd());
  return lines;
}

const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

export function esc(str) {
  return String(str ?? '').replace(/[&<>"']/g, (c) => ESC[c]);
}
