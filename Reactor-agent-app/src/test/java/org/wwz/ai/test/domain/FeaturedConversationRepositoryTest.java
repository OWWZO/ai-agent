package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.entity.FeaturedConversation;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationQueryCondition;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationUpsertCommand;
import org.wwz.ai.infrastructure.adapter.repository.FeaturedConversationRepository;
import org.wwz.ai.infrastructure.dao.po.FeaturedConversationPO;
import org.wwz.ai.infrastructure.dao.reactor.IFeaturedConversationDao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 精品对话仓储适配回归测试。
 */
public class FeaturedConversationRepositoryTest {

    @Test
    public void shouldSerializeTagsWhenUpsertingAndRestoreThemWhenQuerying() {
        InMemoryFeaturedConversationDao dao = new InMemoryFeaturedConversationDao();
        FeaturedConversationRepository repository = new FeaturedConversationRepository(dao);

        repository.upsert(FeaturedConversationUpsertCommand.builder()
                .featuredId("featured-storage-001")
                .sessionId("session-storage-001")
                .title("精品案例")
                .summary("摘要")
                .tags(List.of("研究", "报告"))
                .sortOrder(10)
                .operator("admin")
                .build());

        FeaturedConversation conversation = repository.queryByFeaturedId("featured-storage-001");

        Assert.assertNotNull(conversation);
        Assert.assertEquals(List.of("研究", "报告"), conversation.getTags());
        Assert.assertEquals("OFFLINE", conversation.getStatus());
    }

    @Test
    public void shouldSwitchStatusWithoutMutatingSessionBinding() {
        InMemoryFeaturedConversationDao dao = new InMemoryFeaturedConversationDao();
        FeaturedConversationRepository repository = new FeaturedConversationRepository(dao);
        dao.seed("featured-storage-002", "session-storage-002", "[\"写作\"]", "ONLINE");

        repository.updateStatus("featured-storage-002", "OFFLINE", "admin");

        FeaturedConversation conversation = repository.queryByFeaturedId("featured-storage-002");
        Assert.assertNotNull(conversation);
        Assert.assertEquals("OFFLINE", conversation.getStatus());
        Assert.assertEquals("session-storage-002", conversation.getSessionId());
    }

    private static final class InMemoryFeaturedConversationDao
            implements IFeaturedConversationDao {

        private final Map<String, FeaturedConversationPO> storage = new LinkedHashMap<>();

        void seed(String featuredId, String sessionId, String tagsJson, String status) {
            FeaturedConversationPO po = FeaturedConversationPO.builder()
                    .id((long) (storage.size() + 1))
                    .featuredId(featuredId)
                    .sessionId(sessionId)
                    .title("seed")
                    .summary("seed")
                    .tagsJson(tagsJson)
                    .sortOrder(0)
                    .status(status)
                    .publishedBy("seed")
                    .publishedAt(LocalDateTime.of(2026, 7, 6, 12, 0, 0))
                    .updatedBy("seed")
                    .updatedAt(LocalDateTime.of(2026, 7, 6, 12, 0, 0))
                    .deleted(0)
                    .build();
            storage.put(featuredId, po);
        }

        @Override
        public int upsert(FeaturedConversationPO po) {
            FeaturedConversationPO target = po.toBuilder()
                    .id(storage.containsKey(po.getFeaturedId())
                            ? storage.get(po.getFeaturedId()).getId()
                            : (long) (storage.size() + 1))
                    .build();
            storage.put(target.getFeaturedId(), target);
            return 1;
        }

        @Override
        public FeaturedConversationPO queryByFeaturedId(String featuredId) {
            return clonePo(storage.get(featuredId));
        }

        @Override
        public FeaturedConversationPO queryBySessionId(String sessionId) {
            return storage.values().stream()
                    .filter(item -> sessionId.equals(item.getSessionId()))
                    .findFirst()
                    .map(InMemoryFeaturedConversationDao::clonePo)
                    .orElse(null);
        }

        @Override
        public List<FeaturedConversationPO> queryOnlineList(int offset, int limit) {
            return storage.values().stream()
                    .filter(item -> "ONLINE".equals(item.getStatus()))
                    .skip(offset)
                    .limit(limit)
                    .map(InMemoryFeaturedConversationDao::clonePo)
                    .toList();
        }

        @Override
        public Integer countOnline() {
            return (int) storage.values().stream()
                    .filter(item -> "ONLINE".equals(item.getStatus()))
                    .count();
        }

        @Override
        public int updateStatus(
                String featuredId,
                String status,
                String updatedBy,
                LocalDateTime publishedAt,
                LocalDateTime updatedAt
        ) {
            FeaturedConversationPO existing = storage.get(featuredId);
            if (existing == null) {
                return 0;
            }
            existing.setStatus(status);
            existing.setUpdatedBy(updatedBy);
            existing.setPublishedAt(publishedAt);
            existing.setUpdatedAt(updatedAt);
            return 1;
        }

        @Override
        public List<FeaturedConversationPO> queryAdminList(FeaturedConversationQueryCondition condition) {
            return new ArrayList<>(storage.values()).stream()
                    .skip(condition.getOffset())
                    .limit(condition.getLimit())
                    .map(InMemoryFeaturedConversationDao::clonePo)
                    .toList();
        }

        @Override
        public Integer countAdminList(FeaturedConversationQueryCondition condition) {
            return storage.size();
        }

        private static FeaturedConversationPO clonePo(FeaturedConversationPO source) {
            if (source == null) {
                return null;
            }
            return source.toBuilder().build();
        }
    }
}
