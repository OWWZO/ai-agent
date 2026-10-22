package org.wwz.ai.application.agent.featured;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.IFeaturedConversationRepository;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationAdminView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPageResult;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationQueryCondition;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationUpsertCommand;

/**
 * 精品对话管理应用服务。
 */
@Service
@RequiredArgsConstructor
public class FeaturedConversationAdminApplicationService {

    private static final String ONLINE_STATUS = "ONLINE";
    private static final String OFFLINE_STATUS = "OFFLINE";

    private final IFeaturedConversationRepository featuredConversationRepository;
    private final ExecutionLedgerQueryService executionLedgerQueryService;

    public boolean create(FeaturedConversationUpsertCommand command) {
        validateCreateCommand(command);
        String sessionId = StringUtils.trim(command.getSessionId());
        if (executionLedgerQueryService.querySession(sessionId) == null) {
            throw new IllegalArgumentException("sessionId 对应会话不存在");
        }
        return featuredConversationRepository.upsert(FeaturedConversationUpsertCommand.builder()
                .featuredId("featured_" + sessionId)
                .sessionId(sessionId)
                .title(command.getTitle())
                .summary(command.getSummary())
                .coverResourceKey(command.getCoverResourceKey())
                .coverUrl(command.getCoverUrl())
                .tags(command.getTags())
                .sortOrder(command.getSortOrder())
                .operator(command.getOperator())
                .build());
    }

    public boolean update(FeaturedConversationUpsertCommand command) {
        if (command == null || StringUtils.isBlank(command.getFeaturedId())) {
            throw new IllegalArgumentException("featuredId 不能为空");
        }
        if (featuredConversationRepository.queryByFeaturedId(command.getFeaturedId()) == null) {
            throw new IllegalArgumentException("featuredId 不存在");
        }
        return featuredConversationRepository.upsert(command);
    }

    public boolean online(String featuredId, String operator) {
        return featuredConversationRepository.updateStatus(featuredId, ONLINE_STATUS, operator);
    }

    public boolean offline(String featuredId, String operator) {
        return featuredConversationRepository.updateStatus(featuredId, OFFLINE_STATUS, operator);
    }

    public FeaturedConversationPageResult<FeaturedConversationAdminView> queryList(
            FeaturedConversationQueryCondition condition
    ) {
        return featuredConversationRepository.queryAdminList(condition);
    }

    private void validateCreateCommand(FeaturedConversationUpsertCommand command) {
        if (command == null || StringUtils.isBlank(command.getSessionId())) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
    }
}
