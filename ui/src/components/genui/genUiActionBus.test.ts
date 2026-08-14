import { describe, expect, it, vi, beforeEach } from "vitest";
import {
  normalizeAction,
  dispatchGenUiAction,
  registerGenUiActionAdapters,
  resetGenUiActionAdapters,
} from "./genUiActionBus";

describe("genUiActionBus", () => {
  beforeEach(() => {
    resetGenUiActionAdapters();
  });

  it("does not treat bare actionId as send_message", () => {
    expect(normalizeAction("click me")).toBeNull();
    expect(normalizeAction(undefined, { actionId: "hello" })).toBeNull();
  });

  it("normalizes explicit send_message only", () => {
    expect(
      normalizeAction({ type: "send_message", payload: { content: "hi" } })
    ).toEqual({ type: "send_message", payload: { content: "hi" } });
  });

  it("normalizes patch_ui", () => {
    const action = normalizeAction({
      type: "patch_ui",
      payload: {
        patches: [{ op: "replace", path: "/root/props/title", value: "X" }],
      },
    });
    expect(action?.type).toBe("patch_ui");
  });

  it("dispatches patch_ui to adapter, not sendMessage", async () => {
    const patchUi = vi.fn();
    const sendMessage = vi.fn();
    registerGenUiActionAdapters({ patchUi, sendMessage });
    const result = await dispatchGenUiAction({
      type: "patch_ui",
      payload: {
        patches: [{ op: "replace", path: "/root/props/title", value: "Y" }],
      },
    });
    expect(result).toEqual({ ok: true, type: "patch_ui" });
    expect(patchUi).toHaveBeenCalledTimes(1);
    expect(sendMessage).not.toHaveBeenCalled();
  });

  it("returns invalid when action cannot be normalized", async () => {
    const result = await dispatchGenUiAction("bare-string");
    expect(result).toEqual({ ok: false, reason: "invalid" });
  });
});
