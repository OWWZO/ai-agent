package org.wwz.ai.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.ledger.IFeaturedConversationRepository;
import org.wwz.ai.domain.agent.ledger.entity.FeaturedConversation;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationAdminView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPageResult;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationQueryCondition;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationUpsertCommand;
import org.wwz.ai.infrastructure.dao.po.FeaturedConversationPO;
import org.wwz.ai.infrastructure.dao.reactor.IFeaturedConversationDao;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精品对话仓储适配器。
 */
@Repository
@RequiredArgsConstructor
public class FeaturedConversationRepository implements IFeaturedConversationRepository {

    private static final String ONLINE_STATUS = "ONLINE";
    private static final String OFFLINE_STATUS = "OFFLINE";

    private final IFeaturedConversationDao featuredConversationDao;

    @Override
    public FeaturedConversation queryByFeaturedId(String featuredId) {
        return toEntity(featuredConversationDao.queryByFeaturedId(featuredId));
    }

    @Override
    public FeaturedConversation queryBySessionId(String sessionId) {
        return toEntity(featuredConversationDao.queryBySessionId(sessionId));
    }

    @Override
    public List<FeaturedConversation> queryOnlineList(int offset, int limit) {
        return featuredConversationDao.queryOnlineList(offset, limit)
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public int countOnline() {
        Integer total = featuredConversationDao.countOnline();
        return total == null ? 0 : total;
    }

    @Override
    public FeaturedConversationPageResult<FeaturedConversationAdminView> queryAdminList(
            FeaturedConversationQueryCondition condition
    ) {
        FeaturedConversationQueryCondition safeCondition = condition == null
                ? FeaturedConversationQueryCondition.builder().offset(0).limit(20).build()
                : condition;
        Integer total = featuredConversationDao.countAdminList(safeCondition);
        List<FeaturedConversationAdminView> list = featuredConversationDao.queryAdminList(safeCondition)
                .stream()
                .map(this::toAdminView)
                .toList();
        return FeaturedConversationPageResult.<FeaturedConversationAdminView>builder()
                .total(total == null ? 0 : total)
                .list(list)
                .build();
    }

    @Override
    public boolean upsert(FeaturedConversationUpsertCommand command) {
        if (command == null
                || StringUtils.isBlank(command.getFeaturedId())
                || StringUtils.isBlank(command.getSessionId())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        FeaturedConversation existing = queryByFeaturedId(command.getFeaturedId());
        if (existing == null) {
            existing = queryBySessionId(command.getSessionId());
        }
        FeaturedConversationPO po = FeaturedConversationPO.builder()
                .featuredId(command.getFeaturedId())
                .sessionId(command.getSessionId())
                .title(command.getTitle())
                .summary(command.getSummary())
                .coverResourceKey(command.getCoverResourceKey())
                .coverUrl(command.getCoverUrl())
                .tagsJson(toTagsJson(command.getTags()))
                .sortOrder(command.getSortOrder() == null ? 0 : command.getSortOrder())
                .status(existing == null ? OFFLINE_STATUS : StringUtils.defaultIfBlank(existing.getStatus(), OFFLINE_STATUS))
                .publishedBy(existing == null ? command.getOperator() : existing.getPublishedBy())
                .publishedAt(existing == null ? null : existing.getPublishedAt())
                .updatedBy(command.getOperator())
                .updatedAt(now)
                .deleted(0)
                .build();
        return featuredConversationDao.upsert(po) > 0;
    }

    @Override
    public boolean updateStatus(String featuredId, String status, String operator) {
        if (StringUtils.isBlank(featuredId) || StringUtils.isBlank(status)) {
            return false;
        }
        FeaturedConversation existing = queryByFeaturedId(featuredId);
        if (existing == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        String normalizedStatus = StringUtils.upperCase(status);
        LocalDateTime publishedAt = ONLINE_STATUS.equals(normalizedStatus)
                ? now
                : existing.getPublishedAt();
        return featuredConversationDao.updateStatus(
                featuredId,
                normalizedStatus,
                operator,
                publishedAt,
                now
        ) > 0;
    }

    private FeaturedConversation toEntity(FeaturedConversationPO po) {
        if (po == null) {
            return null;
        }
        return FeaturedConversation.builder()
                .id(po.getId())
                .featuredId(po.getFeaturedId())
                .sessionId(po.getSessionId())
                .title(po.getTitle())
                .summary(po.getSummary())
                .coverResourceKey(po.getCoverResourceKey())
                .coverUrl(po.getCoverUrl())
                .tags(parseTags(po.getTagsJson()))
                .sortOrder(po.getSortOrder())
                .status(po.getStatus())
                .publishedBy(po.getPublishedBy())
                .publishedAt(po.getPublishedAt())
                .updatedBy(po.getUpdatedBy())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private FeaturedConversationAdminView toAdminView(FeaturedConversationPO po) {
        if (po == null) {
            return null;
        }
        return FeaturedConversationAdminView.builder()
                .featuredId(po.getFeaturedId())
                .sessionId(po.getSessionId())
                .title(po.getTitle())
                .summary(po.getSummary())
                .tags(parseTags(po.getTagsJson()))
                .coverUrl(po.getCoverUrl())
                .sortOrder(po.getSortOrder())
                .status(po.getStatus())
                .publishedAt(po.getPublishedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private List<String> parseTags(String tagsJson) {
        if (StringUtils.isBlank(tagsJson)) {
            return List.of();
        }
        List<String> tags = JSON.parseArray(tagsJson, String.class);
        return CollectionUtils.isEmpty(tags) ? List.of() : tags;
    }

    private String toTagsJson(List<String> tags) {
        return JSON.toJSONString(tags == null ? List.of() : tags);
    }
}
