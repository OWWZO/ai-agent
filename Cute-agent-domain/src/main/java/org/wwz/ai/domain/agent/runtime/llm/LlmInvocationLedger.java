package org.wwz.ai.domain.agent.runtime.llm;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayTiming;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.time.LocalDateTime;

/**
 * LLM invocation 账本写入：开一条、收一条。
 * <p>
 * 所有写入都以「有活跃 run + 当前线程有 agentName」为闸门，缺失时 fail-open 返回
 * {@link Handle#disabled}，不让可选持久化阻断模型调用。
 * <p>
 * 时序不变量：prompt 观测快照必须在 {@link #start} 之前写入，
 * {@link #start} 从 {@link LlmPromptObservability#current()} 读取估算值。
 */
@Slf4j
final class LlmInvocationLedger {

    private final AgentContext context;
    private final String model;

    LlmInvocationLedger(AgentContext context, String model) {
        this.context = context;
        this.model = model;
    }

    /**
     * 开一条 invocation 账本行。
     *
     * @return 句柄；不可写账本时返回 disabled 句柄（携带计时但不含 invocationId）
     */
    Handle start(String callKind, boolean stream) {
        ReplayTiming runtimeTiming = ReplayTiming.startRuntime();
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null) {
            return Handle.disabled(runtimeTiming);
        }
        // invocation 的 prompt 估算和观测快照来自当前线程上下文，必须在异步切换前捕获。
        LocalDateTime startedAt = runtimeTiming.getStartedAt();
        int invocationSeq = context.getAgentRunState().nextInvocationSeq();
        String agentName = context.getAgentRunState().getCurrentAgentName();
        Integer stepNo = context.getAgentRunState().getCurrentStepNo();
        if (StringUtils.isBlank(agentName)) {
            log.error("{} skip llm invocation ledger insert: blank agentName, seq={}, stepNo={}, model={}, callKind={}, thread={}",
                    context.getRequestId(), invocationSeq, stepNo, model, callKind, Thread.currentThread().getName());
            return Handle.disabled(runtimeTiming);
        }
        log.info("{} start llm invocation seq={} agentName={} stepNo={} model={} callKind={} stream={} thread={}",
                context.getRequestId(), invocationSeq, agentName, stepNo, model, callKind, stream,
                Thread.currentThread().getName());
        LlmPromptObservability.ObservationBundle obs = LlmPromptObservability.current();
        TokenCounter.PromptEstimate est = obs == null ? null : obs.getEstimate();
        Long invocationId = context.getExecutionRecorder().createLlmInvocation(LlmInvocationStartRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .invocationSeq(invocationSeq)
                .agentName(agentName)
                .stepNo(stepNo)
                .callKind(callKind)
                .streaming(stream)
                .modelName(model)
                .startedAt(startedAt)
                .systemFingerprint(obs == null ? null : obs.getSystemFingerprint())
                .estTotalTokens(est == null ? null : est.getEstimatedTotalTokens())
                .estSystemTokens(est == null ? null : est.getSystemTokens())
                .estMessageTokens(est == null ? null : est.getMessageTokens())
                .estToolTokens(est == null ? null : est.getToolTokens())
                .messageCount(est == null ? null : est.getMessageCount())
                .toolCount(est == null ? null : est.getToolCount())
                .cacheStatus(obs == null ? null : obs.getCacheStatus())
                .cacheRiskFlags(obs == null ? null : obs.getCacheRiskFlags())
                .build());
        context.getAgentRunState().bindCurrentLlmInvocationId(invocationId);
        return new Handle(invocationId, obs, runtimeTiming);
    }

    /** 成功收口：从响应里取正文、推理正文、工具数、用量、结束原因。 */
    void finish(Handle handle, LLM.ToolCallResponse response) {
        restoreObservation(handle);
        completeTiming(handle);
        if (response != null) {
            response.setTiming(ReplayTiming.copyOf(handle.runtimeTiming()));
        }
        long durationMs = handle.runtimeTiming() == null || handle.runtimeTiming().getDurationMs() == null
                ? 0L
                : handle.runtimeTiming().getDurationMs();
        LlmUsageSnapshot usage = response == null ? LlmUsageSnapshot.empty() : response.toUsageSnapshot();
        LlmPromptObservability.logResponse(
                context,
                model,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL,
                usage,
                durationMs
        );
        write(handle,
                ExecutionLedgerConstants.STATUS_SUCCESS,
                response == null ? null : response.getContent(),
                response == null ? null : response.getReasoningContent(),
                response == null || response.getToolCalls() == null ? 0 : response.getToolCalls().size(),
                usage,
                response == null ? null : response.getFinishReason(),
                null);
    }

    /** 失败收口：状态按取消/超时/普通失败区分。 */
    void finish(Handle handle, Throwable throwable) {
        restoreObservation(handle);
        write(handle,
                resolveFailureStatus(throwable),
                null,
                null,
                0,
                null,
                null,
                throwable == null ? null : throwable.getMessage());
    }

    /** 不带 reasoningContent 的收口，等价于传 null。 */
    void finish(Handle handle,
                Integer status,
                String responseText,
                Integer toolCallCount,
                LlmUsageSnapshot usage,
                String finishReason,
                String errorMsg) {
        write(handle, status, responseText, null, toolCallCount, usage, finishReason, errorMsg);
    }

    /**
     * 账本终态的唯一写入口。
     *
     * @param status           ExecutionLedgerConstants.STATUS_*
     * @param responseText     已产出正文；失败/超时为 null
     * @param reasoningContent 模型原生 CoT
     * @param toolCallCount    本次响应解析出的工具数
     * @param usage            供应商返回的用量；null 视为空
     */
    void write(Handle handle,
               Integer status,
               String responseText,
               String reasoningContent,
               Integer toolCallCount,
               LlmUsageSnapshot usage,
               String finishReason,
               String errorMsg) {
        completeTiming(handle);
        if (context == null || handle == null || !handle.enabled()) {
            return;
        }
        // 完成时重新取回 invocation 创建阶段的观测快照，合并最终 token/cache 信息后一次性落账本。
        // 清理当前线程观测是必要的，否则复用线程会把本次模型调用的数据带进下一请求。
        restoreObservation(handle);
        LlmUsageSnapshot snapshot = usage == null ? LlmUsageSnapshot.empty() : usage;
        LlmPromptObservability.ObservationBundle obs = LlmPromptObservability.current();
        if (obs == null || obs.getUsage() == null || obs.getUsage().isEmpty()) {
            if (!snapshot.isEmpty()) {
                LlmPromptObservability.logResponse(context, model, "ask", snapshot, 0L);
                obs = LlmPromptObservability.current();
            }
        }
        context.getExecutionRecorder().finishLlmInvocation(LlmInvocationFinishRecord.builder()
                .llmInvocationId(handle.invocationId())
                .requestId(context.getRequestId())
                .status(status)
                .responseText(responseText)
                .reasoningContent(reasoningContent)
                .toolCallCount(toolCallCount)
                .promptTokens(snapshot.getPromptTokens())
                .completionTokens(snapshot.getCompletionTokens())
                .totalTokens(snapshot.getTotalTokens())
                .cachedPromptTokens(snapshot.getCachedPromptTokens())
                .promptTextTokens(snapshot.getPromptTextTokens())
                .promptAudioTokens(snapshot.getPromptAudioTokens())
                .promptImageTokens(snapshot.getPromptImageTokens())
                .completionTextTokens(snapshot.getCompletionTextTokens())
                .completionAudioTokens(snapshot.getCompletionAudioTokens())
                .reasoningTokens(snapshot.getReasoningTokens())
                .cacheStatus(obs == null ? null : obs.getCacheStatus())
                .cacheRiskFlags(obs == null ? null : obs.getCacheRiskFlags())
                .finishReason(finishReason)
                .errorMsg(errorMsg)
                .finishedAt(handle.runtimeTiming() == null || handle.runtimeTiming().getFinishedAt() == null
                        ? LocalDateTime.now()
                        : handle.runtimeTiming().getFinishedAt())
                .build());
        LlmPromptObservability.clear();
    }

    /** 用户停止优先于异常类型，避免停止被记成 FAILED。 */
    private int resolveFailureStatus(Throwable throwable) {
        if (context != null && context.isRunCancelled()) {
            return ExecutionLedgerConstants.STATUS_STOPPED;
        }
        return ExecutionLedgerConstants.resolveFailureStatus(throwable);
    }

    private static void restoreObservation(Handle handle) {
        if (handle == null || handle.observationBundle() == null) {
            return;
        }
        if (LlmPromptObservability.current() == null) {
            LlmPromptObservability.restore(handle.observationBundle());
        }
    }

    private static void completeTiming(Handle handle) {
        if (handle != null) {
            ReplayTiming.finishRuntime(handle.runtimeTiming());
        }
    }

    /**
     * 一次 invocation 的账本句柄：ID、创建时的观测快照、墙钟计时。
     */
    record Handle(Long invocationId,
                  LlmPromptObservability.ObservationBundle observationBundle,
                  ReplayTiming runtimeTiming) {

        static Handle disabled(ReplayTiming runtimeTiming) {
            return new Handle(null, null, runtimeTiming);
        }

        boolean enabled() {
            return invocationId != null;
        }
    }
}
