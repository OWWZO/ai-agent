package org.wwz.ai.application.agent.featured;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.IFeaturedConversationRepository;
import org.wwz.ai.domain.agent.ledger.entity.FeaturedConversation;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ConversationRunReplay;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationCardView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPageResult;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPublicDetail;
import org.wwz.ai.domain.agent.ledger.replay.ConversationHistoryReplayService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精品对话公共查询应用服务。
 */
@Service
@RequiredArgsConstructor
public class FeaturedConversationPublicQueryApplicationService {

    private static final String ONLINE_STATUS = "ONLINE";
    private static final String CONTENT_UNAVAILABLE_REASON = "session_history_missing";
    private static final int DEFAULT_HISTORY_PAGE_SIZE = 20;

    private final IFeaturedConversationRepository featuredConversationRepository;
    private final ExecutionLedgerQueryService executionLedgerQueryService;
    private final ConversationHistoryReplayService conversationHistoryReplayService;

    public List<FeaturedConversationCardView> queryHomeCards(int limit) {
        int normalizedLimit = Math.max(1, limit);
        return featuredConversationRepository.queryOnlineList(0, normalizedLimit)
                .stream()
                .map(this::toCardView)
                .toList();
    }

    public FeaturedConversationPageResult<FeaturedConversationCardView> queryPublicList(
            int pageNo,
            int pageSize
    ) {
        int normalizedPageNo = Math.max(1, pageNo);
        int normalizedPageSize = Math.max(1, pageSize);
        int offset = (normalizedPageNo - 1) * normalizedPageSize;
        return FeaturedConversationPageResult.<FeaturedConversationCardView>builder()
                .total(featuredConversationRepository.countOnline())
                .list(featuredConversationRepository.queryOnlineList(offset, normalizedPageSize)
                        .stream()
                        .map(this::toCardView)
                        .toList())
                .build();
    }

    public FeaturedConversationPublicDetail queryDetail(String featuredId) {
        if (StringUtils.isBlank(featuredId)) {
            return null;
        }
        FeaturedConversation featured = featuredConversationRepository.queryByFeaturedId(featuredId);
        if (featured == null || !ONLINE_STATUS.equalsIgnoreCase(StringUtils.trimToEmpty(featured.getStatus()))) {
            return null;
        }

        LocalDateTime contentLastActiveAt = resolveContentLastActiveAt(featured.getSessionId());
        var historyPage = conversationHistoryReplayService == null
                ? null
                : conversationHistoryReplayService.queryConversationHistoryPage(
                featured.getSessionId(), DEFAULT_HISTORY_PAGE_SIZE, null);

        return FeaturedConversationPublicDetail.builder()
                .featuredId(featured.getFeaturedId())
                .sessionId(featured.getSessionId())
                .title(featured.getTitle())
                .summary(featured.getSummary())
                .coverUrl(featured.getCoverUrl())
                .tags(featured.getTags())
                .status(featured.getStatus())
                .publishedAt(featured.getPublishedAt())
                .contentLastActiveAt(contentLastActiveAt)
                .contentAvailable(historyPage != null)
                .contentUnavailableReason(historyPage == null ? CONTENT_UNAVAILABLE_REASON : null)
                .historyPage(historyPage)
                .build();
    }

    /**
     * 精品 replay 只允许访问已上线精品绑定的 session run。
     * 先查轻量 run 归属，再进入完整 projector，避免 requestId 横向读取其它 session。
     */
    public ConversationRunReplay queryRunReplay(String featuredId, String requestId) {
        if (StringUtils.isBlank(featuredId) || StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("featuredId 和 requestId 不能为空");
        }
        FeaturedConversation featured = featuredConversationRepository.queryByFeaturedId(featuredId);
        if (featured == null || !ONLINE_STATUS.equalsIgnoreCase(StringUtils.trimToEmpty(featured.getStatus()))) {
            throw new IllegalArgumentException("精品会话不存在或未上线");
        }
        DialogueRunView run = executionLedgerQueryService.queryRunSummary(requestId);
        if (run == null || !StringUtils.equals(featured.getSessionId(), run.getSessionId())) {
            throw new IllegalArgumentException("requestId 不属于该精品会话");
        }
        ConversationRunReplay replay = conversationHistoryReplayService.queryRunReplay(requestId);
        if (replay == null) {
            throw new IllegalArgumentException("run replay 不可用");
        }
        return replay;
    }

    private FeaturedConversationCardView toCardView(FeaturedConversation featured) {
        if (featured == null) {
            return null;
        }
        return FeaturedConversationCardView.builder()
                .featuredId(featured.getFeaturedId())
                .sessionId(featured.getSessionId())
                .title(featured.getTitle())
                .summary(featured.getSummary())
                .coverUrl(featured.getCoverUrl())
                .tags(featured.getTags())
                .publishedAt(featured.getPublishedAt())
                .contentLastActiveAt(resolveContentLastActiveAt(featured.getSessionId()))
                .build();
    }

    private LocalDateTime resolveContentLastActiveAt(String sessionId) {
        if (StringUtils.isBlank(sessionId) || executionLedgerQueryService == null) {
            return null;
        }
        DialogueSessionView session = executionLedgerQueryService.querySessionHistorySummary(sessionId);
        return session == null ? null : session.getLastActiveAt();
    }
}
