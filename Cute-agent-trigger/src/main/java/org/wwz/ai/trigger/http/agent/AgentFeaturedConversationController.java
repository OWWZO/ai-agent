package org.wwz.ai.trigger.http.agent;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.featured.FeaturedConversationPublicQueryApplicationService;
import org.wwz.ai.application.agent.stream.AgentStreamFrameMapper;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryDetail;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryPage;
import org.wwz.ai.domain.agent.ledger.model.ConversationRunReplay;
import org.wwz.ai.domain.agent.ledger.model.ConversationRunSummary;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationCardView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPageResult;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPublicDetail;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;
import org.wwz.ai.trigger.http.agent.mapper.AgentStreamResponseMapper;
import org.wwz.ai.trigger.http.agent.vo.ConversationHistoryPageRespVO;
import org.wwz.ai.trigger.http.agent.vo.FeaturedConversationCardRespVO;
import org.wwz.ai.trigger.http.agent.vo.FeaturedConversationDetailRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationRunReplayRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationRunSummaryRespVO;
import org.wwz.ai.trigger.http.agent.vo.AgentStreamResponseVO;
import org.wwz.ai.trigger.http.agent.vo.PageRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 精品对话公共接口。
 */
@RestController
@RequestMapping("/api/agent/featured-conversations")
public class AgentFeaturedConversationController {

    @Resource
    private FeaturedConversationPublicQueryApplicationService featuredConversationPublicQueryApplicationService;

    @Resource
    private AgentStreamFrameMapper agentStreamFrameMapper = new AgentStreamFrameMapper();

    @Resource
    private AgentStreamResponseMapper agentStreamResponseMapper = new AgentStreamResponseMapper();

    @GetMapping("/home")
    public Response<List<FeaturedConversationCardRespVO>> home(
            @RequestParam(name = "limit", defaultValue = "6") Integer limit
    ) {
        List<FeaturedConversationCardRespVO> cards =
                featuredConversationPublicQueryApplicationService.queryHomeCards(limit == null ? 6 : limit)
                        .stream()
                        .map(this::toCardRespVO)
                        .collect(Collectors.toList());
        return Response.<List<FeaturedConversationCardRespVO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(cards)
                .build();
    }

    @GetMapping
    public Response<PageRespVO<FeaturedConversationCardRespVO>> list(
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        FeaturedConversationPageResult<FeaturedConversationCardView> pageResult =
                featuredConversationPublicQueryApplicationService.queryPublicList(
                        pageNo == null ? 1 : pageNo,
                        pageSize == null ? 20 : pageSize
                );

        PageRespVO<FeaturedConversationCardRespVO> pageRespVO = PageRespVO.<FeaturedConversationCardRespVO>builder()
                .total(pageResult == null ? 0 : pageResult.getTotal())
                .list(pageResult == null || CollectionUtils.isEmpty(pageResult.getList())
                        ? List.of()
                        : pageResult.getList().stream()
                        .map(this::toCardRespVO)
                        .collect(Collectors.toList()))
                .build();
        return Response.<PageRespVO<FeaturedConversationCardRespVO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(pageRespVO)
                .build();
    }

    @GetMapping("/{featuredId}")
    public Response<FeaturedConversationDetailRespVO> detail(
            @PathVariable("featuredId") String featuredId
    ) {
        FeaturedConversationPublicDetail detail =
                featuredConversationPublicQueryApplicationService.queryDetail(featuredId);
        return Response.<FeaturedConversationDetailRespVO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(toDetailRespVO(detail))
                .build();
    }

