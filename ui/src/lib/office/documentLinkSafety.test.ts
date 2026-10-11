import { describe, expect, it } from "vitest";
import { sanitizeDocumentHref } from "./documentLinkSafety";

/**
 * 只覆盖纯函数部分。
 *
 * `installDocumentLinkSafety` 需要真实 DOM（仓库未装 jsdom/happy-dom，
 * 现有测试均走 `renderToStaticMarkup` 的 SSR 路径），其行为在 Phase 4 的
 * CDP 真实渲染矩阵里验证。
 */
describe("sanitizeDocumentHref", () => {
  it("放行 http / https 外链", () => {
    expect(sanitizeDocumentHref("https://example.com/a")).toBe("https://example.com/a");
    expect(sanitizeDocumentHref("http://example.com")).toBe("http://example.com");
  });

  it("放行同文档锚点", () => {
    expect(sanitizeDocumentHref("#section-1")).toBe("#section-1");
  });

  it("拒绝空锚点与带空白的锚点", () => {
    expect(sanitizeDocumentHref("#")).toBeNull();
    expect(sanitizeDocumentHref("#a b")).toBeNull();
    expect(sanitizeDocumentHref("#a<b>")).toBeNull();
  });

  it("拒绝 javascript: 等危险协议", () => {
    expect(sanitizeDocumentHref("javascript:alert(1)")).toBeNull();
    expect(sanitizeDocumentHref("data:text/html,<script>")).toBeNull();
    expect(sanitizeDocumentHref("file:///C:/secret.txt")).toBeNull();
    expect(sanitizeDocumentHref("vbscript:msgbox")).toBeNull();
  });

  it("拒绝用控制字符伪装的协议", () => {
    expect(sanitizeDocumentHref("java\nscript:alert(1)")).toBeNull();
    expect(sanitizeDocumentHref("java\u0000script:alert(1)")).toBeNull();
  });

  it("拒绝非字符串与空串", () => {
    expect(sanitizeDocumentHref(undefined)).toBeNull();
    expect(sanitizeDocumentHref(null)).toBeNull();
    expect(sanitizeDocumentHref(123)).toBeNull();
    expect(sanitizeDocumentHref("   ")).toBeNull();
  });

  it("相对路径不被当作外链放行", () => {
    expect(sanitizeDocumentHref("images/a.png")).toBeNull();
    expect(sanitizeDocumentHref("/abs/path")).toBeNull();
  });
});
