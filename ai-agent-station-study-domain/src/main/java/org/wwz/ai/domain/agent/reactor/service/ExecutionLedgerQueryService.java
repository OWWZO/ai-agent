package org.wwz.ai.domain.agent.reactor.service;

import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunView;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueSessionView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionRunDetail;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;

import java.util.List;

/**
 * 执行账本内部查询契约。
 */
public interface ExecutionLedgerQueryService {

    ExecutionRunDetail queryRunDetail(String requestId);

    List<ToolInvocationView> queryRecentToolInvocations(String toolName, int limit);

    List<DialogueRunView> queryRecentSessionRuns(String sessionId, int limit);

    List<DialogueRunView> querySessionRuns(String sessionId);

    DialogueSessionView querySession(String sessionId);

    List<DialogueSessionView> queryRecentSessions(int limit);
}
