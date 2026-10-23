import { describe, expect, it } from "vitest";

import {
  CHAT_PANEL_DEFAULT_SIZE,
  clampWorkspaceLeftWidth,
  panelPercent,
} from "./useWorkspacePanels";

describe("workspace panel width calculation", () => {
  it("clamps widths to the supported desktop range", () => {
    expect(clampWorkspaceLeftWidth(10)).toBe(24);
    expect(clampWorkspaceLeftWidth(42)).toBe(42);
    expect(clampWorkspaceLeftWidth(80)).toBe(56);
  });

  // react-resizable-panels 把裸数字解释为像素（24 = 24px），只有字符串才是百分比。
  // 回归：曾直接传 24 / 56，对话区被压成 56px 宽，工作区吃掉整屏。
  it("emits percentage strings for Panel size props", () => {
    expect(panelPercent(24)).toBe("24%");
    expect(panelPercent(50)).toBe("50%");
    expect(panelPercent(56)).toBe("56%");
  });

  // 回归：defaultSize 曾绑定拖拽中变化的宽度 state，库会把布局重置回原值，
  // 分隔条只能挪动一两像素就被拽回 —— 表现为「中间那条线拖不动」。
  it("keeps the panel default size a static percentage", () => {
    expect(CHAT_PANEL_DEFAULT_SIZE).toBe("50%");
  });
});
