import { describe, expect, it } from "vitest";

import {
  calculateWorkspaceLeftWidth,
  clampWorkspaceLeftWidth,
} from "./useWorkspacePanels";

describe("workspace panel width calculation", () => {
  it("clamps widths to the supported desktop range", () => {
    expect(clampWorkspaceLeftWidth(10)).toBe(24);
    expect(clampWorkspaceLeftWidth(42)).toBe(42);
    expect(clampWorkspaceLeftWidth(80)).toBe(56);
  });

  it("converts pointer movement into a clamped percentage", () => {
    expect(
      calculateWorkspaceLeftWidth({
        clientX: 700,
        startX: 500,
        startWidth: 50,
        containerWidth: 1000,
      })
    ).toBe(56);

    expect(
      calculateWorkspaceLeftWidth({
        clientX: 200,
        startX: 500,
        startWidth: 50,
        containerWidth: 1000,
      })
    ).toBe(24);
  });

  it("keeps the starting width when the container is unavailable", () => {
    expect(
      calculateWorkspaceLeftWidth({
        clientX: 800,
        startX: 500,
        startWidth: 42,
        containerWidth: 0,
      })
    ).toBe(42);
  });
});
