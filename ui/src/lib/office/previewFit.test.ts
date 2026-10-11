import { describe, expect, it } from "vitest";
import { calculateDocxPreviewFit, isSameDocxPreviewFit } from "./previewFit";

describe("calculateDocxPreviewFit", () => {
  it("可视区比纸张窄时按比例缩小", () => {
    const fit = calculateDocxPreviewFit({
      availableWidth: 400,
      naturalWidth: 800,
      naturalHeight: 1000,
    });
    expect(fit).toEqual({ scale: 0.5, width: 400, height: 500 });
  });

  it("可视区比纸张宽时保持 1 倍，不做放大", () => {
    const fit = calculateDocxPreviewFit({
      availableWidth: 1200,
      naturalWidth: 800,
      naturalHeight: 1000,
    });
    expect(fit).toEqual({ scale: 1, width: 800, height: 1000 });
  });

  it("宽度刚好相等时 scale 为 1", () => {
    expect(
      calculateDocxPreviewFit({ availableWidth: 794, naturalWidth: 794, naturalHeight: 1123 })
    ).toEqual({ scale: 1, width: 794, height: 1123 });
  });

  it("非法输入一律返回 null", () => {
    expect(
      calculateDocxPreviewFit({ availableWidth: 0, naturalWidth: 800, naturalHeight: 1000 })
    ).toBeNull();
    expect(
      calculateDocxPreviewFit({ availableWidth: -10, naturalWidth: 800, naturalHeight: 1000 })
    ).toBeNull();
    expect(
      calculateDocxPreviewFit({ availableWidth: 400, naturalWidth: 0, naturalHeight: 1000 })
    ).toBeNull();
    expect(
      calculateDocxPreviewFit({ availableWidth: 400, naturalWidth: 800, naturalHeight: 0 })
    ).toBeNull();
    expect(
      calculateDocxPreviewFit({
        availableWidth: Number.NaN,
        naturalWidth: 800,
        naturalHeight: 1000,
      })
    ).toBeNull();
    expect(
      calculateDocxPreviewFit({
        availableWidth: Number.POSITIVE_INFINITY,
        naturalWidth: 800,
        naturalHeight: 1000,
      })
    ).toBeNull();
  });
});

describe("isSameDocxPreviewFit", () => {
  const base = { scale: 0.5, width: 400, height: 500 };

  it("current 为 null 时永远不同", () => {
    expect(isSameDocxPreviewFit(null, base)).toBe(false);
  });

  it("亚像素抖动被吸收", () => {
    expect(isSameDocxPreviewFit(base, { scale: 0.5001, width: 400.2, height: 500.3 })).toBe(true);
  });

  it("超过阈值即视为变化", () => {
    expect(isSameDocxPreviewFit(base, { scale: 0.51, width: 400, height: 500 })).toBe(false);
    expect(isSameDocxPreviewFit(base, { scale: 0.5, width: 401, height: 500 })).toBe(false);
    expect(isSameDocxPreviewFit(base, { scale: 0.5, width: 400, height: 501 })).toBe(false);
  });
});
