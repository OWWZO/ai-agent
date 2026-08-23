package org.wwz.ai.domain.agent.ledger.replay;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.model.valobj.ConversationRoleVO;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryDetail;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayFactBundle;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;
import org.wwz.ai.domain.agent.runtime.llm.ContextUsagePayload;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        HistoryModeSnapshot historyModeSnapshot = HistoryModeSnapshot.defaultChat();
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
                historyModeSnapshot = resolveHistoryModeSnapshot(run, runDetail, replayFrames);
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
                .outputStyle(historyModeSnapshot.getOutputStyle())
                .deepThink(historyModeSnapshot.getDeepThink())
                .role(resolveRole())
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

    /**
     * 历史详情先用 entry_agent 判断“大模式”：
     * react = 深度思考，plan_solve = 深度研究。
     * 具体输出样式再尽量从最新 run 的真实事实中补回，
     * 如果拿不到 html/ppt/table 等更细粒度信息，至少也要恢复成结构化 docs，
     * 不能再错误回落成 chat，避免前端输入栏切回聊天态。
     */
    private HistoryModeSnapshot resolveHistoryModeSnapshot(DialogueRunView run,
                                                          ExecutionRunDetail runDetail,
                                                          List<GptProcessResult> replayFrames) {
        if (run == null) {
            return HistoryModeSnapshot.defaultChat();
        }

        String entryAgent = StringUtils.trimToEmpty(run.getEntryAgent());
        if (ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE.equals(entryAgent)) {
            return new HistoryModeSnapshot(
                    StringUtils.defaultIfBlank(resolveStructuredOutputStyle(runDetail, replayFrames), "docs"),
                    Boolean.TRUE
            );
        }
        if (ExecutionLedgerConstants.ENTRY_AGENT_REACT.equals(entryAgent)) {
            return new HistoryModeSnapshot(
                    StringUtils.defaultIfBlank(resolveStructuredOutputStyle(runDetail, replayFrames), "docs"),
                    Boolean.FALSE
            );
        }
        return HistoryModeSnapshot.defaultChat();
    }

    /**
     * 结构化输出样式优先级：
     * 1. rich tool 强类型输出
     * 2. 历史 replay frame 中的 messageType
     * 3. 产物文件后缀
     */
    private String resolveStructuredOutputStyle(ExecutionRunDetail runDetail,
                                                List<GptProcessResult> replayFrames) {
        if (runDetail != null) {
            String styleFromTool = resolveOutputStyleFromToolInvocations(runDetail.getToolInvocations());
            if (StringUtils.isNotBlank(styleFromTool)) {
                return styleFromTool;
            }
        }

        String styleFromReplay = resolveOutputStyleFromReplayFrames(replayFrames);
        if (StringUtils.isNotBlank(styleFromReplay)) {
            return styleFromReplay;
        }

        if (runDetail != null) {
            return resolveOutputStyleFromArtifacts(runDetail.getArtifacts());
        }
        return null;
    }

    private String resolveOutputStyleFromToolInvocations(List<ToolInvocationView> toolInvocations) {
        if (CollectionUtils.isEmpty(toolInvocations)) {
            return null;
        }
        for (int index = toolInvocations.size() - 1; index >= 0; index -= 1) {
            ToolInvocationView invocation = toolInvocations.get(index);
            if (invocation == null) {
                continue;
            }
            ToolStructuredOutput structuredOutput = invocation.getStructuredOutput();
            if (structuredOutput instanceof ReportToolOutput reportToolOutput) {
                String style = normalizeOutputStyle(reportToolOutput.getFileType());
                if (StringUtils.isNotBlank(style)) {
                    return style;
                }
            }
        }
        return null;
    }

    private String resolveOutputStyleFromReplayFrames(List<GptProcessResult> replayFrames) {
        if (CollectionUtils.isEmpty(replayFrames)) {
            return null;
        }
        for (int index = replayFrames.size() - 1; index >= 0; index -= 1) {
            GptProcessResult replayFrame = replayFrames.get(index);
            if (replayFrame == null || replayFrame.getResultMap() == null) {
                continue;
            }
            Object eventDataObject = replayFrame.getResultMap().get("eventData");
            if (!(eventDataObject instanceof Map<?, ?> eventDataMap)) {
                continue;
            }
            Object resultMapObject = eventDataMap.get("resultMap");
            if (!(resultMapObject instanceof Map<?, ?> nestedResultMap)) {
                continue;
            }
            String style = normalizeOutputStyle(nestedResultMap.get("messageType"));
            if (StringUtils.isNotBlank(style)) {
                return style;
            }
        }
        return null;
    }

    private String resolveOutputStyleFromArtifacts(List<ArtifactView> artifacts) {
        if (CollectionUtils.isEmpty(artifacts)) {
            return null;
        }
        for (int index = artifacts.size() - 1; index >= 0; index -= 1) {
            ArtifactView artifact = artifacts.get(index);
            if (artifact == null) {
                continue;
            }
            String style = resolveOutputStyleFromFileName(artifact.getFileName());
            if (StringUtils.isNotBlank(style)) {
                return style;
            }
        }
        return null;
    }

    private String resolveOutputStyleFromFileName(String fileName) {
        if (StringUtils.isBlank(fileName) || !fileName.contains(".")) {
            return null;
        }
        String extension = StringUtils.substringAfterLast(fileName, ".").toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "html", "htm" -> "html";
            case "md", "markdown", "doc", "docx", "txt" -> "docs";
            case "ppt", "pptx" -> "ppt";
            case "csv", "xls", "xlsx" -> "table";
            default -> null;
        };
    }

    private String normalizeOutputStyle(Object rawType) {
        String normalized = StringUtils.trimToEmpty(rawType == null ? null : String.valueOf(rawType))
                .toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "html" -> "html";
            case "markdown", "docs" -> "docs";
            case "ppt" -> "ppt";
            case "table", "data_analysis" -> "table";
            default -> null;
        };
    }

    private ConversationRoleVO resolveRole() {
        return ConversationRoleVO.builder()
                .agentId(null)
                .agentName("默认助手")
                .available(true)
                .defaultRole(true)
                .build();
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

        private final String outputStyle;
        private final Boolean deepThink;

        private HistoryModeSnapshot(String outputStyle, Boolean deepThink) {
            this.outputStyle = outputStyle;
            this.deepThink = deepThink;
        }

        private static HistoryModeSnapshot defaultChat() {
            return new HistoryModeSnapshot("chat", Boolean.FALSE);
        }

        private String getOutputStyle() {
            return outputStyle;
        }

        private Boolean getDeepThink() {
            return deepThink;
        }
    }
}
