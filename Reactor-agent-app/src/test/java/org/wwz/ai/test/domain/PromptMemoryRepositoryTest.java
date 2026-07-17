package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提示词记忆持久化仓储回归。
 */
public class PromptMemoryRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 10, 0, 0);

    @Test
    public void shouldReadCommittedMessagesInTurnAndSequenceOrder() {
        Fixture fixture = new Fixture();
        PromptMemoryLease firstLease = fixture.acquire("request-1");
        fixture.repository.publish(command(firstLease, 101L, message(RoleType.USER, "first-user"),
                message(RoleType.ASSISTANT, "first-assistant")));

        PromptMemoryLease secondLease = fixture.acquire("request-2");
        fixture.repository.publish(command(secondLease, 102L, message(RoleType.USER, "second-user")));

        Assert.assertEquals(List.of("first-user", "first-assistant", "second-user"), fixture.repository
                .loadReadyMessages(fixture.key)
                .stream()
                .map(PromptMemoryMessage::getContent)
                .toList());
    }

    @Test
    public void shouldPersistOnlyDeltaMessagesForLaterRequest() {
        Fixture fixture = new Fixture();
        PromptMemoryLease firstLease = fixture.acquire("request-1");
        fixture.repository.publish(command(firstLease, 101L, message(RoleType.USER, "first-user"),
                message(RoleType.ASSISTANT, "first-assistant")));

        PromptMemoryLease secondLease = fixture.acquire("request-2");
        fixture.repository.publish(command(secondLease, 102L, message(RoleType.USER, "second-user")));

        Assert.assertEquals(List.of(2, 1), fixture.messageDao.insertBatchSizes);
        Assert.assertEquals(3, fixture.messageDao.rows.size());
    }

    @Test
    public void shouldRejectCompetingLeaseWhileOwnerLeaseIsUnexpired() {
        Fixture fixture = new Fixture();

        Assert.assertTrue(fixture.repository.acquireLease(fixture.key, "request-owner", NOW, Duration.ofMinutes(1)).isPresent());
        Assert.assertTrue(fixture.repository.acquireLease(fixture.key, "request-contender", NOW.plusSeconds(1), Duration.ofMinutes(1)).isEmpty());
    }

    @Test
    public void shouldPublishSameRequestOnlyOnce() {
        Fixture fixture = new Fixture();
        PromptMemoryLease lease = fixture.acquire("request-idempotent");
        PromptMemoryPublishCommand command = command(lease, 101L, message(RoleType.USER, "once"));

        fixture.repository.publish(command);
        fixture.repository.publish(command);

        Assert.assertEquals(1, fixture.store.turns.size());
        Assert.assertEquals(1, fixture.messageDao.rows.size());
        Assert.assertEquals(1, fixture.streamDao.queryByKey(fixture.key).getLatestTurnSeq().intValue());
    }

    private static PromptMemoryPublishCommand command(PromptMemoryLease lease, Long runId,
                                                        PromptMemoryMessage... deltaMessages) {
        return PromptMemoryPublishCommand.builder()
                .lease(lease)
                .runId(runId)
                .deltaMessages(List.of(deltaMessages))
                .build();
    }

    private static PromptMemoryMessage message(RoleType role, String content) {
        return PromptMemoryMessage.builder().role(role).content(content).build();
    }

    private static final class Fixture {
        private final PromptMemoryStreamKey key = new PromptMemoryStreamKey("session-1", PromptMemoryScope.REACT,
                "prompt-contract-1", "tool-contract-1");
        private final MemoryStore store = new MemoryStore();
        private final InMemoryPromptMemoryStreamDao streamDao = new InMemoryPromptMemoryStreamDao(store);
        private final InMemoryPromptMemoryTurnDao turnDao = new InMemoryPromptMemoryTurnDao(store);
        private final InMemoryPromptMemoryMessageDao messageDao = new InMemoryPromptMemoryMessageDao(store);
        private final PromptMemoryRepository repository = new PromptMemoryRepository(streamDao, turnDao, messageDao);

        private PromptMemoryLease acquire(String requestId) {
            return repository.acquireLease(key, requestId, NOW, Duration.ofMinutes(1)).orElseThrow();
        }
    }

    private static final class MemoryStore {
        private long nextStreamId = 1L;
        private long nextTurnId = 1L;
        private final Map<String, PromptMemoryStream> streams = new LinkedHashMap<>();
        private final Map<String, PromptMemoryTurn> turns = new LinkedHashMap<>();
        private final List<PromptMemoryMessageRow> rows = new ArrayList<>();
    }

    private static final class InMemoryPromptMemoryStreamDao implements IPromptMemoryStreamDao {
        private final MemoryStore store;

        private InMemoryPromptMemoryStreamDao(MemoryStore store) {
            this.store = store;
        }

        @Override
        public int insertIgnore(PromptMemoryStream stream) {
            String identity = identity(stream.getSessionId(), stream.getMemoryScope(), stream.getPromptContractId(), stream.getToolContractId());
            if (store.streams.containsKey(identity)) {
                return 0;
            }
            stream.setId(store.nextStreamId++);
            store.streams.put(identity, stream);
            return 1;
        }

        @Override
        public PromptMemoryStream queryByKey(PromptMemoryStreamKey key) {
            return store.streams.get(identity(key.sessionId(), key.scope().name(), key.promptContractId(), key.toolContractId()));
        }

        @Override
        public int acquireLease(PromptMemoryStreamKey key, String requestId, LocalDateTime now, LocalDateTime leaseExpireAt) {
            PromptMemoryStream stream = queryByKey(key);
            if (stream == null || (stream.getActiveRequestId() != null && !requestId.equals(stream.getActiveRequestId())
                    && stream.getLeaseExpireAt().isAfter(now))) {
                return 0;
            }
            stream.setActiveRequestId(requestId);
            stream.setLeaseExpireAt(leaseExpireAt);
            stream.setVersion(stream.getVersion() + 1);
            return 1;
        }

        @Override
        public int releaseLease(Long streamId, String requestId) {
            PromptMemoryStream stream = findById(streamId);
            if (stream == null || !requestId.equals(stream.getActiveRequestId())) {
                return 0;
            }
            stream.setActiveRequestId(null);
            stream.setLeaseExpireAt(null);
            return 1;
        }

        @Override
        public int advanceReadyTurn(Long streamId, String requestId, Integer expectedLatestTurnSeq, Integer nextTurnSeq) {
            PromptMemoryStream stream = findById(streamId);
            if (stream == null || !requestId.equals(stream.getActiveRequestId())
                    || !expectedLatestTurnSeq.equals(stream.getLatestTurnSeq())) {
                return 0;
            }
            stream.setLatestTurnSeq(nextTurnSeq);
            stream.setActiveRequestId(null);
            stream.setLeaseExpireAt(null);
            return 1;
        }

        private PromptMemoryStream findById(Long id) {
            return store.streams.values().stream().filter(item -> id.equals(item.getId())).findFirst().orElse(null);
        }
    }

    private static final class InMemoryPromptMemoryTurnDao implements IPromptMemoryTurnDao {
        private final MemoryStore store;

        private InMemoryPromptMemoryTurnDao(MemoryStore store) {
            this.store = store;
        }

        @Override
        public PromptMemoryTurn queryByRequestId(String requestId) {
            return store.turns.get(requestId);
        }

        @Override
        public int insertBuilding(PromptMemoryTurn turn) {
            if (store.turns.containsKey(turn.getRequestId())) {
                return 0;
            }
            turn.setId(store.nextTurnId++);
            store.turns.put(turn.getRequestId(), turn);
            return 1;
        }

        @Override
        public int markReady(Long turnId) {
            PromptMemoryTurn turn = store.turns.values().stream().filter(item -> turnId.equals(item.getId())).findFirst().orElse(null);
            if (turn == null) {
                return 0;
            }
            turn.setStatus(PromptMemoryTurnStatus.READY);
            return 1;
        }
    }

    private static final class InMemoryPromptMemoryMessageDao implements IPromptMemoryMessageDao {
        private final MemoryStore store;
        private final List<Integer> insertBatchSizes = new ArrayList<>();
        private final List<PromptMemoryMessageRow> rows;

        private InMemoryPromptMemoryMessageDao(MemoryStore store) {
            this.store = store;
            this.rows = store.rows;
        }

        @Override
        public int batchInsert(Long turnId, List<PromptMemoryMessageRow> messages) {
            insertBatchSizes.add(messages.size());
            rows.addAll(messages);
            return messages.size();
        }

        @Override
        public List<PromptMemoryMessageRow> queryReadyByStreamId(Long streamId) {
            return rows.stream()
                    .filter(row -> store.turns.values().stream().anyMatch(turn -> turn.getId().equals(row.getTurnId())
                            && turn.getStreamId().equals(streamId) && turn.getStatus() == PromptMemoryTurnStatus.READY))
                    .sorted(Comparator.comparing((PromptMemoryMessageRow row) -> store.turns.values().stream()
                                    .filter(turn -> turn.getId().equals(row.getTurnId())).findFirst().orElseThrow().getTurnSeq())
                            .thenComparing(PromptMemoryMessageRow::getSeqNo))
                    .toList();
        }
    }

    private static String identity(String sessionId, String scope, String promptContractId, String toolContractId) {
        return String.join("|", sessionId, scope, promptContractId, toolContractId);
    }
}
