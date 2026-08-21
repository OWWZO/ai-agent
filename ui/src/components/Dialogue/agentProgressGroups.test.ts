import { describe, expect, it } from "vitest";
import {
  resolveAgentPhaseLabel,
  resolveAgentPhaseTone,
} from "./agentProgressGroups";

describe("agent phase helpers", () => {
  it("maps phase labels", () => {
    expect(resolveAgentPhaseLabel("working", "running")).toBe("Working");
    expect(resolveAgentPhaseLabel(undefined, "failed")).toBe("Failed");
    expect(resolveAgentPhaseLabel("completed", "completed")).toBe("Completed");
  });

  it("maps phase tones", () => {
    expect(resolveAgentPhaseTone("Working")).toBe("running");
    expect(resolveAgentPhaseTone("Failed")).toBe("error");
    expect(resolveAgentPhaseTone("Completed")).toBe("ok");
  });
});
