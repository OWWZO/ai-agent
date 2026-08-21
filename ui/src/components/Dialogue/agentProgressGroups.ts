import { formatSubAgentDuration } from "@/utils/chat/subagent";
import type { AgentPhase } from "@/types/agentRuntime";

export function resolveAgentPhaseLabel(
  phase: AgentPhase | string | undefined,
  status: string
): string {
  const normalized = (phase || "").toLowerCase();
  switch (normalized) {
    case "queued":
      return "Queued";
    case "working":
      return "Working";
    case "suspended":
      return "Suspended";
    case "completed":
      return "Completed";
    case "failed":
      return "Failed";
    default:
      if (status === "running") return "Working";
      if (status === "failed") return "Failed";
      if (status === "completed") return "Completed";
      return "Queued";
  }
}

export function resolveAgentPhaseTone(
  phaseLabel: string
): "running" | "ok" | "error" | "neutral" {
  if (phaseLabel === "Working" || phaseLabel === "Queued") return "running";
  if (phaseLabel === "Failed") return "error";
  if (phaseLabel === "Completed") return "ok";
  return "neutral";
}

export function formatAgentElapsed(display: {
  elapsedMs?: number;
  totalDurationMs?: number;
}): string {
  return (
    formatSubAgentDuration(display.totalDurationMs) ||
    formatSubAgentDuration(display.elapsedMs) ||
    ""
  );
}
