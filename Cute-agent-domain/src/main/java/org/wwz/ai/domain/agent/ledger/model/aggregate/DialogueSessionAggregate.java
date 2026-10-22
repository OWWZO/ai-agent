package org.wwz.ai.domain.agent.ledger.model.aggregate;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话-执行聚合。
 * <p>
 * 组合 {@link DialogueSession} 与其 {@link DialogueRun}，统一承载会话级状态转移：
 * 开始一轮 run 时同时建立 run 事实与会话计数；结束 run 时同步 run 终态与会话计数，
 * 避免 Application Service 自行拼装两套状态判断。
 */
public final class DialogueSessionAggregate {

    private final DialogueSession session;
    private final List<DialogueRun> runs;

    private DialogueSessionAggregate(DialogueSession session, List<DialogueRun> runs) {
        if (session == null) {
            throw new IllegalArgumentException("DialogueSession 不能为空");
        }
        this.session = session;
        this.runs = runs == null ? new ArrayList<>() : new ArrayList<>(runs);
    }

    public static DialogueSessionAggregate of(DialogueSession session, List<DialogueRun> runs) {
        return new DialogueSessionAggregate(session, runs);
    }

    public DialogueSession session() {
        return session;
    }

    public List<DialogueRun> runs() {
        return Collections.unmodifiableList(runs);
    }

    public DialogueSessionAggregate bindOwner(String userId) {
        session.bindOwner(userId);
        return this;
    }

    public DialogueSessionAggregate rename(String title) {
        session.rename(title);
        return this;
    }

    /**
     * 开启一轮执行：创建 RUNNING 状态的 run 事实，并同步会话计数。
     */
    public DialogueRun startRun(String requestId, String queryText, LocalDateTime now) {
        return startRun(requestId, queryText, null, now);
    }

    /**
     * 开启一轮执行：创建 RUNNING 状态的 run 事实，并同步会话计数。
     */
    public DialogueRun startRun(String requestId, String queryText, String entryAgent, LocalDateTime now) {
        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("requestId 不能为空");
        }
        LocalDateTime startedAt = now == null ? LocalDateTime.now() : now;
        DialogueRun run = DialogueRun.builder()
                .runUid(requestId)
                .requestId(requestId)
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .entryAgent(entryAgent)
                .build();
        run.start(startedAt);
        runs.add(run);
        session.startRun(requestId, queryText, startedAt);
        return run;
    }

    /**
     * 结束指定 run：先落 run 终态，再同步会话 finishedRunCount；失败时额外累计 failedRunCount。
     */
    public DialogueRun finishRun(String requestId, Integer status, String summary, LocalDateTime now) {
        LocalDateTime finishedAt = now == null ? LocalDateTime.now() : now;
        DialogueRun run = findRun(requestId);
        if (run == null) {
            throw new IllegalStateException("找不到对应的 run: requestId=" + requestId);
        }
        if (status != null && status == ExecutionLedgerConstants.STATUS_FAILED) {
            run.finishFailed(run.getErrorCode(), summary, finishedAt);
        } else if (status != null && status == ExecutionLedgerConstants.STATUS_STOPPED) {
            run.finishStopped(summary, finishedAt);
        } else {
            run.finishSuccess(summary, finishedAt);
        }
        session.finishRun(status, summary, finishedAt);
        if (status != null && status == ExecutionLedgerConstants.STATUS_FAILED) {
            session.recordFailedRun(finishedAt);
        }
        return run;
    }

    public DialogueRun findRun(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return null;
        }
        for (DialogueRun run : runs) {
            if (requestId.equals(run.getRequestId())) {
                return run;
            }
        }
        return null;
    }
}
