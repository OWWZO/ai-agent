/**
 * 一次性构建脚本：把 library.ts 的组件库固化成三个静态产物。
 *
 *   src/generated/openui/prompt-resident.txt    常驻提示词（注入主 agent system prompt）
 *   src/generated/openui/components-extended.txt 扩展目录（get_ui_components 工具的返回值）
 *   src/generated/openui/spec.json               组件 schema（前端做渲染校验）
 *
 * 运行期不依赖 Node：产物提交进仓库，Java 读 classpath、前端 import json。
 * 改了 library.ts（增删组件、改签名、改 prompt-options）才需要重跑 `pnpm openui:build`。
 */
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { generateSystemPrompt } from '@openuidev/lang-core';

const here = dirname(fileURLToPath(import.meta.url));
const uiRoot = resolve(here, '..');
const libraryPath = join(uiRoot, 'src/lib/openui/library.ts');
const outDir = join(uiRoot, 'src/generated/openui');

/** 常驻组件：覆盖 90% 的界面需求，剩下的靠 get_ui_components 按需取。 */
const CORE_COMPONENTS = [
  'Col', 'Row', 'Box', 'Card', 'Grid',
  'Title', 'Text', 'Caption', 'Badge',
  'Button', 'Table', 'Chart', 'Input', 'Slider', 'Form',
];

/** 常驻 @ 函数：列表/数值/文本/日期四类里最常用的。@Set/@Reset 在 Action 段已全文收录。 */
const CORE_FUNCTIONS = [
  '@Each', '@Default', '@Clamp', '@Round', '@Format', '@Join', '@Plural',
  '@Count', '@Sum', '@Avg', '@Percent', '@Filter', '@Toggle', '@Includes',
  '@Append', '@Remove', '@Range', '@Today', '@FormatDate',
];

/** Final Verification 里必须保留的行为规范（按子串锚定，上游改文案时会打警告而不是静默丢失）。 */
const KEPT_RULES = [
  'Answer with a working tool',
  '@Random, @Pick and @Shuffle',
  'Use @ToAssistant only for what the page cannot compute',
  'To change a page from an earlier answer',
  'Chart data values must be bare numbers',
];

// 这段会被追加到主 agent 的 system prompt 末尾，所以不能自称角色（原版第一句
// "Your ENTIRE response must be valid openui-lang" 会把 markdown 能力一并接管）。
const RESIDENT_OPENING = `When the user needs an interactive interface — a dashboard, chart, calculator, checklist, form, how-to demo or small game — answer with an openui-lang program inside marker lines; for everything else answer with normal markdown. Prefer this over writing a standalone HTML page when the interface should live inside the conversation.

]]>openui:content
root = Col([head])
head = Title("Title", "xl")
]]>openui:end

Everything inside the markers must be valid openui-lang: no markdown, no prose, one statement per line, \`root\` first. Everything outside the markers is normal markdown for the user. At most one marker block per reply, never inside a code fence.`;

function sha12(file) {
  return createHash('sha256').update(readFileSync(file)).digest('hex').slice(0, 12);
}

/**
 * 调用 openui CLI。
 *
 * CLI 每个命令（含 generate）都会往 PostHog 发一条匿名 cli_invoked（版本/OS/命令名，
 * 不含代码与提示词）。构建步骤没必要上报，这里双重关闭：`--no-telemetry` 全局参数 +
 * `DO_NOT_TRACK=1` 环境变量（CLI 里两个开关任一命中即禁用）。
 */
function runCli(args, label) {
  const require = createRequire(join(uiRoot, 'package.json'));
  const cliPkgPath = require.resolve('@openuidev/cli/package.json');
  const binJs = join(dirname(cliPkgPath), 'dist/index.js');
  try {
    execFileSync(process.execPath, [binJs, '--no-telemetry', ...args], {
      cwd: uiRoot,
      stdio: 'pipe',
      env: { ...process.env, DO_NOT_TRACK: '1' },
    });
  } catch (error) {
    const stderr = error.stderr?.toString() ?? '';
    throw new Error(`${label} 失败：\n${stderr || error.message}`);
  }
}

/** 按 `## ` 标题切 section，第一段是开场（title 为 null）。 */
function splitSections(text) {
  const sections = [];
  let current = { title: null, lines: [] };
  for (const line of text.split('\n')) {
    const match = /^## (.+)$/.exec(line);
    if (match) {
      sections.push(current);
      current = { title: match[1].trim(), lines: [] };
    } else {
      current.lines.push(line);
    }
  }
  sections.push(current);
  return sections;
}

