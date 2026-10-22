package org.wwz.ai.domain.agent.ledger.replay.projector.support;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;

import java.util.List;
import java.util.function.Function;

/**
 * 历史回放时按 tool invocation 反查 HITL 让步的持久化状态。
 * <p>
 * AskUserQuestion 与 RequestDesktopControl 两个 projector 的反查逻辑逐字相同：
 * 先按业务 id 直查，未命中再按 session 扫开放记录并用 toolInvocationId /
 * toolCallId 兜底匹配。共用的安全语义是"历史回放不能因附属状态读失败而中断"，
 * 因此仓储异常一律吞掉返回 null。
 */
public final class PersistedYieldLookup {

    private PersistedYieldLookup() {
    }

    /**
     * @param businessId   业务 id（questionId / controlId），可空
     * @param invocation   当前 tool invocation；为空或没有 sessionId 时不做兜底扫描
     * @param byId         按业务 id 直查
     * @param openBySession 按 sessionId 列出开放记录
     * @param toolInvocationIdOf 取记录的 toolInvocationId
     * @param toolCallIdOf      取记录的 toolCallId
     */
    public static <T> T find(String businessId,
                             ToolInvocationView invocation,
                             Function<String, T> byId,
                             Function<String, List<T>> openBySession,
                             Function<T, Long> toolInvocationIdOf,
                             Function<T, String> toolCallIdOf) {
        try {
            if (StringUtils.isNotBlank(businessId)) {
                T byBusinessId = byId.apply(businessId);
                if (byBusinessId != null) {
                    return byBusinessId;
                }
            }
            if (invocation == null || StringUtils.isBlank(invocation.getSessionId())) {
                return null;
            }
            for (T record : openBySession.apply(invocation.getSessionId())) {
                if (record == null) {
                    continue;
                }
                if (invocation.getId() != null
                        && invocation.getId().equals(toolInvocationIdOf.apply(record))) {
                    return record;
                }
                if (StringUtils.isNotBlank(invocation.getToolCallId())
                        && invocation.getToolCallId().equals(toolCallIdOf.apply(record))) {
                    return record;
                }
            }
            return null;
        } catch (RuntimeException ignored) {
            // 历史回放不能因问询/桌面控制附属状态读取失败而中断。
            return null;
        }
    }
}