    @GetMapping("/{featuredId}/runs/{requestId}/replay")
    public Response<ConversationRunReplayRespVO> replay(
            @PathVariable("featuredId") String featuredId,
            @PathVariable("requestId") String requestId
    ) {
        try {
            ConversationRunReplay replay =
                    featuredConversationPublicQueryApplicationService.queryRunReplay(featuredId, requestId);
            return Response.<ConversationRunReplayRespVO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toReplayRespVO(replay))
                    .build();
        } catch (Exception exception) {
            return Response.<ConversationRunReplayRespVO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(exception.getMessage())
                    .build();
        }
    }

    private FeaturedConversationCardRespVO toCardRespVO(FeaturedConversationCardView card) {
        if (card == null) {
            return null;
        }
        return FeaturedConversationCardRespVO.builder()
                .featuredId(card.getFeaturedId())
                .sessionId(card.getSessionId())
                .title(card.getTitle())
                .summary(card.getSummary())
                .coverUrl(card.getCoverUrl())
                .tags(card.getTags())
                .publishedAt(card.getPublishedAt())
                .contentLastActiveAt(card.getContentLastActiveAt())
                .build();
    }

    private FeaturedConversationDetailRespVO toDetailRespVO(FeaturedConversationPublicDetail detail) {
        if (detail == null) {
            return null;
        }
        return FeaturedConversationDetailRespVO.builder()
                .featuredId(detail.getFeaturedId())
                .sessionId(detail.getSessionId())
                .title(detail.getTitle())
                .summary(detail.getSummary())
                .coverUrl(detail.getCoverUrl())
                .tags(detail.getTags())
                .status(detail.getStatus())
                .publishedAt(detail.getPublishedAt())
                .contentLastActiveAt(detail.getContentLastActiveAt())
                .contentAvailable(detail.getContentAvailable())
                .contentUnavailableReason(detail.getContentUnavailableReason())
                .historyDetail(toHistoryPageRespVO(detail.getHistoryPage(), detail.getHistoryDetail()))
                .build();
    }

    private ConversationHistoryPageRespVO toHistoryPageRespVO(ConversationHistoryPage page,
                                                              ConversationHistoryDetail legacyDetail) {
        if (page == null && legacyDetail == null) {
            return null;
        }
        if (page == null) {
            return ConversationHistoryPageRespVO.builder()
                    .sessionId(legacyDetail.getSessionId())
                    .title(legacyDetail.getTitle())
                    .status(resolveStatusLabel(legacyDetail.getStatus()))
                    .deepThink(legacyDetail.getDeepThink())
                    .runCount(legacyDetail.getRunCount())
                    .finishedRunCount(legacyDetail.getFinishedRunCount())
                    .failedRunCount(legacyDetail.getFailedRunCount())
                    .startedAt(legacyDetail.getStartedAt())
                    .lastActiveAt(legacyDetail.getLastActiveAt())
                    .runs(legacyDetail.getRuns() == null ? List.of() : legacyDetail.getRuns().stream()
                            .map(this::toLegacyRunSummary)
                            .collect(Collectors.toList()))
                    .build();
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

    private ConversationRunSummaryRespVO toLegacyRunSummary(ConversationHistoryDetail.ConversationRunDetail run) {
        return ConversationRunSummaryRespVO.builder()
                .requestId(run.getRequestId())
                .status(resolveStatusLabel(run.getStatus()))
                .queryPreview(run.getQueryText())
                .finalSummaryPreview(run.getFinalSummaryText())
                .finalSummaryText(run.getFinalSummaryText())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .build();
    }

    private ConversationRunSummaryRespVO toRunSummaryRespVO(ConversationRunSummary run) {
        return ConversationRunSummaryRespVO.builder()
                .requestId(run.getRequestId())
                .entryAgent(run.getEntryAgent())
                .status(resolveStatusLabel(run.getStatus()))
                .queryPreview(run.getQueryPreview())
                .finalSummaryPreview(run.getFinalSummaryPreview())
                .finalSummaryText(run.getFinalSummaryText())
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
        if (replay == null || replay.getRun() == null) {
            return null;
        }
        var run = replay.getRun();
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
                .replayFrames(toResponseFrames(replay.getReplayFrames()))
                .build();
    }

    private List<AgentStreamResponseVO> toResponseFrames(List<AgentStreamResult> frames) {
        if (frames == null) {
            return List.of();
        }
        return frames.stream()
                .map(agentStreamFrameMapper::toFrame)
                .map(agentStreamResponseMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 对外统一返回稳定可读状态，避免前端再维护第二套映射。
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
