import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import RunStatus from "./RunStatus";

describe("RunStatus", () => {
  it("renders stopped status with explicit terminal copy", () => {
    const html = renderToStaticMarkup(
      <RunStatus status="STOPPED" finishedAt="2026-05-02T12:05:00" />
    );

    expect(html).toContain("已停止");
    expect(html).toContain("结束于 2026-05-02T12:05:00");
  });

  it("does not render success status banner", () => {
    const html = renderToStaticMarkup(
      <RunStatus status="SUCCESS" />
    );

    expect(html).toBe("");
  });
});
