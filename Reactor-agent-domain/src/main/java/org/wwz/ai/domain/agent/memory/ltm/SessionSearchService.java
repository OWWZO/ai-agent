package org.wwz.ai.domain.agent.memory.ltm;

/**
 * 情节按需检索。实现应查询持久化的消息级历史投影，并保留压缩前的失效投影。
 * Execution Ledger 仍是执行事实源；本接口只负责面向 Agent 的历史回忆视图。
 */
public interface SessionSearchService {

    /**
     * Hermes 风格的消息历史检索。实现根据请求参数选择 discovery、scroll、read 或 browse。
     */
    default String search(SessionSearchRequest request) {
        if (request == null) {
            return search(null, null, null, 0, null);
        }
        int limit = request.getLimit() == null ? 0 : request.getLimit();
        return search(request.getCurrentSessionId(), request.getVisitorId(), request.getQuery(),
                limit, request.getScope());
    }

    /**
     * @param sessionId  当前会话（scope=session 时必填；scope=user 时用于排序/标注当前会话）
     * @param visitorId  访客/用户归属（scope=user 时优先；可为空则回退本会话）
     * @param query      关键词
     * @param limit      最大命中
     * @param scope      session=仅本会话；user=该 visitor 下跨会话
     */
    String search(String sessionId, String visitorId, String query, int limit, String scope);
}
