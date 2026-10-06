export type MermaidModule = typeof import("mermaid");

let mermaidPromise: Promise<MermaidModule> | undefined;

export function loadMermaid(): Promise<MermaidModule> {
  return (mermaidPromise ??= import("mermaid"));
}
