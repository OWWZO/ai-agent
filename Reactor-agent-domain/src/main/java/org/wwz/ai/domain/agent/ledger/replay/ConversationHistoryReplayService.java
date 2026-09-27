package org.wwz.ai.domain.agent.ledger.replay;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryDetail;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryPage;
import org.wwz.ai.domain.agent.ledger.model.ConversationRunReplay;
import org.wwz.ai.domain.agent.ledger.model.ConversationRunSummary;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationView;
import org.wwz.ai.domain.agent.ledger.model.RunCursor;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayFactBundle;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.runtime.llm.ContextUsagePayload;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史摘要与回放聚合服务。
 * <p>
 * 摘要只读取 Execution Ledger 的 session/run 主表；单 run replay 再交给
 * ReplayProjector 生成前端事件。working memory 仅服务下一轮 prompt，不参与用户可见历史回放。
 */
@RequiredArgsConstructor
public class ConversationHistoryReplayService {

    public static final int DEFAULT_HISTORY_PAGE_SIZE = 20;
    public static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final int SUMMARY_PREVIEW_LENGTH = 240;

    private final ExecutionLedgerQueryService executionLedgerQueryService;
    private final ReplayProjector replayProjector;
    private final HistoryReplayPrinter historyReplayPrinter;
    private final LlmModelCatalog llmModelCatalog;

    /**
     * 读取会话轻量摘要。该路径只访问 session/run 主账本，不触发 replay projector、
     * tool output reader 或 artifact 详情查询。
     */
    public ConversationHistoryPage queryConversationHistoryPage(String sessionId,
                                                                 Integer limit,
                                                                 String after) {
        if (StringUtils.isBlank(sessionId) || executionLedgerQueryService == null) {
            return null;
        }
        DialogueSessionView session = executionLedgerQueryService.querySessionHistorySummary(sessionId);
        if (session == null) {
            return null;
        }

        int pageSize = normalizeHistoryPageSize(limit);
        RunCursor cursor = StringUtils.isBlank(after) ? null : RunCursor.decode(after);
        if (cursor != null && !StringUtils.equals(sessionId, cursor.getSessionId())) {
            throw new IllegalArgumentException("cursor 不属于当前 session");
        }

        List<DialogueRunView> pageRuns = executionLedgerQueryService.querySessionRuns(
                sessionId,
                cursor,
                pageSize + 1
        );
        boolean hasMore = pageRuns.size() > pageSize;
        if (hasMore) {
            pageRuns = new ArrayList<>(pageRuns.subList(0, pageSize));
        }

        DialogueRunView latestRun = resolveLatestRun(session, pageRuns);
        return ConversationHistoryPage.builder()
                .sessionId(session.getSessionId())
                .title(session.getTitle())
                .status(resolveSessionStatus(session, pageRuns))
                .deepThink(resolveDeepThink(latestRun))
                .latestRequestId(session.getLatestRequestId())
                .latestQueryPreview(preview(session.getLatestQueryText()))
                .latestSummaryPreview(preview(session.getLatestSummaryText()))
                .runCount(session.getRunCount())
                .finishedRunCount(session.getFinishedRunCount())
                .failedRunCount(session.getFailedRunCount())
                .startedAt(session.getStartedAt())
                .lastActiveAt(session.getLastActiveAt())
                .runs(pageRuns.stream().map(this::toRunSummary).toList())
                .nextCursor(hasMore && !pageRuns.isEmpty()
                        ? RunCursor.from(pageRuns.get(pageRuns.size() - 1)).encode()
                        : null)
                .hasMore(hasMore)
                .build();
    }

