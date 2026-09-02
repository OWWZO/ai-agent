package org.wwz.ai.domain.agent.ledger;

import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.LlmInvocation;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;

import java.util.List;

/**
 * Phase 1 执行账本读仓储端口。
 * 仅暴露查询服务当前需要的聚合读能力。
 */
public interface IExecutionLedgerReadRepository {

    DialogueRun queryRunByRequestId(String requestId);

    List<LlmInvocation> queryLlmInvocationsByRunId(Long runId);

    List<ToolInvocation> queryToolInvocationsByRunId(Long runId);

    List<ArtifactRecord> queryArtifactsByRunId(Long runId);

    List<ToolInvocationView> queryRecentToolInvocations(String toolName, int limit);

    List<DialogueRunView> queryRecentRunsBySessionId(String sessionId, int limit);

    List<DialogueRunView> queryRunsBySessionId(String sessionId);

    DialogueSessionView querySession(String sessionId);

    List<DialogueSessionView> queryRecentSessions(int limit);

    DialogueSession querySessionEntity(String sessionId);

    DialogueSessionView querySession(String visitorId, String sessionId);

    List<DialogueSessionView> queryRecentSessions(String visitorId, int limit);

    List<ArtifactRecord> queryArtifactsByRunIds(List<Long> runIds);
}