function sectionBody(sections, titlePrefix) {
  const found = sections.find((section) => section.title?.startsWith(titlePrefix));
  if (!found) throw new Error(`prompt 里找不到 section: ${titlePrefix}`);
  return found.lines;
}

function trimBlankLines(lines) {
  const out = [...lines];
  while (out.length && !out[0].trim()) out.shift();
  while (out.length && !out[out.length - 1].trim()) out.pop();
  return out;
}

/** 组件段按 `### Group` 分组；返回组名、签名行（含描述）与组级说明。 */
function splitComponentGroups(lines) {
  const groups = [];
  let current = { name: null, signatures: [], notes: [], intro: [] };
  for (const line of lines) {
    const heading = /^### (.+)$/.exec(line);
    if (heading) {
      groups.push(current);
      current = { name: heading[1].trim(), signatures: [], notes: [], intro: [] };
      continue;
    }
    if (/^[A-Z][A-Za-z0-9]*\(/.test(line)) current.signatures.push(line);
    else if (line.startsWith('- ')) current.notes.push(line);
    else if (line.trim()) current.intro.push(line);
  }
  groups.push(current);
  return groups.filter((group) => group.name);
}

const signatureName = (line) => line.slice(0, line.indexOf('('));
const stripDescription = (line) => line.split(' — ')[0];

function buildResident(sections, hash) {
  const componentGroups = splitComponentGroups(sectionBody(sections, 'Component Signatures'));
  const byName = new Map();
  for (const group of componentGroups) {
    for (const signature of group.signatures) byName.set(signatureName(signature), signature);
  }
  const missing = CORE_COMPONENTS.filter((name) => !byName.has(name));
  if (missing.length) throw new Error(`常驻组件在库里不存在: ${missing.join(', ')}`);

  const functionLines = sectionBody(sections, 'Built-in Functions').filter((line) => line.startsWith('@'));
  const functionByName = new Map(functionLines.map((line) => [line.split('(')[0], line]));
  const missingFn = CORE_FUNCTIONS.filter((name) => !functionByName.has(name));
  if (missingFn.length) throw new Error(`常驻函数在库里不存在: ${missingFn.join(', ')}`);

  const exampleBody = trimBlankLines(sectionBody(sections, 'Examples'));
  const exampleOne = [];
  for (const line of exampleBody) {
    if (/^Example 2:/.test(line)) break;
    exampleOne.push(line);
  }

  const verification = trimBlankLines(sectionBody(sections, 'Final Verification'));
  const checks = verification.filter((line) => /^\d+\. /.test(line));
  const rules = KEPT_RULES.map((anchor) => {
    const found = verification.find((line) => line.includes(anchor));
    if (!found) console.warn(`[openui] 警告：Final Verification 里找不到「${anchor}」，已跳过`);
    return found;
  }).filter(Boolean);

  const parts = [
    `# generated from library.ts@${hash} — regenerate with npm run openui:build`,
    '',
    RESIDENT_OPENING,
    '',
    '## Syntax Rules',
    '',
    ...trimBlankLines(sectionBody(sections, 'Syntax Rules')),
    '',
    '## Component Signatures (core)',
    '',
    'Arguments marked with ? are optional and positional — order matters, not names.',
    'Props typed `ActionExpression` take one @step or Action([...]). Props marked `$binding<type>` take a `$variable`.',
    '',
    ...CORE_COMPONENTS.map((name) => stripDescription(byName.get(name))),
    '',
    '## Built-in Functions (core)',
    '',
    'Use these instead of hardcoding computed values.',
    '',
    ...CORE_FUNCTIONS.map((name) => stripDescription(functionByName.get(name))),
    '',
    '## Action — Button Behavior',
    '',
    ...trimBlankLines(sectionBody(sections, 'Action')),
    '',
    '## Hoisting & Streaming',
    '',
    ...trimBlankLines(sectionBody(sections, 'Hoisting')),
    '',
    '## Example',
    '',
    ...trimBlankLines(exampleOne),
    '',
    '## Before you finish',
    '',
    ...checks,
    '',
    ...rules,
    '',
    'Components or functions beyond the lists above exist: call the `get_ui_components` tool before using them.',
    '',
  ];
  return parts.join('\n');
}

function buildExtended(sections, hash) {
  const componentGroups = splitComponentGroups(sectionBody(sections, 'Component Signatures'));
  const functionLines = sectionBody(sections, 'Built-in Functions').filter((line) => line.startsWith('@'));
  const restFunctions = functionLines.filter((line) => !CORE_FUNCTIONS.includes(line.split('(')[0]));

  const parts = [
    `# generated from library.ts@${hash} — regenerate with npm run openui:build`,
    '',
    'Additional components and @functions beyond the core set already in your system prompt.',
    'Same syntax as the core set: positional arguments, `?` marks optional ones.',
    '',
    '## Components',
  ];

  for (const group of componentGroups) {
    const rest = group.signatures.filter((line) => !CORE_COMPONENTS.includes(signatureName(line)));
    if (!rest.length) continue;
    parts.push('', `### ${group.name}`, ...group.intro.map((line) => `(${line})`), '', ...rest, ...group.notes);
  }

  parts.push('', '## Functions', '', ...restFunctions, '');
  return parts.join('\n');
}

function writeArtifact(name, content) {
  const file = join(outDir, name);
  writeFileSync(file, content, 'utf8');
  const bytes = Buffer.byteLength(content, 'utf8');
  console.log(`  ${name.padEnd(24)} ${String(bytes).padStart(7)} B  ~${Math.round(bytes / 4)} tokens`);
  return bytes;
}

/**
 * CI 防漂移：产物头部记录了生成时的 library.ts hash，改了库却没重新生成时直接报错。
 * `pnpm openui:check`
 */
function checkFreshness() {
  const expected = sha12(libraryPath);
  const file = join(outDir, 'prompt-resident.txt');
  let firstLine = '';
  try {
    firstLine = readFileSync(file, 'utf8').split('\n', 1)[0];
  } catch {
    throw new Error(`缺少产物 ${file}，先跑 pnpm openui:build`);
  }
  const recorded = /library\.ts@([0-9a-f]{12})/.exec(firstLine)?.[1];
  if (recorded !== expected) {
    throw new Error(
      `openui 产物已过期：library.ts 现在是 @${expected}，产物是 @${recorded ?? 'unknown'}。跑 pnpm openui:build 并同步到 Java resources。`
    );
  }
  console.log(`[openui] 产物与 library.ts@${expected} 一致`);
}

function main() {
  if (process.argv.includes('--check')) {
    checkFreshness();
    return;
  }
  mkdirSync(outDir, { recursive: true });
  const hash = sha12(libraryPath);

  console.log('[openui] 生成 spec.json …');
  runCli(['generate', 'src/lib/openui/library.ts', '--spec', '--out', 'src/generated/openui/spec.json'], 'spec.json');

  console.log('[openui] 生成全量 system prompt …');
  const tmp = mkdtempSync(join(tmpdir(), 'openui-prompt-'));
  const promptFile = join(tmp, 'system-prompt.txt');
  try {
    runCli(
      ['generate', 'src/lib/openui/library.ts', '--out', promptFile, '--prompt-options', 'promptOptions'],
      'system prompt'
    );
    const sections = splitSections(readFileSync(promptFile, 'utf8'));
    const resident = buildResident(sections, hash);
    const extended = buildExtended(sections, hash);

    console.log(`[openui] 产物（library.ts@${hash}）：`);
    const residentBytes = writeArtifact('prompt-resident.txt', resident);
    writeArtifact('components-extended.txt', extended);
    const specBytes = readFileSync(join(outDir, 'spec.json')).byteLength;
    console.log(`  ${'spec.json'.padEnd(24)} ${String(specBytes).padStart(7)} B`);

    // Autofix 只认 cloud 形态的 config block（`]]>openui:config`），它不属于对话上下文、
    // 不占 8k 限制，但必须和 spec 同源，否则修出来的程序用的是旧组件库。
    console.log('[openui] 生成 Autofix 用的 cloud config block …');
    const spec = JSON.parse(readFileSync(join(outDir, 'spec.json'), 'utf8'));
    const cloudBlock = generateSystemPrompt({ cloud: true, library: spec });
    writeArtifact('openui-cloud-config.txt', `${cloudBlock.trim()}\n`);

    // 常驻提示词直接进主 agent 的上下文，超了会挤压其他指令；这里设一条硬上限防止悄悄膨胀。
    // 2026-10-18：新增 chart 数据卫生规则后从 10KB 放宽到 10.5KB（system prompt 预算仍远低于注入的 24KB 组件目录）。
    const LIMIT = 10.5 * 1024;
    if (residentBytes > LIMIT) {
      throw new Error(`prompt-resident.txt ${residentBytes}B 超过上限 ${LIMIT}B，请裁剪常驻组件/函数清单`);
    }
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
}

main();
