import type { ConversationSessionItem } from "@/services/agentConversation";
import type {
  FeaturedConversationAdminRecord,
  FeaturedConversationAdminUpsertPayload,
} from "@/services/featuredConversationAdmin";

const DEFAULT_SORT_ORDER = 100;
const DEFAULT_OPERATOR = "ui-featured-manager";

export interface FeaturedConversationFormState {
  sessionId: string;
  title: string;
  summary: string;
  coverUrl: string;
  tagsText: string;
  sortOrder: string;
  operator: string;
}

export function buildFeaturedConversationFormState(params: {
  session: ConversationSessionItem;
  existingRecord?: FeaturedConversationAdminRecord | null;
  operator?: string;
}): FeaturedConversationFormState {
  const { session, existingRecord } = params;

  return {
    sessionId: session.sessionId,
    title: existingRecord?.title || session.title || "",
    summary: existingRecord?.summary || session.latestQueryText || "",
    coverUrl: existingRecord?.coverUrl || "",
    tagsText: (existingRecord?.tags || []).join(", "),
    sortOrder: String(existingRecord?.sortOrder ?? DEFAULT_SORT_ORDER),
    operator: params.operator?.trim() || DEFAULT_OPERATOR,
  };
}

export function parseFeaturedConversationTags(tagsText: string) {
  return tagsText
    .split(/[\n,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean);
}

export function validateFeaturedConversationForm(
  form: FeaturedConversationFormState
) {
  if (!form.operator.trim()) {
    return "请填写操作人";
  }
  if (!form.sessionId.trim()) {
    return "缺少会话 ID";
  }
  if (!form.title.trim()) {
    return "请填写精品标题";
  }
  if (!form.summary.trim()) {
    return "请填写精品摘要";
  }
  return null;
}

export function toFeaturedConversationUpsertPayload(
  form: FeaturedConversationFormState,
  existingRecord?: FeaturedConversationAdminRecord | null
): FeaturedConversationAdminUpsertPayload {
  const parsedSortOrder = Number.parseInt(form.sortOrder.trim(), 10);

  return {
    featuredId: existingRecord?.featuredId,
    sessionId: form.sessionId.trim(),
    title: form.title.trim(),
    summary: form.summary.trim(),
    coverUrl: form.coverUrl.trim() || undefined,
    tags: parseFeaturedConversationTags(form.tagsText),
    sortOrder: Number.isFinite(parsedSortOrder)
      ? parsedSortOrder
      : DEFAULT_SORT_ORDER,
    operator: form.operator.trim() || DEFAULT_OPERATOR,
  };
}

/**
 * 精品会话依赖已存在的 session 持久化记录，空草稿会被后端拒绝，因此前端先做一次显式拦截。
 */
export function canFeatureConversationSession(
  session: ConversationSessionItem
) {
  return Boolean(session.sessionId && session.runCount > 0);
}

export function getFeaturedConversationStatusLabel(status?: string) {
  switch ((status || "").toUpperCase()) {
    case "ONLINE":
      return "已上线";
    case "OFFLINE":
      return "草稿";
    default:
      return "未创建";
  }
}
