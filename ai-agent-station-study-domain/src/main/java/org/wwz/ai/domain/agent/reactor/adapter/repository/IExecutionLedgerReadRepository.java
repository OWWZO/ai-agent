package org.wwz.ai.domain.agent.reactor.adapter.repository;

import org.wwz.ai.domain.agent.reactor.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.reactor.entity.DialogueRun;
import org.wwz.ai.domain.agent.reactor.entity.LlmInvocation;
import org.wwz.ai.domain.agent.reactor.entity.ToolInvocation;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunView;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueSessionView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;

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

    List<ArtifactRecord> queryArtifactsByRunIds(List<Long> runIds);
}
