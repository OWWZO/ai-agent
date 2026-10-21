import { describe, expect, it } from "vitest";

import { clampWorkspaceLeftWidth } from "./useWorkspacePanels";

describe("workspace panel width calculation", () => {
  it("clamps widths to the supported desktop range", () => {
    expect(clampWorkspaceLeftWidth(10)).toBe(24);
    expect(clampWorkspaceLeftWidth(42)).toBe(42);
    expect(clampWorkspaceLeftWidth(80)).toBe(56);
  });
});
