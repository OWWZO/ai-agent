export const WORKSPACE_RESIZE_START_EVENT = "reactor-workspace-resize-start";
export const WORKSPACE_RESIZE_END_EVENT = "reactor-workspace-resize-end";
export const WORKSPACE_RESIZING_SELECTOR = '[data-workspace-resizing="true"]';

export const isWorkspaceResizeEventFor = (event: Event, node: Element) => {
  const target = event.target;
  return target instanceof Element && target.contains(node);
};
