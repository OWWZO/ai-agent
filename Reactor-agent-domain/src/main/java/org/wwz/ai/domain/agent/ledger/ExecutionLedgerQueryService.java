package org.wwz.ai.domain.agent.ledger;

import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;

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

    DialogueSessionView querySession(String visitorId, String sessionId);

    List<DialogueSessionView> queryRecentSessions(String visitorId, int limit);
}
