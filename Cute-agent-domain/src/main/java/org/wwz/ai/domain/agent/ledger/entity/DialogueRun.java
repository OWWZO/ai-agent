package org.wwz.ai.domain.agent.ledger.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 单次对话执行总账。
 * <p>
 * 充血模型：状态转移由实体自身承载，Application / Repository 只负责加载、调用行为、保存，
 * 不再各自重复判断状态合法性。数据库整数状态编码保持不变。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueRun {

    private Long id;

    /** 对外稳定运行标识，首期直接复用 requestId */
    private String runUid;

    /** 单次请求ID */
    private String requestId;

    /** 会话ID */
    private String sessionId;

    /** 所属用户 ID */
    private String userId;

    /** 入口执行链 react / plan_solve */
    private String entryAgent;

    /** 运行状态 */
    private Integer status;

    /** 用户原始问题 */
    private String queryText;

    /** 最终总结文本 */
    private String finalSummaryText;

    /** LLM 调用次数 */
    private Integer llmCallCount;

    /** 工具调用次数 */
    private Integer toolCallCount;

    /** 产物数量 */
    private Integer artifactCount;

    /** LLM 输入 token 总量 */
    private Integer promptTokensTotal;

    /** LLM 输出 token 总量 */
    private Integer completionTokensTotal;

    /** LLM token 总量 */
    private Integer totalTokensTotal;

    /** 失败码 */
    private String errorCode;

    /** 失败信息 */
    private String errorMsg;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 总耗时 */
    private Long durationMs;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;

    /**
     * 进入运行态，并把统计计数归零，避免复用实例时残留上一轮计数。
     */
    public void start() {
        start(LocalDateTime.now());
    }

    public void start(LocalDateTime now) {
        if (StringUtils.isBlank(requestId)) {
            throw new IllegalStateException("DialogueRun.requestId 不能为空");
        }
        this.status = ExecutionLedgerConstants.STATUS_RUNNING;
        this.llmCallCount = 0;
        this.toolCallCount = 0;
        this.artifactCount = 0;
        this.promptTokensTotal = 0;
        this.completionTokensTotal = 0;
        this.totalTokensTotal = 0;
        this.errorCode = null;
        this.errorMsg = null;
        this.finishedAt = null;
        this.durationMs = null;
        if (this.startedAt == null) {
            this.startedAt = now;
        }
    }

    public void finishSuccess(String summary) {
        finishSuccess(summary, LocalDateTime.now());
    }

    public void finishSuccess(String summary, LocalDateTime now) {
        finish(ExecutionLedgerConstants.STATUS_SUCCESS, summary, null, null, now);
    }

    public void finishFailed(String errorCode, String errorMessage) {
        finishFailed(errorCode, errorMessage, LocalDateTime.now());
    }

    public void finishFailed(String errorCode, String errorMessage, LocalDateTime now) {
        finish(ExecutionLedgerConstants.STATUS_FAILED, this.finalSummaryText, errorCode, errorMessage, now);
    }

    public void finishStopped(String reason) {
        finishStopped(reason, LocalDateTime.now());
    }

    public void finishStopped(String reason, LocalDateTime now) {
        finish(ExecutionLedgerConstants.STATUS_STOPPED, this.finalSummaryText, "USER_STOP", reason, now);
    }

    /**
     * 是否已经进入终态（成功 / 失败 / 超时 / 停止）。
     */
    public boolean isFinished() {
        return isTerminal();
    }

    public boolean isTerminal() {
        return status != null
                && (status == ExecutionLedgerConstants.STATUS_SUCCESS
                || status == ExecutionLedgerConstants.STATUS_FAILED
                || status == ExecutionLedgerConstants.STATUS_TIMEOUT
                || status == ExecutionLedgerConstants.STATUS_STOPPED);
    }

    public boolean isRunning() {
        return status != null && status == ExecutionLedgerConstants.STATUS_RUNNING;
    }

    private void finish(Integer targetStatus,
                        String summary,
                        String errorCode,
                        String errorMessage,
                        LocalDateTime now) {
        if (isTerminal()) {
            throw new IllegalStateException("DialogueRun 已处于终态，不能重复结束: status=" + status);
        }
        LocalDateTime finishedAt = now == null ? LocalDateTime.now() : now;
        this.status = targetStatus;
        if (summary != null) {
            this.finalSummaryText = summary;
        }
        this.errorCode = errorCode;
        this.errorMsg = errorMessage;
        this.finishedAt = finishedAt;
        this.durationMs = this.startedAt == null
                ? null
                : Duration.between(this.startedAt, finishedAt).toMillis();
    }
}
