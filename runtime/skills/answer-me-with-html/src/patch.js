// Replace one ## panel in the source draft. page.js readPage recovers the source from the page; the CLI writes back over the original path.

import { parseDoc } from './parse.js';

const ATTR_BLOCK = /\s*\{([^{}]*)\}\s*$/;

export class PatchError extends Error {
  constructor(message) {
    super(message);
    this.name = 'PatchError';
  }
}

export function normalizePanelQuery(query) {
  let q = String(query ?? '').trim();
  if (q.startsWith('##')) q = q.replace(/^##\s*/, '');
  q = q.replace(ATTR_BLOCK, '').trim();
  return q;
}

function panelKeys(panel) {
  const title = panel.title.trim();
  const id = String(panel.id || '').trim();
  const keys = new Set([title]);
  if (id) {
    keys.add(id);
    if (title) keys.add(`${id} ${title}`);
  }
  return keys;
}

export function findPanel(doc, query) {
  const q = normalizePanelQuery(query);
  if (!q) throw new PatchError('Missing the --panel title');
  const matches = doc.panels.filter((p) => panelKeys(p).has(q));
  if (matches.length === 0) throw new PatchError(`No panel titled "${query}"`);
  if (matches.length > 1) throw new PatchError(`The title "${query}" matches more than one panel`);
  return matches[0];
}

function asSinglePanelMarkdown(replacement) {
  const text = String(replacement).replace(/\r\n?/g, '\n');
  if (!text.trim()) throw new PatchError('The new panel draft is empty');
  const looksLikeHeading = /^\s*##\s+/.test(text);
  const doc = parseDoc(looksLikeHeading ? text : `## _\n${text}`);
  if (doc.panels.length !== 1) throw new PatchError('The new panel draft must contain exactly one ## panel');
  return { text, looksLikeHeading };
}

export function replacePanel(source, query, replacement) {
  const doc = parseDoc(source);
  const panel = findPanel(doc, query);
  const idx = doc.panels.indexOf(panel);
  const lines = String(source).replace(/\r\n?/g, '\n').split('\n');
  const start = panel.line - 1;
  const end = doc.panels[idx + 1] ? doc.panels[idx + 1].line - 1 : lines.length;

  const { text, looksLikeHeading } = asSinglePanelMarkdown(replacement);
  const section = looksLikeHeading ? text : `${lines[start]}\n${text.replace(/^\n+/, '')}`;
  const newLines = section.replace(/\n$/, '').split('\n');
  return [...lines.slice(0, start), ...newLines, ...lines.slice(end)].join('\n');
}
