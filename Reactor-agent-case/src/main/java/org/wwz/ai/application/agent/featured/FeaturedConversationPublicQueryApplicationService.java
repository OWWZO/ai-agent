package org.wwz.ai.application.agent.featured;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.IFeaturedConversationRepository;
import org.wwz.ai.domain.agent.ledger.entity.FeaturedConversation;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
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
        var historyDetail = conversationHistoryReplayService == null
                ? null
                : conversationHistoryReplayService.queryConversationHistory(featured.getSessionId());

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
                .contentAvailable(historyDetail != null)
                .contentUnavailableReason(historyDetail == null ? CONTENT_UNAVAILABLE_REASON : null)
                .historyDetail(historyDetail)
                .build();
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
        DialogueSessionView session = executionLedgerQueryService.querySession(sessionId);
        return session == null ? null : session.getLastActiveAt();
    }
}
