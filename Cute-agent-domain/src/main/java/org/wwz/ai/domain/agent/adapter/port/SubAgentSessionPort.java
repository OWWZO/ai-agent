package org.wwz.ai.domain.agent.adapter.port;

/**
 * 子智能体会话身份端口。
 * <p>
 * 子 Agent 派发时，domain 需要把「子会话」落成一条真实会话记录（{@code session_kind=subagent}），
 * 但 domain 不能依赖 case/controller 层的会话服务（DDD 边界），故以端口形式由 case/infra 侧实现。
 * <p>
 * 契约：
 * <ul>
 *   <li><b>先落库后运行</b>：必须在 child run 启动之前完成，失败则不启动子 Agent，
 *       避免留下无主运行与不可查询的孤儿会话；</li>
 *   <li><b>幂等</b>：确定性 childSessionId 可能被续跑重复传入，实现须保证不会新建第二行；</li>
 *   <li><b>身份列不可覆盖</b>：已存在的会话行不得被本次调用改写 {@code session_kind}/{@code parent_session_id}。</li>
 * </ul>
 */
public interface SubAgentSessionPort {

    /**
     * 幂等确保子会话身份行存在。
     *
     * @param childSessionId  确定性子会话 ID（{@code sub_{agentId}}）
     * @param parentSessionId 父会话 ID，仅审计与授权穿透，不参与工作记忆 hydrate
     * @param userId          会话归属用户，与父会话一致
     * @param title           展示标题（子 Agent 任务描述）
     * @throws RuntimeException 落库失败时抛出，调用方据此中止子 Agent 启动
     */
    void ensureChildSession(String childSessionId, String parentSessionId, String userId, String title);
}
