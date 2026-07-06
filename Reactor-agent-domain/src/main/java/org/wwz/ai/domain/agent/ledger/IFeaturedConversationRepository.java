package org.wwz.ai.domain.agent.ledger;

import org.wwz.ai.domain.agent.ledger.entity.FeaturedConversation;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationAdminView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPageResult;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationQueryCondition;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationUpsertCommand;

import java.util.List;

/**
 * 精品对话读写仓储端口。
 */
public interface IFeaturedConversationRepository {

    FeaturedConversation queryByFeaturedId(String featuredId);

    FeaturedConversation queryBySessionId(String sessionId);

    List<FeaturedConversation> queryOnlineList(int offset, int limit);

    int countOnline();

    FeaturedConversationPageResult<FeaturedConversationAdminView> queryAdminList(
            FeaturedConversationQueryCondition condition
    );

    boolean upsert(FeaturedConversationUpsertCommand command);

    boolean updateStatus(String featuredId, String status, String operator);
}