    /**
     * 单 run 完整回放。只有该路径允许加载账本明细、rich output 和 artifact，
     * 最终事件仍由现有 ReplayProjector/HistoryReplayPrinter 生成。
     */
    public ConversationRunReplay queryRunReplay(String requestId) {
        if (StringUtils.isBlank(requestId) || executionLedgerQueryService == null) {
            return null;
        }
        ExecutionRunDetail runDetail = executionLedgerQueryService.queryRunDetail(requestId);
        if (runDetail == null || runDetail.getRun() == null) {
            return null;
        }
        DialogueRunView run = runDetail.getRun();
        if (run.getArtifactSummaries() == null && runDetail.getArtifacts() != null) {
            run.setArtifactSummaries(runDetail.getArtifacts());
        }
        ReplayFactBundle bundle = ReplayFactBundle.builder()
                .run(run)
                .llmInvocations(runDetail.getLlmInvocations())
                .toolInvocations(runDetail.getToolInvocations())
                .artifacts(runDetail.getArtifacts())
                .build();
        List<GptProcessResult> frames =
                replayProjector == null ? List.of() : replayProjector.projectHistoryFrames(bundle);
        return ConversationRunReplay.builder()
                .run(run)
                .contextUsage(resolveContextUsage(runDetail.getLlmInvocations()))
                .replayFrames(historyReplayPrinter == null
                        ? frames
                        : historyReplayPrinter.ensureReadableConclusion(run, frames))
                .build();
    }

    public ConversationHistoryDetail queryConversationHistory(String sessionId) {
        if (StringUtils.isBlank(sessionId) || executionLedgerQueryService == null) {
            return null;
        }
        DialogueSessionView session = executionLedgerQueryService.querySession(sessionId);
        if (session == null) {
            return null;
        }
        // session 只提供会话头，具体 run/LLM/tool/artifact 事实由 projector 统一组装。
        List<DialogueRunView> runs = new ArrayList<>();
        List<ExecutionRunDetail> batchRunDetails = new ArrayList<>();
        int offset = 0;
        final int pageSize = ExecutionLedgerQueryService.DEFAULT_SESSION_RUN_PAGE_SIZE;
        while (true) {
            List<DialogueRunView> page = executionLedgerQueryService.querySessionRuns(sessionId, offset, pageSize);
            if (CollectionUtils.isEmpty(page)) {
                break;
            }
            runs.addAll(page);
            // 每页批量补齐事实，避免逐 run 调 queryRunDetail，也避免一次读取无界 run 列表。
            batchRunDetails.addAll(executionLedgerQueryService.queryRunDetails(page));
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        List<ConversationHistoryDetail.ConversationRunDetail> runDetails = new ArrayList<>();
        HistoryModeSnapshot historyModeSnapshot = HistoryModeSnapshot.defaultReact();
        if (CollectionUtils.isNotEmpty(runs)) {
            for (int index = 0; index < runs.size(); index++) {
                DialogueRunView run = runs.get(index);
                if (run == null || StringUtils.isBlank(run.getRequestId())) {
                    continue;
                }
                ExecutionRunDetail runDetail = batchRunDetails != null && index < batchRunDetails.size()
                        ? batchRunDetails.get(index)
                        : null;
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

    private int normalizeHistoryPageSize(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_HISTORY_PAGE_SIZE;
        }
        return Math.min(limit, MAX_HISTORY_PAGE_SIZE);
    }

    private DialogueRunView resolveLatestRun(DialogueSessionView session,
                                             List<DialogueRunView> pageRuns) {
        if (session != null && StringUtils.isNotBlank(session.getLatestRequestId())) {
            DialogueRunView latest = executionLedgerQueryService.queryRunSummary(session.getLatestRequestId());
            if (latest != null) {
                return latest;
            }
        }
        return CollectionUtils.isEmpty(pageRuns) ? null : pageRuns.get(pageRuns.size() - 1);
    }

    private ConversationRunSummary toRunSummary(DialogueRunView run) {
        if (run == null) {
            return null;
        }
        return ConversationRunSummary.builder()
                .requestId(run.getRequestId())
                .entryAgent(run.getEntryAgent())
                .status(run.getStatus())
                .queryPreview(preview(run.getQueryText()))
                .finalSummaryPreview(preview(run.getFinalSummaryText()))
                .llmCallCount(run.getLlmCallCount())
                .toolCallCount(run.getToolCallCount())
                .artifactCount(run.getArtifactCount())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .durationMs(run.getDurationMs())
                .hasReplay(StringUtils.isNotBlank(run.getRequestId()))
                .build();
    }

    private String preview(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return StringUtils.abbreviate(StringUtils.trim(value), SUMMARY_PREVIEW_LENGTH);
    }

    private Boolean resolveDeepThink(DialogueRunView run) {
        return run != null && ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE.equals(
                StringUtils.trimToEmpty(run.getEntryAgent()));
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
