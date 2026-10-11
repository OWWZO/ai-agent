import type { BundledTheme } from "shiki";

/**
 * 代码预览设置。
 *
 * 与 ZCode `lib/codePreviewSettings.ts` 对齐，但**不提供设置页**：
 * 默认值集中在这里，可变的偏好（如 `wrapLongLines`）另由
 * `lib/codePreviewWrap.ts` 这个模块级 store 承载并持久化。
 * 保留这个模块的意义是让「默认值」有唯一出口，后续要加设置页时不必改渲染器。
 *
 * ⚠️ `wrapLongLines` 默认 `true`（本项目明确要求「文件预览默认开启自动换行」），
 * 与 ZCode 的 `false` 不同；它同时是 `codePreviewWrap` store 的兜底默认值。
 */
export interface CodePreviewSettings {
  lightTheme: BundledTheme;
  darkTheme: BundledTheme;
  showLineNumbers: boolean;
  wrapLongLines: boolean;
  fontSizePx: number;
}

export const DEFAULT_CODE_PREVIEW_SETTINGS: CodePreviewSettings = {
  lightTheme: "github-light",
  darkTheme: "github-dark",
  showLineNumbers: true,
  wrapLongLines: true,
  fontSizePx: 12,
};

/** 按当前应用主题取代码主题。 */
export function resolveCodePreviewTheme(
  isDark: boolean,
  settings: CodePreviewSettings = DEFAULT_CODE_PREVIEW_SETTINGS
): BundledTheme {
  return isDark ? settings.darkTheme : settings.lightTheme;
}
