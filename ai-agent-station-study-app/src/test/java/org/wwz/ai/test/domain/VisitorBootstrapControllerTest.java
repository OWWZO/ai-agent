package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.visitor.AnonymousVisitorBootstrapApplicationService;
import org.wwz.ai.application.agent.visitor.AnonymousVisitorNamingApplicationService;
import org.wwz.ai.domain.agent.adapter.repository.IAnonymousVisitorRepository;
import org.wwz.ai.domain.agent.visitor.entity.AnonymousVisitorRecord;
import org.wwz.ai.trigger.http.agent.AgentVisitorController;
import org.wwz.ai.trigger.http.agent.vo.VisitorBootstrapRespVO;
import org.wwz.ai.trigger.http.agent.vo.VisitorNamingReqVO;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 访客 bootstrap 接口测试。
 */
public class VisitorBootstrapControllerTest {

    @Test
    public void shouldReturnUnnamedVisitorBootstrapState() {
        InMemoryAnonymousVisitorRepository repository = new InMemoryAnonymousVisitorRepository();
        repository.save(newVisitor("visitor-bootstrap-001", null));
        AgentVisitorController controller = newController(repository);

        VisitorRequestContext.bind("visitor-bootstrap-001");
        try {
            Response<VisitorBootstrapRespVO> response = controller.bootstrap();
            Assert.assertEquals("0000", response.getCode());
            Assert.assertNotNull(response.getData());
            Assert.assertFalse(response.getData().isNamed());
            Assert.assertEquals("visitor-bootstrap-001", response.getData().getVisitorId());
        } finally {
            VisitorRequestContext.clear();
        }
    }

    @Test
    public void shouldReturnCurrentUsernameForNamedVisitor() {
        InMemoryAnonymousVisitorRepository repository = new InMemoryAnonymousVisitorRepository();
        repository.save(newVisitor("visitor-bootstrap-002", "Alice"));
        AgentVisitorController controller = newController(repository);

        VisitorRequestContext.bind("visitor-bootstrap-002");
        try {
            Response<VisitorBootstrapRespVO> response = controller.bootstrap();
            Assert.assertEquals("0000", response.getCode());
            Assert.assertTrue(response.getData().isNamed());
            Assert.assertEquals("Alice", response.getData().getUsername());
        } finally {
            VisitorRequestContext.clear();
        }
    }

    @Test
    public void shouldBindUsernameThroughNamingEndpoint() {
        InMemoryAnonymousVisitorRepository repository = new InMemoryAnonymousVisitorRepository();
        repository.save(newVisitor("visitor-bootstrap-003", null));
        AgentVisitorController controller = newController(repository);

        VisitorRequestContext.bind("visitor-bootstrap-003");
        try {
            Response<VisitorBootstrapRespVO> response = controller.naming(new VisitorNamingReqVO("Alice"));
            Assert.assertEquals("0000", response.getCode());
            Assert.assertTrue(response.getData().isNamed());
            Assert.assertEquals("Alice", response.getData().getUsername());
            Assert.assertEquals("Alice", repository.readUsername("visitor-bootstrap-003"));
        } finally {
            VisitorRequestContext.clear();
        }
    }

    @Test
    public void shouldRejectRenameThroughNamingEndpoint() {
        InMemoryAnonymousVisitorRepository repository = new InMemoryAnonymousVisitorRepository();
        repository.save(newVisitor("visitor-bootstrap-004", "Alice"));
        AgentVisitorController controller = newController(repository);

        VisitorRequestContext.bind("visitor-bootstrap-004");
        try {
            Response<VisitorBootstrapRespVO> response = controller.naming(new VisitorNamingReqVO("Bob"));
            Assert.assertEquals("0002", response.getCode());
            Assert.assertTrue(response.getInfo().contains("已命名") || response.getInfo().contains("不可修改"));
        } finally {
            VisitorRequestContext.clear();
        }
    }

    private AgentVisitorController newController(InMemoryAnonymousVisitorRepository repository) {
        AnonymousVisitorNamingApplicationService namingService =
                new AnonymousVisitorNamingApplicationService(repository);
        AnonymousVisitorBootstrapApplicationService bootstrapService =
                new AnonymousVisitorBootstrapApplicationService(namingService);

        AgentVisitorController controller = new AgentVisitorController();
        ReflectionTestUtils.setField(controller, "anonymousVisitorBootstrapApplicationService", bootstrapService);
        ReflectionTestUtils.setField(controller, "anonymousVisitorNamingApplicationService", namingService);
        return controller;
    }

    /**
     * 内存仓储桩。
     */
    private static final class InMemoryAnonymousVisitorRepository implements IAnonymousVisitorRepository {

        private final Map<String, AnonymousVisitorRecord> byTokenDigest = new LinkedHashMap<>();
        private final Map<String, AnonymousVisitorRecord> byVisitorId = new LinkedHashMap<>();

        @Override
        public AnonymousVisitorRecord queryByTokenDigest(String tokenDigest) {
            return byTokenDigest.get(tokenDigest);
        }

        @Override
        public AnonymousVisitorRecord queryByVisitorId(String visitorId) {
            return byVisitorId.get(visitorId);
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
}
