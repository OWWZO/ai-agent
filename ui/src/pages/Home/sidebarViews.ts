import { ROUTES } from "@/router/routes";

/**
 * 侧栏视图的唯一定义处。
 *
 * 工作台模块（mrag / sop / …）只有一套导航：Home 页内切换。
 * `/workspace/*` 旧路由保留为重定向入口，落到 `/?view=<key>`，
 * 因此 view 与 URL 必须由这里统一换算，避免两套模型各写一份枚举。
 */
export type SidebarView =
  | "chat"
  | "mrag"
  | "image-generation"
  | "sop"
  | "sub-agents"
  | "models"
  | "capabilities"
  | "tools"
  | "featured";

export const SIDEBAR_VIEWS: SidebarView[] = [
  "chat",
  "mrag",
  "image-generation",
  "sop",
  "sub-agents",
  "models",
  "capabilities",
  "tools",
  "featured",
];

export const isSidebarView = (value: string | null): value is SidebarView =>
  value !== null && SIDEBAR_VIEWS.includes(value as SidebarView);

/** 从 query 还原视图；非法或缺省都回落到对话页。 */
export const readViewFromSearch = (searchParams: URLSearchParams): SidebarView => {
  const view = searchParams.get("view");
  return isSidebarView(view) ? view : "chat";
};

/** 对话页不带 query，其余视图统一挂在 `?view=` 上。 */
export const buildHomeViewPath = (view: SidebarView): string =>
  view === "chat" ? ROUTES.HOME : `${ROUTES.HOME}?view=${view}`;
