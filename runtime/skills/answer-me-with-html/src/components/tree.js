// Structure tree (figure A): indentation shows hierarchy. A single root with 2–4 children draws an org chart; otherwise an indented list with connectors.
import { mdInline } from '../markdown.js';
import { ComponentError, fields } from './error.js';
import { esc } from '../svg/text.js';

export default {
  name: 'tree',
  summary: 'Hierarchy tree (org chart / indented list)',
  syntax: `\`\`\`tree [list]
Root | subtitle
  Child
    Grandchild | one-line note
  *Highlighted child
\`\`\`
- Indentation (spaces or tabs) sets the level; "label | note" adds a gray note.
- One root with 2 to 4 children → org chart; more children or the list argument → indented list; several roots → side by side.
- Labels support inline Markdown, such as \`Section 1\` Words.`,
  example: '```tree\nASD-STE100 | Simplified Technical English\n  Part 1: Writing rules\n    `Section 1` Words\n  Part 2: Dictionary\n    Approved words | one word, one meaning\n```',
  render(text, { args }) {
    const roots = buildTree(text);
    if (!roots.length) throw new ComponentError('tree needs at least one node', 1);
    const listMode = /\blist\b/.test(args);
    if (roots.length === 1) {
      const [root] = roots;
      const n = root.children.length;
      if (!listMode && n >= 2 && n <= 4) return orgHtml(root);
      return `<div class="am-tree">${rootBox(root, true)}${listHtml(root.children)}</div>`;
    }
    if (!listMode && roots.length <= 4) {
      return `<div class="am-tree"><div class="am-tree-cols am-tree-cols--free" style="--n: ${roots.length}">${roots.map(colHtml).join('')}</div></div>`;
    }
    return `<div class="am-tree">${listHtml(roots)}</div>`;
  },
};

function buildTree(text) {
  const roots = [];
  const stack = [];
  let step = 0;
  for (const raw of String(text).split('\n')) {
    if (!raw.trim()) continue;
    const indent = raw.replace(/\t/g, '  ').match(/^ */)[0].length;
    const node = { ...parseLabel(raw.trim()), indent, step: step++, children: [] };
    while (stack.length && stack.at(-1).indent >= indent) stack.pop();
    (stack.length ? stack.at(-1).children : roots).push(node);
    stack.push(node);
  }
  return roots;
}

function parseLabel(t) {
  const hi = t.startsWith('*');
  const [label, sub = ''] = fields(hi ? t.slice(1) : t);
  return { label, sub, hi };
}

// When a label starts with inline code followed by text (e.g. `Section 1` Words), the code part becomes a grey number tag.
const labelHtml = (label) => mdInline(label).replace(/^<code>([^<]*)<\/code>(?=\s*\S)/, '<span class="am-tree-tag">$1</span>');

// data-key / data-step are for video mode: same-named nodes morph across scenes, appearing step by step by source line.
const vattrs = (n) => ` data-key="${esc(n.label)}" data-step="${n.step}"`;

const boxInner = (n) => `${labelHtml(n.label)}${n.sub ? `<small>${mdInline(n.sub)}</small>` : ''}`;

function rootBox(root, solo = false) {
  return `<div class="am-tree-root${solo ? ' am-tree-root--solo' : ''}"><div class="am-tree-box am-tree-box--root"${vattrs(root)}>${boxInner(root)}</div></div>`;
}

function colHtml(node) {
  const children = node.children.length ? listHtml(node.children) : '';
  return `<div class="am-tree-col"><div class="am-tree-box${node.hi ? ' am-tree-box--hi' : ''}"${vattrs(node)}>${boxInner(node)}</div>${children}</div>`;
}

function orgHtml(root) {
  return `<div class="am-tree">${rootBox(root)}<div class="am-tree-cols" style="--n: ${root.children.length}">${root.children.map(colHtml).join('')}</div></div>`;
}

function listHtml(nodes) {
  return `<ul class="am-tree-list">${nodes.map(liHtml).join('')}</ul>`;
}

function liHtml(n) {
  const sub = n.sub ? `<span class="am-tree-sub">${mdInline(n.sub)}</span>` : '';
  const kids = n.children.length ? `<ul>${n.children.map(liHtml).join('')}</ul>` : '';
  return `<li${n.hi ? ' class="am-tree-hi"' : ''}${vattrs(n)}><span class="am-tree-label">${labelHtml(n.label)}</span>${sub}${kids}</li>`;
}
