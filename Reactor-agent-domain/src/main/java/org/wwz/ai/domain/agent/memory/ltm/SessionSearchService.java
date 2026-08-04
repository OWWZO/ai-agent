package org.wwz.ai.domain.agent.memory.ltm;

/**
 * 情节按需检索（对齐 Hermes session_search）：查 Execution Ledger 真相源。
 * <p>
 * 不搜 working memory：working memory 是可压缩热窗口投影，会丢细节、可与 ledger 重复，
 * 且跨会话不稳定；情节「无限原文」以 ledger 为准。
 */
public interface SessionSearchService {

    /**
     * @param sessionId  当前会话（scope=session 时必填；scope=user 时用于排序/标注当前会话）
     * @param visitorId  访客/用户归属（scope=user 时优先；可为空则回退本会话）
     * @param query      关键词
     * @param limit      最大命中
     * @param scope      session=仅本会话；user=该 visitor 下跨会话
     */
    String search(String sessionId, String visitorId, String query, int limit, String scope);
}
