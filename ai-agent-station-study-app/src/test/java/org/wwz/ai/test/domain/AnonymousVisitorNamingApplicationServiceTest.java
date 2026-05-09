package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.visitor.AnonymousVisitorNamingApplicationService;
import org.wwz.ai.domain.agent.adapter.repository.IAnonymousVisitorRepository;
import org.wwz.ai.domain.agent.visitor.entity.AnonymousVisitorRecord;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 匿名访客首次命名应用服务测试。
 */
public class AnonymousVisitorNamingApplicationServiceTest {

    @Test
    public void shouldBindUsernameForUnnamedVisitor() {
        InMemoryAnonymousVisitorRepository repository = new InMemoryAnonymousVisitorRepository();
        repository.save(newVisitor("visitor-001", null));

        AnonymousVisitorNamingApplicationService service = new AnonymousVisitorNamingApplicationService(repository);

        service.bindUsername("visitor-001", "Alice");

        Assert.assertEquals("Alice", repository.readUsername("visitor-001"));
    }

    @Test
    public void shouldRejectRenameForNamedVisitor() {
        InMemoryAnonymousVisitorRepository repository = new InMemoryAnonymousVisitorRepository();
        repository.save(newVisitor("visitor-002", "Alice"));

        AnonymousVisitorNamingApplicationService service = new AnonymousVisitorNamingApplicationService(repository);

        try {
            service.bindUsername("visitor-002", "Bob");
            Assert.fail("已命名访客重复命名应被拒绝");
        } catch (IllegalStateException exception) {
            Assert.assertTrue(exception.getMessage().contains("已命名") || exception.getMessage().contains("不可修改"));
        }
    }

    @Test
    public void shouldAllowSameUsernameAcrossDifferentVisitors() {
        InMemoryAnonymousVisitorRepository repository = new InMemoryAnonymousVisitorRepository();
        repository.save(newVisitor("visitor-003", null));
        repository.save(newVisitor("visitor-004", null));

        AnonymousVisitorNamingApplicationService service = new AnonymousVisitorNamingApplicationService(repository);

        service.bindUsername("visitor-003", "Alice");
        service.bindUsername("visitor-004", "Alice");

        Assert.assertEquals("Alice", repository.readUsername("visitor-003"));
        Assert.assertEquals("Alice", repository.readUsername("visitor-004"));
    }

    @Test
    public void shouldRejectBlankUsername() {
        InMemoryAnonymousVisitorRepository repository = new InMemoryAnonymousVisitorRepository();
        repository.save(newVisitor("visitor-005", null));

        AnonymousVisitorNamingApplicationService service = new AnonymousVisitorNamingApplicationService(repository);

        try {
            service.bindUsername("visitor-005", "   ");
            Assert.fail("空用户名应被拒绝");
        } catch (IllegalArgumentException exception) {
            Assert.assertTrue(exception.getMessage().contains("不能为空"));
        }
    }

    private static AnonymousVisitorRecord newVisitor(String visitorId, String username) {
        LocalDateTime now = LocalDateTime.of(2026, 5, 8, 12, 0, 0);
        return AnonymousVisitorRecord.builder()
                .visitorId(visitorId)
                .tokenDigest("token-" + visitorId)
                .status(1)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .lastIp("127.0.0.1")
                .lastUserAgent("JUnit")
                .username(username)
                .deleted(0)
                .build();
    }

    /**
     * 访客仓储内存桩。
     */
    private static final class InMemoryAnonymousVisitorRepository implements IAnonymousVisitorRepository {

        private final Map<String, AnonymousVisitorRecord> byTokenDigest = new LinkedHashMap<>();
        private final Map<String, AnonymousVisitorRecord> byVisitorId = new LinkedHashMap<>();

        @Override
        public AnonymousVisitorRecord queryByTokenDigest(String tokenDigest) {
            return byTokenDigest.get(tokenDigest);
        }

        @Override
        public void save(AnonymousVisitorRecord record) {
            byTokenDigest.put(record.getTokenDigest(), record);
            byVisitorId.put(record.getVisitorId(), record);
        }

        @Override
        public void touchVisitor(String visitorId, LocalDateTime lastSeenAt, String lastIp, String lastUserAgent) {
            AnonymousVisitorRecord record = byVisitorId.get(visitorId);
            if (record == null) {
                return;
            }
            record.setLastSeenAt(lastSeenAt);
            record.setLastIp(lastIp);
            record.setLastUserAgent(lastUserAgent);
        }

        @Override
        public AnonymousVisitorRecord queryByVisitorId(String visitorId) {
            return byVisitorId.get(visitorId);
        }

        @Override
        public boolean bindUsernameIfAbsent(String visitorId, String username, LocalDateTime updateTime) {
            AnonymousVisitorRecord record = byVisitorId.get(visitorId);
            if (record == null || record.getUsername() != null) {
                return false;
            }
            record.setUsername(username);
            record.setUpdateTime(updateTime);
            return true;
        }

        private String readUsername(String visitorId) {
            AnonymousVisitorRecord record = byVisitorId.get(visitorId);
            return record == null ? null : record.getUsername();
        }
    }
}
