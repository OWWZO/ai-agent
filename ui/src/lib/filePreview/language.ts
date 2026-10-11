import type { BundledLanguage } from "shiki";

/**
 * 扩展名 → shiki 语言标识。
 *
 * 这里是「文件扩展名」到「高亮语言」的唯一映射表。渲染器只负责把语言标识交给
 * shiki，未知语言由 shiki 自身抛错后回退到纯文本（见 SourceCodeView / KimiCodeFence）。
 */
export const LANGUAGE_BY_EXTENSION: Readonly<Record<string, string>> = {
  // JS 家族
  js: "javascript",
  mjs: "javascript",
  cjs: "javascript",
  jsx: "jsx",
  ts: "typescript",
  mts: "typescript",
  cts: "typescript",
  tsx: "tsx",
  vue: "vue",
  svelte: "svelte",
  // 后端 / 系统语言
  py: "python",
  rb: "ruby",
  rs: "rust",
  go: "go",
  java: "java",
  kt: "kotlin",
  kts: "kotlin",
  scala: "scala",
  swift: "swift",
  php: "php",
  cs: "csharp",
  dart: "dart",
  lua: "lua",
  pl: "perl",
  r: "r",
  // C 家族
  c: "c",
  h: "c",
  cpp: "cpp",
  cc: "cpp",
  cxx: "cpp",
  hpp: "cpp",
  hh: "cpp",
  // 标记 / 样式
  html: "html",
  htm: "html",
  xml: "xml",
  svg: "xml",
  css: "css",
  scss: "scss",
  sass: "sass",
  less: "less",
  // 数据 / 配置
  json: "json",
  jsonc: "jsonc",
  json5: "json5",
  yml: "yaml",
  yaml: "yaml",
  toml: "toml",
  ini: "ini",
  env: "dotenv",
  properties: "properties",
  // 脚本 / 构建
  sh: "bash",
  bash: "bash",
  zsh: "bash",
  fish: "fish",
  ps1: "powershell",
  bat: "bat",
  cmd: "bat",
  gradle: "groovy",
  groovy: "groovy",
  dockerfile: "docker",
  makefile: "make",
  mk: "make",
  cmake: "cmake",
  // 文档 / 数据
  md: "markdown",
  markdown: "markdown",
  mdx: "mdx",
  tex: "latex",
  sql: "sql",
  csv: "csv",
  tsv: "csv",
  log: "log",
  diff: "diff",
  patch: "diff",
  mermaid: "mermaid",
  mmd: "mermaid",
  txt: "text",
  text: "text",
};

/** 显式已知的代码扩展名集合（用于区分 code / text 两种 kind）。 */
export const CODE_EXTENSIONS: ReadonlySet<string> = new Set(
  Object.keys(LANGUAGE_BY_EXTENSION)
);

/** 去掉前导点、转小写、trim。 */
export function normalizeExtension(value?: string | null): string {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/^\./, "");
}

/** 从文件名 / 路径 / URL 中取出扩展名（不含点）。 */
export function extractExtension(source?: string | null): string {
  const raw = String(source ?? "").trim();
  if (!raw) return "";
  // 去掉 query / hash，避免 `report.pdf?v=2` 取到 "pdf?v=2"
  const withoutQuery = raw.split(/[?#]/)[0];
  const leaf = withoutQuery.replace(/\\/g, "/").split("/").pop() ?? "";
  const dotIndex = leaf.lastIndexOf(".");
  if (dotIndex <= 0 || dotIndex === leaf.length - 1) return "";
  return normalizeExtension(leaf.slice(dotIndex + 1));
}

/** 扩展名是否属于已知代码语言。 */
export function isKnownCodeExtension(extension: string): boolean {
  return CODE_EXTENSIONS.has(normalizeExtension(extension));
}

/**
 * 解析高亮语言：接受扩展名或完整文件名。
 * 未知扩展名统一回退到 `text`，由 shiki 侧保证不会因语言缺失而渲染失败。
 */
export function resolveCodeLanguage(extensionOrName?: string | null): BundledLanguage {
  const ext = normalizeExtension(extensionOrName).includes(".")
    ? extractExtension(extensionOrName)
    : normalizeExtension(extensionOrName);
  const language = LANGUAGE_BY_EXTENSION[ext] || "text";
  return language as BundledLanguage;
}
