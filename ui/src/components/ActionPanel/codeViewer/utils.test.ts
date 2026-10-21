import { describe, expect, it } from "vitest";

import { buildDiffViewerStyle } from "./utils";

describe("buildDiffViewerStyle", () => {
  it("暗色必须显式指定 colorScheme", () => {
    // 应用暗色由 `.dark` class 驱动，而 @pierre/diffs 的 token 依赖
    // light-dark()；不指定 colorScheme 时暗色下会渲染成浅色主题的深色文字。
    expect(buildDiffViewerStyle({
      isDark: true,
      fontSizePx: 12
    }).colorScheme).toBe(
      "dark"
    );
    expect(buildDiffViewerStyle({
      isDark: false,
      fontSizePx: 12
    }).colorScheme).toBe(
      "light"
    );
  });

  it("三个背景 token 都指向 chat-surface", () => {
    const style = buildDiffViewerStyle({
      isDark: false,
      fontSizePx: 12
    });
    expect(style["--diffs-bg"]).toBe("var(--chat-surface)");
    expect(style["--diffs-light-bg"]).toBe("var(--chat-surface)");
    expect(style["--diffs-dark-bg"]).toBe("var(--chat-surface)");
  });

  it("字体走项目的 mono token，避免 Shadow DOM 落到宋体", () => {
    expect(
      buildDiffViewerStyle({
        isDark: false,
        fontSizePx: 12
      })["--diffs-font-family"]
    ).toBe("var(--font-mono)");
  });

  it("字号原样透传并带上 px 单位", () => {
    expect(
      buildDiffViewerStyle({
        isDark: false,
        fontSizePx: 13.5
      })["--diffs-font-size"]
    ).toBe("13.5px");
  });

  it("CodeViewer 与 PatchViewer 用同一份配置（同输入同输出）", () => {
    const params = {
      isDark: true,
      fontSizePx: 14
    };
    expect(buildDiffViewerStyle(params)).toEqual(buildDiffViewerStyle(params));
  });
});
