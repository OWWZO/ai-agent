package org.wwz.ai.trigger.http.agent;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.domain.agent.model.valobj.ConversationRoleVO;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryDetail;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.replay.ConversationHistoryReplayService;
import org.wwz.ai.trigger.http.agent.vo.ConversationHistoryDetailRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationRoleRespVO;
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
@RequestMapping("/api/agent/conversation/sessions")
public class AgentConversationHistoryController {

    @Resource
    private ExecutionLedgerQueryService executionLedgerQueryService;

    @Resource
    private ConversationHistoryReplayService conversationHistoryReplayService;

    @Resource
    private ConversationSessionOwnershipApplicationService conversationSessionOwnershipApplicationService;

    @GetMapping
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

    @GetMapping("/{sessionId}")
    public Response<ConversationHistoryDetailRespVO> detail(@PathVariable("sessionId") String sessionId) {
        try {
            conversationSessionOwnershipApplicationService.ensureExistingSessionAccessible(
                    VisitorRequestContext.requireVisitorId(),
                    sessionId
            );
        } catch (Exception e) {
            return Response.<ConversationHistoryDetailRespVO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
        ConversationHistoryDetail detail = conversationHistoryReplayService.queryConversationHistory(sessionId);
        return Response.<ConversationHistoryDetailRespVO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(toDetailRespVO(detail))
                .build();
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

    private ConversationHistoryDetailRespVO toDetailRespVO(ConversationHistoryDetail detail) {
        if (detail == null) {
            return null;
        }
        List<ConversationHistoryDetailRespVO.RunDetailRespVO> runs = CollectionUtils.isEmpty(detail.getRuns())
                ? List.of()
                : detail.getRuns().stream()
                .map(run -> ConversationHistoryDetailRespVO.RunDetailRespVO.builder()
                        .requestId(run.getRequestId())
                        .status(resolveStatusLabel(run.getStatus()))
                        .queryText(run.getQueryText())
                        .finalSummaryText(run.getFinalSummaryText())
                        .startedAt(run.getStartedAt())
                        .finishedAt(run.getFinishedAt())
                        .replayFrames(run.getReplayFrames() == null ? List.of() : run.getReplayFrames())
                        .build())
                .collect(Collectors.toList());

        return ConversationHistoryDetailRespVO.builder()
                .sessionId(detail.getSessionId())
                .title(detail.getTitle())
                .status(resolveStatusLabel(detail.getStatus()))
                .outputStyle(detail.getOutputStyle())
                .deepThink(detail.getDeepThink())
                .role(toRoleRespVO(detail.getRole()))
                .runCount(detail.getRunCount())
                .finishedRunCount(detail.getFinishedRunCount())
                .failedRunCount(detail.getFailedRunCount())
                .startedAt(detail.getStartedAt())
                .lastActiveAt(detail.getLastActiveAt())
                .runs(runs)
                .build();
    }

    private ConversationRoleRespVO toRoleRespVO(ConversationRoleVO role) {
        if (role == null) {
            return null;
        }
        return ConversationRoleRespVO.builder()
                .agentId(role.getAgentId())
                .agentName(role.getAgentName())
                .available(role.isAvailable())
                .defaultRole(role.isDefaultRole())
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
            default -> "RUNNING";
        };
    }
}
