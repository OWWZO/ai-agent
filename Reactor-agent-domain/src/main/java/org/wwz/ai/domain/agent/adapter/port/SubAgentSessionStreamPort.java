package org.wwz.ai.domain.agent.adapter.port;

import org.wwz.ai.domain.agent.runtime.printer.Printer;

/**
 * 子会话独立流端口。
 * <p>
 * Phase 3 SSE 隔离用：子 Agent 的事件不再透传父流，而是写入子会话自己的投影流，
 * 由 {@code GET /api/agent/session/{childSessionId}/stream} 按需订阅。
 * <p>
 * 契约：
 * <ul>
 *   <li>{@link #openChildSessionStream} 必须在 child run 启动前调用；实现负责把投影注册进
 *       SessionProjectionRegistry，使 SSE 端点按 sessionId 自然可达；</li>
 *   <li>{@link #closeChildSessionStream} 在 run 终态/异常时调用，注销投影；不得关闭父流；</li>
 *   <li>返回 null 表示无法隔离（实现缺资源），调用方应回退到旧的透传父流行为。</li>
 * </ul>
 */
public interface SubAgentSessionStreamPort {

    /**
     * 打开子会话独立投影流。
     *
     * @param childSessionId 子会话 ID（{@code sub_{agentId}}）
     * @param userId         会话归属用户
     * @param requestId      子 run 的 requestId（与 ledger run 同一）
     * @param queryText      子 Agent 任务 prompt，用于投影初始 query
     * @return 子会话领域 Printer；无法隔离时返回 null
     */
    Printer openChildSessionStream(String childSessionId,
                                   String userId,
                                   String requestId,
                                   String queryText);

    /**
     * 关闭并注销子会话投影流。
     *
     * @param childSessionId 子会话 ID
     * @param printer        {@link #openChildSessionStream} 返回的实例；空时按 sessionId 兜底清理
     */
    void closeChildSessionStream(String childSessionId, Printer printer);
}
