import { cn } from "@/lib/utils";

type DotKind = "ok" | "error" | "running" | "suspended" | "idle";

function normalize(status?: string): DotKind {
  switch (status) {
    case "ok":
    case "done":
    case "completed":
    case "success":
      return "ok";
    case "error":
    case "failed":
    case "danger":
      return "error";
    case "running":
    case "working":
    case "in_progress":
    case "active":
      return "running";
    case "suspended":
      return "suspended";
    default:
      return "idle";
  }
}

export function StatusDot({ status }: { status?: string }) {
  const kind = normalize(status);
  return <span className={cn("kw-dot", `kw-dot--${kind}`)} aria-hidden />;
}
