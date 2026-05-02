package org.wwz.ai.domain.agent.reactor.service.replay;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.model.valobj.ConversationRoleVO;
import org.wwz.ai.domain.agent.reactor.model.ledger.ConversationHistoryDetail;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunView;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueSessionView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionRunDetail;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.reactor.model.replay.ReplayFactBundle;
import org.wwz.ai.domain.agent.reactor.service.ExecutionLedgerQueryService;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史详情聚合服务。
 */
@RequiredArgsConstructor
public class ConversationHistoryReplayService {

    private final ExecutionLedgerQueryService executionLedgerQueryService;
    private final ReplayProjector replayProjector;
    private final HistoryReplayPrinter historyReplayPrinter;

    public ConversationHistoryDetail queryConversationHistory(String sessionId) {
        if (StringUtils.isBlank(sessionId) || executionLedgerQueryService == null) {
            return null;
        }
        DialogueSessionView session = executionLedgerQueryService.querySession(sessionId);
        if (session == null) {
            return null;
        }
        List<DialogueRunView> runs = executionLedgerQueryService.querySessionRuns(sessionId);
        List<ConversationHistoryDetail.ConversationRunDetail> runDetails = new ArrayList<>();
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
                runDetails.add(ConversationHistoryDetail.ConversationRunDetail.builder()
                        .requestId(run.getRequestId())
                        .status(run.getStatus())
                        .queryText(run.getQueryText())
                        .finalSummaryText(run.getFinalSummaryText())
                        .startedAt(run.getStartedAt())
                        .finishedAt(run.getFinishedAt())
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
                .outputStyle(resolveOutputStyle(session, runs))
                .deepThink(resolveDeepThink(session, runs))
                .role(resolveRole())
                .runCount(session.getRunCount())
                .finishedRunCount(session.getFinishedRunCount())
                .failedRunCount(session.getFailedRunCount())
                .startedAt(session.getStartedAt())
                .lastActiveAt(session.getLastActiveAt())
                .runs(runDetails)
                .build();
    }

    /**
     * 当前账本没有稳定保存 outputStyle，先根据 entryAgent 兜底：
     * react 视为结构化输出入口，plan_solve 视为深度研究；后续若补持久化字段，可只替换这里。
     */
    private String resolveOutputStyle(DialogueSessionView session, List<DialogueRunView> runs) {
        if (CollectionUtils.isEmpty(runs)) {
            return "chat";
        }
        DialogueRunView latestRun = runs.get(runs.size() - 1);
        if (latestRun == null) {
            return "chat";
        }
        return "plan_solve".equals(latestRun.getEntryAgent()) ? "docs" : "chat";
    }

    /**
     * 当前没有独立 deepThink 真相源，按 plan_solve entryAgent 推断为 true，其余保持 false。
     */
    private Boolean resolveDeepThink(DialogueSessionView session, List<DialogueRunView> runs) {
        if (CollectionUtils.isEmpty(runs)) {
            return Boolean.FALSE;
        }
        DialogueRunView latestRun = runs.get(runs.size() - 1);
        return latestRun != null && "plan_solve".equals(latestRun.getEntryAgent());
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
}
