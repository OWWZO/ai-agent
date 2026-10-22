package org.wwz.ai.infrastructure.adapter.port;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.SubAgentSessionPort;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerWriteRepository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.SessionKind;

import java.time.LocalDateTime;

/**
 * 子智能体隐藏会话身份端口适配：复用既有会话账本 upsert 写入 {@code session_kind=subagent} 的会话行。
 * <p>
 * 幂等语义：已存在即 no-op。原因见计划书 §5.1 —— 身份列与运行计数都不应被重复派发/续跑覆盖，
 * 否则续跑会把子会话的 run_count 重置为 0，且可能让子会话漏回侧栏。
 */
@Component
@RequiredArgsConstructor
public class SubAgentSessionPortAdapter implements SubAgentSessionPort {

    private static final String DEFAULT_TITLE = "子智能体任务";

    private final IExecutionLedgerWriteRepository executionLedgerWriteRepository;

    @Override
    public void ensureChildSession(String childSessionId,
                                   String parentSessionId,
                                   String userId,
                                   String title) {
        if (StringUtils.isAnyBlank(childSessionId, parentSessionId)) {
            throw new IllegalArgumentException("childSessionId 与 parentSessionId 不能为空");
        }
        DialogueSession existing = executionLedgerWriteRepository.querySessionBySessionId(childSessionId);
        if (existing != null) {
            // 身份行已存在（重复派发 / 续跑）：保持原样，不覆盖 run 计数与身份列。
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        executionLedgerWriteRepository.upsertSession(DialogueSessionUpsertRecord.builder()
                .sessionId(childSessionId)
                .userId(userId)
                .sessionKind(SessionKind.SUBAGENT.code())
                .parentSessionId(parentSessionId)
                .title(StringUtils.defaultIfBlank(title, DEFAULT_TITLE))
                .status(ExecutionLedgerConstants.STATUS_RUNNING)
                .runCount(0)
                .finishedRunCount(0)
                .failedRunCount(0)
                .startedAt(now)
                .lastActiveAt(now)
                .build());
    }
}
