export const ROUTES = {
  HOME: "/",
  FEATURED_CONVERSATIONS: "/featured-conversations",
  FEATURED_CONVERSATION_DETAIL: "/featured-conversations/:featuredId",
  WORKSPACE: "/workspace",
  WORKSPACE_MRAG: "/workspace/mrag",
  WORKSPACE_IMAGE_GENERATION: "/workspace/image-generation",
  NOT_FOUND: "*",
} as const;

export function buildFeaturedConversationDetailPath(featuredId: string) {
  return ROUTES.FEATURED_CONVERSATION_DETAIL.replace(
    ":featuredId",
    encodeURIComponent(featuredId)
  );
}
