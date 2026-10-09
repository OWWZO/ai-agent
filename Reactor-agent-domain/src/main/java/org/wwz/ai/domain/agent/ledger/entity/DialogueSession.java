package org.wwz.ai.domain.agent.ledger.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;

import java.time.LocalDateTime;

/**
 * 会话级执行摘要。
 * <p>
 * 充血模型：所有者绑定、标题变更与 run 计数转移由实体自身承载，并强制以下不变量：
 * <ul>
 *     <li>已绑定会话不能被其他用户覆盖所有者；</li>
 *     <li>runCount 不得为负数；</li>
 *     <li>finishedRunCount 不得超过 runCount；</li>
 *     <li>failedRunCount 不得超过 finishedRunCount。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueSession {

    private Long id;

    private String sessionId;

    private String userId;

    private String title;

    private Integer status;

    private String latestRequestId;

    private String latestQueryText;

    private String latestSummaryText;

    private Integer runCount;

    private Integer finishedRunCount;

    private Integer failedRunCount;

    private LocalDateTime startedAt;

    private LocalDateTime lastActiveAt;

    private Long eventSeq;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;

    /**
     * 绑定所有者。已绑定会话不能被其他用户覆盖。
     */
    public void bindOwner(String userId) {
        if (StringUtils.isBlank(userId)) {
            return;
        }
        if (StringUtils.isNotBlank(this.userId) && !this.userId.equals(userId)) {
            throw new IllegalStateException("会话已绑定其他用户，不能覆盖所有者: sessionId=" + sessionId);
        }
        this.userId = userId;
    }

    /**
     * 重命名会话。空白标题视为无效输入，保持原值。
     */
    public void rename(String title) {
        if (StringUtils.isNotBlank(title)) {
            this.title = title;
        }
    }

    /**
     * 记录一次新的 run 开始：runCount 自增，并刷新最新活动信息。
     */
    public void startRun(String requestId, String queryText, LocalDateTime now) {
        int runs = normalizeCount(this.runCount) + 1;
        int finished = normalizeCount(this.finishedRunCount);
        int failed = normalizeCount(this.failedRunCount);
        validateInvariants(runs, finished, failed);
        this.runCount = runs;
        this.finishedRunCount = finished;
        this.failedRunCount = failed;
        this.latestRequestId = requestId;
        this.latestQueryText = queryText;
        this.status = ExecutionLedgerConstants.STATUS_RUNNING;
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        this.lastActiveAt = now;
    }

    /**
     * 记录一次 run 结束：finishedRunCount 自增，并刷新终态与最新摘要。
     */
    public void finishRun(Integer status, String summary, LocalDateTime now) {
        int runs = normalizeCount(this.runCount);
        int finished = normalizeCount(this.finishedRunCount) + 1;
        int failed = normalizeCount(this.failedRunCount);
        validateInvariants(runs, finished, failed);
        this.runCount = runs;
        this.finishedRunCount = finished;
        this.failedRunCount = failed;
        this.status = status;
        if (summary != null) {
            this.latestSummaryText = summary;
        }
        this.lastActiveAt = now;
    }

    /**
     * 记录一次失败 run。必须在对应 run 已计入 finishedRunCount 之后调用，否则违反不变量。
     */
    public void recordFailedRun(LocalDateTime now) {
        int runs = normalizeCount(this.runCount);
        int finished = normalizeCount(this.finishedRunCount);
        int failed = normalizeCount(this.failedRunCount) + 1;
        validateInvariants(runs, finished, failed);
        this.runCount = runs;
        this.finishedRunCount = finished;
        this.failedRunCount = failed;
        this.lastActiveAt = now;
    }

    /**
     * 校验会话计数不变量。任何一次状态转移后都必须成立。
     */
    public void assertInvariants() {
        validateInvariants(
                normalizeCount(this.runCount),
                normalizeCount(this.finishedRunCount),
                normalizeCount(this.failedRunCount)
        );
    }

    private void validateInvariants(int runs, int finished, int failed) {
        if (runs < 0) {
            throw new IllegalStateException("runCount 不得为负数: " + runs);
        }
        if (finished > runs) {
            throw new IllegalStateException("finishedRunCount 不得超过 runCount: finished=" + finished + ", runs=" + runs);
        }
        if (failed > finished) {
            throw new IllegalStateException("failedRunCount 不得超过 finishedRunCount: failed=" + failed + ", finished=" + finished);
        }
    }

    private int normalizeCount(Integer value) {
        return value == null ? 0 : value;
    }
}
