package org.wwz.ai.domain.agent.ledger.replay;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryDetail;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationView;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayFactBundle;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.runtime.llm.ContextUsagePayload;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史详情聚合服务。
 * <p>
 * 查询只读取 Execution Ledger，再交给 ReplayProjector 生成前端事件；working memory
 * 仅服务下一轮 prompt，不参与用户可见历史回放。
 */
@RequiredArgsConstructor
public class ConversationHistoryReplayService {

    private final ExecutionLedgerQueryService executionLedgerQueryService;
    private final ReplayProjector replayProjector;
    private final HistoryReplayPrinter historyReplayPrinter;
    private final LlmModelCatalog llmModelCatalog;

    public ConversationHistoryDetail queryConversationHistory(String sessionId) {
        if (StringUtils.isBlank(sessionId) || executionLedgerQueryService == null) {
            return null;
        }
        DialogueSessionView session = executionLedgerQueryService.querySession(sessionId);
        if (session == null) {
            return null;
        }
        // session 只提供会话头，具体 run/LLM/tool/artifact 事实由 projector 统一组装。
        List<DialogueRunView> runs = executionLedgerQueryService.querySessionRuns(sessionId);
        List<ConversationHistoryDetail.ConversationRunDetail> runDetails = new ArrayList<>();
        HistoryModeSnapshot historyModeSnapshot = HistoryModeSnapshot.defaultReact();
        if (CollectionUtils.isNotEmpty(runs)) {
            for (DialogueRunView run : runs) {
                if (run == null || StringUtils.isBlank(run.getRequestId())) {
                    continue;
                }
                // 历史详情严格以 run 为最小回放单元：
                // 先查单 run 明细，再交给共享 projector 产出与实时同构的 replay frames。
                ExecutionRunDetail runDetail = executionLedgerQueryService.queryRunDetail(run.getRequestId());
                ReplayFactBundle bundle = ReplayFactBundle.builder()
                        .run(runDetail == null ? run : runDetail.getRun())
                        .llmInvocations(runDetail == null ? List.of() : runDetail.getLlmInvocations())
                        .toolInvocations(runDetail == null ? List.of() : runDetail.getToolInvocations())
                        .artifacts(runDetail == null ? List.of() : runDetail.getArtifacts())
                        .build();
                List<GptProcessResult> replayFrames = replayProjector == null
                        ? List.of()
                        : replayProjector.projectHistoryFrames(bundle);
                historyModeSnapshot = resolveHistoryModeSnapshot(run);
                runDetails.add(ConversationHistoryDetail.ConversationRunDetail.builder()
                        .requestId(run.getRequestId())
                        .status(run.getStatus())
                        .queryText(run.getQueryText())
                        .finalSummaryText(run.getFinalSummaryText())
                        .startedAt(run.getStartedAt())
                        .finishedAt(run.getFinishedAt())
                        .contextUsage(resolveContextUsage(runDetail == null ? List.of() : runDetail.getLlmInvocations()))
                        .replayFrames(historyReplayPrinter == null
                                ? replayFrames
                                : historyReplayPrinter.ensureReadableConclusion(run, replayFrames))
                        .build());
            }
        }

        return ConversationHistoryDetail.builder()
                .sessionId(session.getSessionId())
                .title(session.getTitle())
                .status(resolveSessionStatus(session, runs))
                .deepThink(historyModeSnapshot.getDeepThink())
                .runCount(session.getRunCount())
                .finishedRunCount(session.getFinishedRunCount())
                .failedRunCount(session.getFailedRunCount())
                .startedAt(session.getStartedAt())
                .lastActiveAt(session.getLastActiveAt())
                .runs(runDetails)
                .build();
    }

    private ContextUsagePayload resolveContextUsage(List<LlmInvocationView> invocations) {
        if (CollectionUtils.isEmpty(invocations)) {
            return null;
        }
        for (int index = invocations.size() - 1; index >= 0; index -= 1) {
            LlmInvocationView invocation = invocations.get(index);
            if (invocation == null) {
                continue;
            }
            int sys = nonNegative(invocation.getEstSystemTokens());
            int tools = nonNegative(invocation.getEstToolTokens());
            int history = nonNegative(invocation.getEstMessageTokens());
            int estimated = nonNegative(invocation.getEstTotalTokens());
            Integer promptTokens = invocation.getPromptTokens();
            Integer completionTokens = invocation.getCompletionTokens();
            int used = promptTokens != null && promptTokens > 0
                    ? promptTokens
                    : estimated > 0 ? estimated : sys + tools + history;
            if (used <= 0) {
                continue;
            }
            return ContextUsagePayload.builder()
                    .sys(sys)
                    .tools(tools)
                    .history(history)
                    .files(0)
                    .max(resolveContextWindow(invocation.getModelName()))
                    .used(used)
                    .estimatedTotal(estimated > 0 ? estimated : sys + tools + history)
                    .promptTokens(promptTokens != null && promptTokens > 0 ? promptTokens : null)
                    .completionTokens(completionTokens != null && completionTokens > 0 ? completionTokens : null)
                    .source(promptTokens != null && promptTokens > 0 ? "measured" : "estimate")
                    .build();
        }
        return null;
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private int resolveContextWindow(String modelName) {
        if (StringUtils.isNotBlank(modelName) && llmModelCatalog != null) {
            try {
                LLMSettings settings = llmModelCatalog.resolve(modelName).orElse(null);
                if (settings != null && settings.getMaxInputTokens() > 0) {
                    return settings.getMaxInputTokens();
                }
            } catch (Exception ignored) {
                // 历史回放不能因模型目录暂时不可用而失败。
            }
        }
        return 100_000;
    }

    /** 根据账本入口恢复仅有的两种 Agent 模式。 */
    private HistoryModeSnapshot resolveHistoryModeSnapshot(DialogueRunView run) {
        if (run == null) {
            return HistoryModeSnapshot.defaultReact();
        }

        String entryAgent = StringUtils.trimToEmpty(run.getEntryAgent());
        if (ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE.equals(entryAgent)) {
            return new HistoryModeSnapshot(Boolean.TRUE);
        }
        if (ExecutionLedgerConstants.ENTRY_AGENT_REACT.equals(entryAgent)) {
            return new HistoryModeSnapshot(Boolean.FALSE);
        }
        return HistoryModeSnapshot.defaultReact();
    }

    /**
     * 会话历史对外复用 run 的整型状态，保持与账本一致，
     * 具体的字符串化交给 trigger 层统一收口，避免多个层次重复维护枚举。
     */
    private Integer resolveSessionStatus(DialogueSessionView session, List<DialogueRunView> runs) {
        if (session != null && session.getStatus() != null) {
            return session.getStatus();
        }
        if (CollectionUtils.isEmpty(runs)) {
            return ExecutionLedgerConstants.STATUS_RUNNING;
        }
        DialogueRunView latestRun = runs.get(runs.size() - 1);
        return latestRun == null || latestRun.getStatus() == null
                ? ExecutionLedgerConstants.STATUS_RUNNING
                : latestRun.getStatus();
    }

    private static final class HistoryModeSnapshot {

        private final Boolean deepThink;

        private HistoryModeSnapshot(Boolean deepThink) {
            this.deepThink = deepThink;
        }

        private static HistoryModeSnapshot defaultReact() {
            return new HistoryModeSnapshot(Boolean.FALSE);
        }

        private Boolean getDeepThink() {
            return deepThink;
        }
    }
}
