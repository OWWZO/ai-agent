import type { CSSProperties } from "react";
import type { BundledLanguage, ThemedToken } from "shiki";
import { loadShiki } from "@/lib/lazy/shiki";

const TEXT_LANGUAGE = "text" as BundledLanguage;

/**
 * shiki token → 内联样式。
 *
 * shiki 的 `FontStyle` 是位掩码（italic=1 / bold=2 / underline=4），
 * 直接写进 `fontStyle` 会失效，必须逐位翻译成 CSS 属性。
 */
export function tokenStyle(token: ThemedToken): CSSProperties | undefined {
  if (!token.color && !token.fontStyle) {
    return undefined;
  }
  const style: CSSProperties = {};
  if (token.color) {
    style.color = token.color;
  }
  const fontStyle = token.fontStyle ?? 0;
  if (fontStyle & 1) {
    style.fontStyle = "italic";
  }
  if (fontStyle & 2) {
    style.fontWeight = 700;
  }
  if (fontStyle & 4) {
    style.textDecoration = "underline";
  }
  return style;
}

/**
 * 逐行 tokenize。未知语言回退到 `text`，`text` 自身失败则抛错由调用方兜底成纯文本。
 *
 * 与 `KimiCodeFence` / 内联 diff 共用，避免两处各写一份 shiki 调用与回退规则。
 */
export async function tokenizeCode(
  code: string,
  language: BundledLanguage,
  dark: boolean
): Promise<ThemedToken[][]> {
  const { codeToTokens } = await loadShiki();
  const tokenize = (lang: BundledLanguage) =>
    codeToTokens(code, {
      lang,
      theme: dark ? "github-dark" : "github-light",
    });

  try {
    return (await tokenize(language)).tokens;
  } catch {
    if (language === TEXT_LANGUAGE) throw new Error("Shiki language unavailable");
    return (await tokenize(TEXT_LANGUAGE)).tokens;
  }
}
