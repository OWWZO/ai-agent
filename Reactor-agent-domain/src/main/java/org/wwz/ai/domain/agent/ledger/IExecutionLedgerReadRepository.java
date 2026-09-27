package org.wwz.ai.domain.agent.ledger;

import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.LlmInvocation;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.RunCursor;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;

import java.util.List;

/**
 * Phase 1 执行账本读仓储端口。
 * 仅暴露查询服务当前需要的聚合读能力。
 */
public interface IExecutionLedgerReadRepository {

    DialogueRun queryRunByRequestId(String requestId);

    DialogueRun queryRunSummaryByRequestId(String requestId);

    List<LlmInvocation> queryLlmInvocationsByRunId(Long runId);

    List<LlmInvocation> queryLlmInvocationsByRunIds(List<Long> runIds);

    List<ToolInvocation> queryToolInvocationsByRunId(Long runId);

    List<ToolInvocation> queryToolInvocationsByRunIds(List<Long> runIds);

    List<ArtifactRecord> queryArtifactsByRunId(Long runId);

    List<ToolInvocationView> queryRecentToolInvocations(String toolName, int limit);

    List<DialogueRunView> queryRecentRunsBySessionId(String sessionId, int limit);

    List<DialogueRunView> queryRunsBySessionId(String sessionId, int offset, int limit);

    /**
     * 按 create_time/id 正序读取 session run，cursor 为空时从头开始。
     * 实现层不得为该轻量查询补 artifact 或 rich tool output。
     */
    List<DialogueRunView> queryRunsBySessionId(String sessionId, RunCursor after, int limit);

    /** 兼容已有内部调用方；底层查询仍受分页上限约束。 */
    default List<DialogueRunView> queryRunsBySessionId(String sessionId) {
        return queryRunsBySessionId(sessionId, 0, 100);
    }

    DialogueSessionView querySession(String sessionId);

    DialogueSessionView querySessionHistorySummary(String sessionId);

    List<DialogueSessionView> queryRecentSessions(int limit);

    DialogueSession querySessionEntity(String sessionId);

    DialogueSession querySessionOwnership(String sessionId);

    DialogueSessionView querySession(String visitorId, String sessionId);

    List<DialogueSessionView> queryRecentSessions(String visitorId, int limit);

    List<ArtifactRecord> queryArtifactsByRunIds(List<Long> runIds);
}
