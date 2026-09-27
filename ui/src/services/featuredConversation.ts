import api from "./index";
import type {
  ConversationHistoryDetail,
  ConversationHistoryPage,
  ConversationRunReplay,
} from "./agentConversation";

export interface FeaturedConversationCard {
  featuredId: string;
  sessionId: string;
  title: string;
  summary: string;
  coverUrl?: string;
  tags: string[];
  publishedAt?: string;
  contentLastActiveAt?: string;
}

export interface FeaturedConversationDetail extends FeaturedConversationCard {
  status?: string;
  contentAvailable: boolean;
  contentUnavailableReason?: string;
  historyDetail: ConversationHistoryPage | ConversationHistoryDetail | null;
}

export interface FeaturedConversationPage {
  total: number;
  list: FeaturedConversationCard[];
}

export const featuredConversationApi = {
  listHome: (limit = 6) =>
    api.get<FeaturedConversationCard[]>("/api/agent/featured-conversations/home", {limit,}) as unknown as Promise<FeaturedConversationCard[]>,
  list: (params: { pageNo: number; pageSize: number }) =>
    api.get<FeaturedConversationPage>("/api/agent/featured-conversations", params) as unknown as Promise<FeaturedConversationPage>,
  detail: (featuredId: string) =>
    api.get<FeaturedConversationDetail>(`/api/agent/featured-conversations/${featuredId}`) as unknown as Promise<FeaturedConversationDetail>,
  getFeaturedRunReplay: (featuredId: string, requestId: string) =>
    api.get<ConversationRunReplay>(
      `/api/agent/featured-conversations/${encodeURIComponent(featuredId)}/runs/${encodeURIComponent(requestId)}/replay`
    ) as unknown as Promise<ConversationRunReplay>,
};
