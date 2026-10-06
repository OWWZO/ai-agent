package org.wwz.ai.domain.agent.ledger;

import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.RunCursor;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;

import java.util.List;

/**
 * 执行账本内部查询契约。
 */
public interface ExecutionLedgerQueryService {

    int DEFAULT_SESSION_RUN_PAGE_SIZE = 100;
    int MAX_SESSION_RUN_PAGE_SIZE = 100;

    ExecutionRunDetail queryRunDetail(String requestId);

    /** 只读取当前会话可见的输入/输出 artifact，不展开 run 回放明细。 */
    List<ArtifactView> querySessionArtifacts(String sessionId);

    /** 只读取 run 主表字段，用于 ownership 校验和轻量摘要。 */
    DialogueRunView queryRunSummary(String requestId);

    /**
     * 批量加载已经定位好的 run 明细，避免历史回放按 run 重复查询账本事实。
     */
    List<ExecutionRunDetail> queryRunDetails(List<DialogueRunView> runs);

    List<ToolInvocationView> queryRecentToolInvocations(String toolName, int limit);

    List<DialogueRunView> queryRecentSessionRuns(String sessionId, int limit);

    /** 按创建时间正序读取 session run 页面，limit 会在实现层强制限制。 */
    List<DialogueRunView> querySessionRuns(String sessionId, int offset, int limit);

    /** 按 create_time/id 正序读取 keyset 页面，返回数量可能为 limit+1。 */
    List<DialogueRunView> querySessionRuns(String sessionId, RunCursor after, int limit);

    /** 兼容已有调用方的首页查询；完整历史由 replay/memory 调用方逐页读取。 */
    default List<DialogueRunView> querySessionRuns(String sessionId) {
        return querySessionRuns(sessionId, 0, DEFAULT_SESSION_RUN_PAGE_SIZE);
    }

    DialogueSessionView querySession(String sessionId);

    /** 只读取会话元数据和截断后的最新摘要，供 UI 历史摘要页使用。 */
    DialogueSessionView querySessionHistorySummary(String sessionId);

    List<DialogueSessionView> queryRecentSessions(int limit);

    DialogueSessionView querySession(String userId, String sessionId);

    List<DialogueSessionView> queryRecentSessions(String userId, int limit);
}
