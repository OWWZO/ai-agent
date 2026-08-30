package org.wwz.ai.trigger.http.agent;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.featured.FeaturedConversationPublicQueryApplicationService;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryDetail;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationCardView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPageResult;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPublicDetail;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.trigger.http.agent.vo.ConversationHistoryDetailRespVO;
import org.wwz.ai.trigger.http.agent.vo.FeaturedConversationCardRespVO;
import org.wwz.ai.trigger.http.agent.vo.FeaturedConversationDetailRespVO;
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
                .historyDetail(toHistoryDetailRespVO(detail.getHistoryDetail()))
                .build();
    }

    private ConversationHistoryDetailRespVO toHistoryDetailRespVO(ConversationHistoryDetail detail) {
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
                        .contextUsage(run.getContextUsage())
                        .replayFrames(run.getReplayFrames() == null ? List.of() : run.getReplayFrames())
                        .build())
                .collect(Collectors.toList());

        return ConversationHistoryDetailRespVO.builder()
                .sessionId(detail.getSessionId())
                .title(detail.getTitle())
                .status(resolveStatusLabel(detail.getStatus()))
                .deepThink(detail.getDeepThink())
                .runCount(detail.getRunCount())
                .finishedRunCount(detail.getFinishedRunCount())
                .failedRunCount(detail.getFailedRunCount())
                .startedAt(detail.getStartedAt())
                .lastActiveAt(detail.getLastActiveAt())
                .runs(runs)
                .build();
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
