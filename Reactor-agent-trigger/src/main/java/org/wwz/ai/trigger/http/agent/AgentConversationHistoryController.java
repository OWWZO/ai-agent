package org.wwz.ai.trigger.http.agent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryPage;
import org.wwz.ai.domain.agent.ledger.model.ConversationRunReplay;
import org.wwz.ai.domain.agent.ledger.model.ConversationRunSummary;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.replay.ConversationHistoryReplayService;
import org.wwz.ai.trigger.http.agent.vo.ConversationHistoryPageRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationRunReplayRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationRunSummaryRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationSessionRespVO;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话历史恢复接口。
 */
@RestController
@RequestMapping("/api/agent/conversation")
public class AgentConversationHistoryController {

    @Resource
    private ExecutionLedgerQueryService executionLedgerQueryService;

    @Resource
    private ConversationHistoryReplayService conversationHistoryReplayService;

    @Resource
    private ConversationSessionOwnershipApplicationService conversationSessionOwnershipApplicationService;

    @GetMapping("/sessions")
    public Response<List<ConversationSessionRespVO>> list(
            @RequestParam(name = "limit", defaultValue = "20") Integer limit) {
        String visitorId = VisitorRequestContext.requireVisitorId();
        List<ConversationSessionRespVO> sessions = executionLedgerQueryService.queryRecentSessions(visitorId, limit == null ? 20 : limit)
                .stream()
                .map(this::toSessionRespVO)
                .collect(Collectors.toList());

        return Response.<List<ConversationSessionRespVO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(sessions)
                .build();
    }

    @GetMapping("/sessions/{sessionId}")
    public Response<ConversationHistoryPageRespVO> detail(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(name = "limit", defaultValue = "20") Integer limit,
            @RequestParam(name = "after", required = false) String after) {
        try {
            conversationSessionOwnershipApplicationService.ensureExistingSessionAccessible(
                    VisitorRequestContext.requireVisitorId(),
                    sessionId
            );
            ConversationHistoryPage page = conversationHistoryReplayService.queryConversationHistoryPage(
                    sessionId,
                    limit,
                    after
            );
            return Response.<ConversationHistoryPageRespVO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toPageRespVO(page))
                    .build();
        } catch (Exception e) {
            return Response.<ConversationHistoryPageRespVO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    @GetMapping("/runs/{requestId}/replay")
    public Response<ConversationRunReplayRespVO> replay(
            @PathVariable("requestId") String requestId) {
        try {
            DialogueRunView run = executionLedgerQueryService.queryRunSummary(requestId);
            if (run == null || run.getSessionId() == null) {
                throw new IllegalArgumentException("requestId 对应的 run 不存在");
            }
            // 先由 requestId 定位 session，再校验当前 visitor，最后才加载 replay 明细。
            conversationSessionOwnershipApplicationService.ensureExistingSessionAccessible(
                    VisitorRequestContext.requireVisitorId(),
                    run.getSessionId()
            );
            ConversationRunReplay replay = conversationHistoryReplayService.queryRunReplay(requestId);
            if (replay == null) {
                throw new IllegalArgumentException("run replay 不可用");
            }
            return Response.<ConversationRunReplayRespVO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toReplayRespVO(replay))
                    .build();
        } catch (Exception e) {
            return Response.<ConversationRunReplayRespVO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    private ConversationSessionRespVO toSessionRespVO(DialogueSessionView session) {
        if (session == null) {
            return null;
        }
        return ConversationSessionRespVO.builder()
                .sessionId(session.getSessionId())
                .title(session.getTitle())
                .status(resolveStatusLabel(session.getStatus()))
                .latestQueryText(session.getLatestQueryText())
                .runCount(session.getRunCount())
                .finishedRunCount(session.getFinishedRunCount())
                .failedRunCount(session.getFailedRunCount())
                .startedAt(session.getStartedAt())
                .lastActiveAt(session.getLastActiveAt())
                .build();
    }

    private ConversationHistoryPageRespVO toPageRespVO(ConversationHistoryPage page) {
        if (page == null) {
            return null;
        }
        return ConversationHistoryPageRespVO.builder()
                .sessionId(page.getSessionId())
                .title(page.getTitle())
                .status(resolveStatusLabel(page.getStatus()))
                .deepThink(page.getDeepThink())
                .latestRequestId(page.getLatestRequestId())
                .latestQueryPreview(page.getLatestQueryPreview())
                .latestSummaryPreview(page.getLatestSummaryPreview())
                .runCount(page.getRunCount())
                .finishedRunCount(page.getFinishedRunCount())
                .failedRunCount(page.getFailedRunCount())
                .startedAt(page.getStartedAt())
                .lastActiveAt(page.getLastActiveAt())
                .runs(page.getRuns() == null ? List.of() : page.getRuns().stream()
                        .map(this::toRunSummaryRespVO)
                .collect(Collectors.toList()))
                .nextCursor(page.getNextCursor())
                .hasMore(page.isHasMore())
                .build();
    }

    private ConversationRunSummaryRespVO toRunSummaryRespVO(ConversationRunSummary run) {
        if (run == null) {
            return null;
        }
        return ConversationRunSummaryRespVO.builder()
                .requestId(run.getRequestId())
                .entryAgent(run.getEntryAgent())
                .status(resolveStatusLabel(run.getStatus()))
                .queryPreview(run.getQueryPreview())
                .finalSummaryPreview(run.getFinalSummaryPreview())
                .llmCallCount(run.getLlmCallCount())
                .toolCallCount(run.getToolCallCount())
                .artifactCount(run.getArtifactCount())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .durationMs(run.getDurationMs())
                .hasReplay(run.getHasReplay())
                .build();
    }

    private ConversationRunReplayRespVO toReplayRespVO(ConversationRunReplay replay) {
        DialogueRunView run = replay == null ? null : replay.getRun();
        if (run == null) {
            return null;
        }
        return ConversationRunReplayRespVO.builder()
                .runUid(run.getRunUid())
                .requestId(run.getRequestId())
                .sessionId(run.getSessionId())
                .entryAgent(run.getEntryAgent())
                .status(resolveStatusLabel(run.getStatus()))
                .queryText(run.getQueryText())
                .finalSummaryText(run.getFinalSummaryText())
                .llmCallCount(run.getLlmCallCount())
                .toolCallCount(run.getToolCallCount())
                .artifactCount(run.getArtifactCount())
                .errorCode(run.getErrorCode())
                .errorMsg(run.getErrorMsg())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .durationMs(run.getDurationMs())
                .contextUsage(replay.getContextUsage())
                .replayFrames(replay.getReplayFrames() == null ? List.of() : replay.getReplayFrames())
                .build();
    }

    /**
     * 对外接口统一返回可读终态，避免前端和调试工具重复维护状态枚举映射。
     */
    private String resolveStatusLabel(Integer status) {
        int normalized = status == null ? ExecutionLedgerConstants.STATUS_RUNNING : status;
        return switch (normalized) {
            case ExecutionLedgerConstants.STATUS_SUCCESS -> "SUCCESS";
            case ExecutionLedgerConstants.STATUS_FAILED -> "FAILED";
            case ExecutionLedgerConstants.STATUS_TIMEOUT -> "TIMEOUT";
            case ExecutionLedgerConstants.STATUS_STOPPED -> "STOPPED";
            case ExecutionLedgerConstants.STATUS_WAITING_INPUT -> "WAITING_INPUT";
            default -> "RUNNING";
        };
    }
}
