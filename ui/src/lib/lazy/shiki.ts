export type ShikiModule = typeof import("shiki");

let shikiPromise: Promise<ShikiModule> | undefined;

export function loadShiki(): Promise<ShikiModule> {
  return (shikiPromise ??= import("shiki"));
}
