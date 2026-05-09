/**
 * 首页 visitor bootstrap 编排判断。
 */
export function shouldBootstrapVisitor(params: {
  bootstrapLoaded: boolean;
  bootstrapLoading: boolean;
}) {
  return !params.bootstrapLoaded && !params.bootstrapLoading;
}

/**
 * 当前首页工作区处于哪个 visitor 引导阶段。
 */
export function resolveVisitorWorkspaceStage(params: {
  bootstrapLoaded: boolean;
  bootstrapLoading: boolean;
  visitorNamed?: boolean;
}) {
  if (!params.bootstrapLoaded || params.bootstrapLoading) {
    return "bootstrapping" as const;
  }
  if (params.visitorNamed === false) {
    return "naming" as const;
  }
  return "ready" as const;
}

/**
 * 仅在 bootstrap 完成且当前 visitor 已命名后才允许继续加载 visitor 保护数据。
 */
export function shouldLoadVisitorProtectedData(params: {
  bootstrapLoaded: boolean;
  bootstrapLoading: boolean;
  visitorNamed?: boolean;
}) {
  return resolveVisitorWorkspaceStage(params) === "ready";
}
