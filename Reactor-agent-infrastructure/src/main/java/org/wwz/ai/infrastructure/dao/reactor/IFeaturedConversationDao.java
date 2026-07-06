package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationQueryCondition;
import org.wwz.ai.infrastructure.dao.po.FeaturedConversationPO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精品对话 DAO。
 */
@Mapper
public interface IFeaturedConversationDao {

    int upsert(FeaturedConversationPO po);

    FeaturedConversationPO queryByFeaturedId(@Param("featuredId") String featuredId);

    FeaturedConversationPO queryBySessionId(@Param("sessionId") String sessionId);

    List<FeaturedConversationPO> queryOnlineList(@Param("offset") int offset, @Param("limit") int limit);

    Integer countOnline();

    int updateStatus(@Param("featuredId") String featuredId,
                     @Param("status") String status,
                     @Param("updatedBy") String updatedBy,
                     @Param("publishedAt") LocalDateTime publishedAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    List<FeaturedConversationPO> queryAdminList(FeaturedConversationQueryCondition condition);

    Integer countAdminList(FeaturedConversationQueryCondition condition);
}
