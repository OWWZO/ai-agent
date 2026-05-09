import { describe, expect, it } from "vitest";

import {
  resolveVisitorWorkspaceStage,
  shouldBootstrapVisitor,
  shouldLoadVisitorProtectedData,
} from "./visitorGate";

describe("visitorGate", () => {
  it("未完成 bootstrap 前不能加载 visitor 保护数据", () => {
    expect(
      shouldLoadVisitorProtectedData({
        bootstrapLoaded: false,
        bootstrapLoading: true,
        visitorNamed: false,
      })
    ).toBe(false);
  });

  it("bootstrap 完成且当前 visitor 已命名后才允许加载 visitor 保护数据", () => {
    expect(
      shouldLoadVisitorProtectedData({
        bootstrapLoaded: true,
        bootstrapLoading: false,
        visitorNamed: true,
      })
    ).toBe(true);
  });

  it("bootstrap 完成但未命名时仍不能加载 visitor 保护数据", () => {
    expect(
      shouldLoadVisitorProtectedData({
        bootstrapLoaded: true,
        bootstrapLoading: false,
        visitorNamed: false,
      })
    ).toBe(false);
  });

  it("仅在尚未加载且不处于加载中时才需要触发 bootstrap", () => {
    expect(
      shouldBootstrapVisitor({
        bootstrapLoaded: false,
        bootstrapLoading: false,
      })
    ).toBe(true);

    expect(
      shouldBootstrapVisitor({
        bootstrapLoaded: false,
        bootstrapLoading: true,
      })
    ).toBe(false);
  });

  it("首页首访必须先停留在 bootstrap 阶段，避免并发拉取历史和近期会话", () => {
    expect(
      resolveVisitorWorkspaceStage({
        bootstrapLoaded: false,
        bootstrapLoading: false,
      })
    ).toBe("bootstrapping");

    expect(
      resolveVisitorWorkspaceStage({
        bootstrapLoaded: true,
        bootstrapLoading: false,
        visitorNamed: false,
      })
    ).toBe("naming");

    expect(
      resolveVisitorWorkspaceStage({
        bootstrapLoaded: true,
        bootstrapLoading: false,
        visitorNamed: true,
      })
    ).toBe("ready");
  });
});
