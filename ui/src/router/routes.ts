export const ROUTES = {
  HOME: "/",
  LOGIN: "/login",
  REGISTER: "/register",
  FEATURED_CONVERSATIONS: "/featured-conversations",
  FEATURED_CONVERSATION_DETAIL: "/featured-conversations/:featuredId",
  WORKSPACE: "/workspace",
  WORKSPACE_MRAG: "/workspace/mrag",
  WORKSPACE_IMAGE_GENERATION: "/workspace/image-generation",
  WORKSPACE_SOP: "/workspace/sop",
  WORKSPACE_SUB_AGENTS: "/workspace/sub-agents",
  WORKSPACE_MODELS: "/workspace/models",
  WORKSPACE_CAPABILITIES: "/workspace/capabilities",
  NOT_FOUND: "*",
} as const;

export function normalizeReturnUrl(value: string | null | undefined) {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) {
    return ROUTES.HOME;
  }
  return value;
}

export function buildLoginPath(returnUrl: string) {
  const params = new URLSearchParams({ returnUrl: normalizeReturnUrl(returnUrl) });
  return `${ROUTES.LOGIN}?${params.toString()}`;
}

export function buildFeaturedConversationDetailPath(featuredId: string) {
  return ROUTES.FEATURED_CONVERSATION_DETAIL.replace(
    ":featuredId",
    encodeURIComponent(featuredId)
  );
}
