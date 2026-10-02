package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserLiveView;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSession;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionStatus;
import org.wwz.ai.infrastructure.adapter.port.KernelBrowserSessionAdapter;
import org.wwz.ai.infrastructure.dao.IAiAgentKernelBrowserSessionDao;
import org.wwz.ai.infrastructure.dao.po.AiAgentKernelBrowserSession;
import org.wwz.ai.types.agent.config.KernelBrowserProperties;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public class KernelBrowserSessionServiceTest {

    private static final String OWNER_A = "owner-a";
    private static final String OWNER_B = "owner-b";
    private static final String API_KEY = "kernel-api-secret";
    private static final String CDP_URL = "wss://kernel.example/cdp/session-a";
    private static final String JWT = "jwt-secret-value";

    @Test
    public void firstCallCreatesOneBrowserAndPersistsSessionMapping() {
        Fixture fixture = new Fixture();
        String browserName = browserName(OWNER_A);
        fixture.http.enqueueCreate(FakeHttp.response(200, browser("session-a", browserName,
                CDP_URL, "https://kernel.example/live/session-a")));

        KernelBrowserSession session = fixture.adapter.resolveForOwner(OWNER_A);

        Assert.assertEquals(OWNER_A, session.getOwnerKey());
        Assert.assertEquals("session-a", session.getKernelSessionId());
        Assert.assertEquals(browserName, session.getKernelBrowserName());
        Assert.assertEquals(CDP_URL, session.getCdpWsUrl());
        Assert.assertFalse(session.isReconstructed());
        Assert.assertEquals(1, fixture.http.createCalls.size());
        FakeHttp.CreateCall createCall = fixture.http.createCalls.get(0);
        Assert.assertEquals(browserName, createCall.name);
        Assert.assertEquals(259200, createCall.timeoutSeconds);
        Assert.assertFalse(createCall.headless);
        Assert.assertEquals("agent", createCall.profileName);
        Assert.assertFalse(createCall.profileSaveChanges);
        Assert.assertTrue(Pattern.matches("[a-zA-Z0-9._-]{1,255}", createCall.name));
        Assert.assertFalse(createCall.name.contains(OWNER_A));

        AiAgentKernelBrowserSession persisted = fixture.rows.get(OWNER_A);
        Assert.assertNotNull(persisted);
        Assert.assertEquals("session-a", persisted.getKernelSessionId());
        Assert.assertEquals(browserName, persisted.getKernelBrowserName());
        Assert.assertFalse(JSON.toJSONString(persisted).contains("cdp_ws_url"));
    }

    @Test
    public void secondCallReusesMappingAndFetchesFreshCdpEndpoint() {
        Fixture fixture = new Fixture();
        String browserName = browserName(OWNER_A);
        fixture.http.enqueueCreate(FakeHttp.response(200, browser("session-a", browserName,
                CDP_URL, "https://kernel.example/live/session-a")));
        fixture.adapter.resolveForOwner(OWNER_A);

        String freshCdpUrl = "wss://kernel.example/cdp/session-a-fresh";
        fixture.http.whenGet("session-a", FakeHttp.response(200, browser("session-a", browserName,
                freshCdpUrl, "https://kernel.example/live/session-a")));

        KernelBrowserSession second = fixture.adapter.resolveForOwner(OWNER_A);

        Assert.assertEquals("session-a", second.getKernelSessionId());
        Assert.assertEquals(freshCdpUrl, second.getCdpWsUrl());
        Assert.assertFalse(second.isReconstructed());
        Assert.assertEquals(1, fixture.http.createCalls.size());
        Assert.assertEquals(List.of("session-a"), fixture.http.getCalls);
        Mockito.verify(fixture.dao).updateLastUsedAt(Mockito.eq(OWNER_A), Mockito.any(LocalDateTime.class));
    }

    @Test
    public void differentOwnersReceiveDifferentBrowsers() {
        Fixture fixture = new Fixture();
        fixture.http.enqueueCreate(FakeHttp.response(200, browser("session-a", browserName(OWNER_A),
                "wss://kernel.example/cdp/a", "https://kernel.example/live/a")));
        fixture.http.enqueueCreate(FakeHttp.response(200, browser("session-b", browserName(OWNER_B),
                "wss://kernel.example/cdp/b", "https://kernel.example/live/b")));

        KernelBrowserSession ownerA = fixture.adapter.resolveForOwner(OWNER_A);
        KernelBrowserSession ownerB = fixture.adapter.resolveForOwner(OWNER_B);

        Assert.assertEquals("session-a", ownerA.getKernelSessionId());
        Assert.assertEquals("session-b", ownerB.getKernelSessionId());
        Assert.assertNotEquals(ownerA.getKernelBrowserName(), ownerB.getKernelBrowserName());
        Assert.assertEquals(2, fixture.http.createCalls.size());
        Assert.assertEquals("agent", fixture.http.createCalls.get(0).profileName);
        Assert.assertEquals("agent", fixture.http.createCalls.get(1).profileName);
        Assert.assertFalse(fixture.http.createCalls.get(0).profileSaveChanges);
        Assert.assertFalse(fixture.http.createCalls.get(1).profileSaveChanges);
        Assert.assertEquals(2, fixture.rows.size());
    }

    @Test
    public void missingBrowserIsReconstructedByCreatingReplacement() {
        Fixture fixture = new Fixture();
        String browserName = browserName(OWNER_A);
        fixture.putRow(row(OWNER_A, "session-old", browserName));
        fixture.http.whenGet("session-old", FakeHttp.response(404, ""));
        fixture.http.whenGet(browserName, FakeHttp.response(404, ""));
        fixture.http.enqueueCreate(FakeHttp.response(200, browser("session-new", browserName,
                "wss://kernel.example/cdp/new", "https://kernel.example/live/new")));

        KernelBrowserSession session = fixture.adapter.resolveForOwner(OWNER_A);

        Assert.assertEquals("session-new", session.getKernelSessionId());
        Assert.assertTrue(session.isReconstructed());
        Assert.assertEquals(List.of("session-old", browserName), fixture.http.getCalls);
        Assert.assertEquals(1, fixture.http.createCalls.size());
        Assert.assertEquals("agent", fixture.http.createCalls.get(0).profileName);
        Assert.assertFalse(fixture.http.createCalls.get(0).profileSaveChanges);
        Assert.assertEquals("session-new", fixture.rows.get(OWNER_A).getKernelSessionId());
    }

    @Test
    public void blankProfileNameDoesNotPassProfileConfiguration() {
        KernelBrowserProperties properties = properties();
        properties.setProfileName("  ");
        Fixture fixture = new Fixture(properties);
        String browserName = browserName(OWNER_A);
        fixture.http.enqueueCreate(FakeHttp.response(200, browser("session-a", browserName,
                CDP_URL, "https://kernel.example/live/session-a")));

        fixture.adapter.resolveForOwner(OWNER_A);

        FakeHttp.CreateCall createCall = fixture.http.createCalls.get(0);
        Assert.assertNull(createCall.profileName);
        Assert.assertFalse(createCall.profileSaveChanges);
    }

    @Test
    public void missingSessionIdIsReconciledByNameWithoutReconstruction() {
        Fixture fixture = new Fixture();
        String browserName = browserName(OWNER_A);
        fixture.putRow(row(OWNER_A, "session-old", browserName));
        fixture.http.whenGet("session-old", FakeHttp.response(404, "gone"));
        fixture.http.whenGet(browserName, FakeHttp.response(200, browser("session-recovered", browserName,
                "wss://kernel.example/cdp/recovered", "https://kernel.example/live/recovered")));

        KernelBrowserSession session = fixture.adapter.resolveForOwner(OWNER_A);

        Assert.assertEquals("session-recovered", session.getKernelSessionId());
        Assert.assertFalse(session.isReconstructed());
        Assert.assertTrue(fixture.http.createCalls.isEmpty());
        Assert.assertEquals("session-recovered", fixture.rows.get(OWNER_A).getKernelSessionId());
    }

    @Test
    public void concurrentFirstCallsReconcilePostConflictWithoutDuplicate() throws Exception {
        Fixture fixture = new Fixture();
        String browserName = browserName(OWNER_A);
        String body = browser("session-shared", browserName,
                "wss://kernel.example/cdp/shared", "https://kernel.example/live/shared");
        fixture.http.enqueueCreate(FakeHttp.response(409, "conflict"));
        fixture.http.whenGet(browserName, FakeHttp.response(200, body));
        fixture.http.whenGet("session-shared", FakeHttp.response(200, body));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<KernelBrowserSession> first = executor.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            return fixture.adapter.resolveForOwner(OWNER_A);
        });
        Future<KernelBrowserSession> second = executor.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            return fixture.adapter.resolveForOwner(OWNER_A);
        });
        start.countDown();

        KernelBrowserSession firstResult = first.get(5, TimeUnit.SECONDS);
        KernelBrowserSession secondResult = second.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        Assert.assertEquals("session-shared", firstResult.getKernelSessionId());
        Assert.assertEquals("session-shared", secondResult.getKernelSessionId());
        Assert.assertEquals(1, fixture.http.createCalls.size());
        Assert.assertEquals(1, fixture.rows.size());
        Mockito.verify(fixture.dao, Mockito.times(1)).insert(Mockito.any(AiAgentKernelBrowserSession.class));
    }

    @Test
    public void explicitDeleteIsIdempotentWhenKernelReturns404() {
        Fixture fixture = new Fixture();
        fixture.putRow(row(OWNER_A, "session-a", browserName(OWNER_A)));
        fixture.http.deleteResponse = FakeHttp.response(404, "gone");

        fixture.adapter.deleteForOwner(OWNER_A);
        fixture.adapter.deleteForOwner(OWNER_A);

        Assert.assertTrue(fixture.rows.isEmpty());
        Assert.assertEquals(List.of("session-a"), fixture.http.deleteCalls);
        Mockito.verify(fixture.dao, Mockito.times(2)).deleteByOwnerKey(OWNER_A);
    }

    @Test
    public void exceptionMessagesRedactCredentialsAndEndpoints() {
        Fixture fixture = new Fixture();
        fixture.putRow(row(OWNER_A, "session-a", browserName(OWNER_A)));
        fixture.http.getFailure = new IllegalStateException(
                API_KEY + " cdp_ws_url=" + CDP_URL + "?jwt=" + JWT);

        try {
            fixture.adapter.resolveForOwner(OWNER_A);
            Assert.fail("Expected the Kernel request to fail");
        } catch (IllegalStateException expected) {
            String message = expected.getMessage();
            Assert.assertNotNull(message);
            Assert.assertFalse(message.contains(API_KEY));
            Assert.assertFalse(message.contains(CDP_URL));
            Assert.assertFalse(message.contains(JWT));
        }
    }

    @Test
    public void isConfiguredRequiresNonBlankApiKey() {
        KernelBrowserProperties properties = properties();
        properties.setApiKey("  ");
        Fixture fixture = new Fixture(properties);

        Assert.assertFalse(fixture.adapter.isConfigured());
    }

    @Test
    public void profileDefaultsToSharedReadOnlyAgent() {
        KernelBrowserProperties properties = new KernelBrowserProperties();

        Assert.assertEquals("agent", properties.getProfileName());
        Assert.assertFalse(properties.isProfileSaveChanges());
    }

    @Test
    public void ensureLiveViewReturnsLiveUrlWithoutCdpEndpointOnType() {
        Fixture fixture = new Fixture();
        String browserName = browserName(OWNER_A);
        fixture.http.enqueueCreate(FakeHttp.response(200, browser("session-a", browserName,
                CDP_URL, "https://kernel.example/live/session-a")));

        KernelBrowserLiveView liveView = fixture.adapter.ensureLiveView(OWNER_A);

        Assert.assertEquals("https://kernel.example/live/session-a", liveView.getBrowserLiveViewUrl());
        Assert.assertEquals("session-a", liveView.getKernelSessionId());
        Assert.assertFalse(Arrays.stream(KernelBrowserLiveView.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("cdpWsUrl")));
    }

    @Test
    public void statusForOwnerDoesNotReturnOrFetchCdpEndpoint() {
        Fixture fixture = new Fixture();
        LocalDateTime lastUsedAt = LocalDateTime.of(2026, 9, 29, 10, 0);
        AiAgentKernelBrowserSession row = row(OWNER_A, "session-a", browserName(OWNER_A));
        row.setLastUsedAt(lastUsedAt);
        fixture.putRow(row);

        KernelBrowserSessionStatus status = fixture.adapter.statusForOwner(OWNER_A);

        Assert.assertTrue(status.isExists());
        Assert.assertEquals("session-a", status.getKernelSessionId());
        Assert.assertEquals(lastUsedAt, status.getLastUsedAt());
        Assert.assertTrue(fixture.http.getCalls.isEmpty());
        Assert.assertFalse(Arrays.stream(KernelBrowserSessionStatus.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("cdpWsUrl")));
    }

    @Test
    public void resolveRejectsBlankOwnerKey() {
        Fixture fixture = new Fixture();

        try {
            fixture.adapter.resolveForOwner("  ");
            Assert.fail("Expected blank ownerKey validation");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals("ownerKey must not be blank", expected.getMessage());
        }
    }

    private static KernelBrowserProperties properties() {
        KernelBrowserProperties properties = new KernelBrowserProperties();
        properties.setEnabled(true);
        properties.setApiKey(API_KEY);
        properties.setBaseUrl("https://api.onkernel.com");
        properties.setTimeoutSeconds(259200);
        properties.setBrowserNamePrefix("rb");
        properties.setProfileName("agent");
        properties.setProfileSaveChanges(false);
        return properties;
    }

    private static String browserName(String ownerKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(ownerKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "rb-" + java.util.HexFormat.of().formatHex(digest).substring(0, 40);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String browser(String sessionId, String name, String cdpUrl, String liveViewUrl) {
        return JSON.toJSONString(Map.of(
                "session_id", sessionId,
                "name", name,
                "cdp_ws_url", cdpUrl,
                "browser_live_view_url", liveViewUrl));
    }

    private static AiAgentKernelBrowserSession row(String ownerKey, String sessionId, String browserName) {
        return AiAgentKernelBrowserSession.builder()
                .ownerKey(ownerKey)
                .kernelSessionId(sessionId)
                .kernelBrowserName(browserName)
                .lastUsedAt(LocalDateTime.now())
                .build();
    }

    private static final class Fixture {

        private final IAiAgentKernelBrowserSessionDao dao = Mockito.mock(IAiAgentKernelBrowserSessionDao.class);
        private final ConcurrentMap<String, AiAgentKernelBrowserSession> rows = new ConcurrentHashMap<>();
        private final FakeHttp http = new FakeHttp();
        private final KernelBrowserSessionAdapter adapter;

        private Fixture() {
            this(properties());
        }

        private Fixture(KernelBrowserProperties properties) {
            Mockito.when(dao.getLock(Mockito.anyString(), Mockito.anyInt())).thenAnswer(invocation -> {
                http.lock(invocation.getArgument(0));
                return 1;
            });
            Mockito.when(dao.releaseLock(Mockito.anyString())).thenAnswer(invocation -> {
                http.unlock(invocation.getArgument(0));
                return 1;
            });
            Mockito.when(dao.queryByOwnerKey(Mockito.anyString()))
                    .thenAnswer(invocation -> rows.get(invocation.getArgument(0)));
            Mockito.when(dao.queryByKernelBrowserName(Mockito.anyString())).thenAnswer(invocation -> rows.values()
                    .stream()
                    .filter(row -> invocation.getArgument(0).equals(row.getKernelBrowserName()))
                    .findFirst()
                    .orElse(null));
            Mockito.when(dao.updateSession(Mockito.any(AiAgentKernelBrowserSession.class))).thenAnswer(invocation -> {
                AiAgentKernelBrowserSession updated = invocation.getArgument(0);
                if (rows.containsKey(updated.getOwnerKey())) {
                    rows.put(updated.getOwnerKey(), copy(updated));
                    return 1;
                }
                return 0;
            });
            Mockito.when(dao.updateLastUsedAt(Mockito.anyString(), Mockito.any(LocalDateTime.class)))
                    .thenAnswer(invocation -> {
                        String ownerKey = invocation.getArgument(0);
                        AiAgentKernelBrowserSession existing = rows.get(ownerKey);
                        if (existing == null) {
                            return 0;
                        }
                        existing.setLastUsedAt(invocation.getArgument(1));
                        return 1;
                    });
            Mockito.when(dao.insert(Mockito.any(AiAgentKernelBrowserSession.class))).thenAnswer(invocation -> {
                AiAgentKernelBrowserSession inserted = invocation.getArgument(0);
                rows.put(inserted.getOwnerKey(), copy(inserted));
                return 1;
            });
            Mockito.when(dao.deleteByOwnerKey(Mockito.anyString())).thenAnswer(invocation -> {
                rows.remove(invocation.getArgument(0));
                return 1;
            });
            adapter = new KernelBrowserSessionAdapter(properties, dao, http);
        }

        private void putRow(AiAgentKernelBrowserSession row) {
            rows.put(row.getOwnerKey(), row);
        }

        private static AiAgentKernelBrowserSession copy(AiAgentKernelBrowserSession source) {
            return AiAgentKernelBrowserSession.builder()
                    .id(source.getId())
                    .ownerKey(source.getOwnerKey())
                    .kernelSessionId(source.getKernelSessionId())
                    .kernelBrowserName(source.getKernelBrowserName())
                    .lastUsedAt(source.getLastUsedAt())
                    .createTime(source.getCreateTime())
                    .updateTime(source.getUpdateTime())
                    .build();
        }
    }

    private static final class FakeHttp implements KernelBrowserSessionAdapter.KernelBrowserHttpClient {

        private final Deque<KernelBrowserSessionAdapter.KernelBrowserHttpResponse> createResponses = new ArrayDeque<>();
        private final ConcurrentMap<String, KernelBrowserSessionAdapter.KernelBrowserHttpResponse> getResponses = new ConcurrentHashMap<>();
        private final List<CreateCall> createCalls = java.util.Collections.synchronizedList(new ArrayList<>());
        private final List<String> getCalls = java.util.Collections.synchronizedList(new ArrayList<>());
        private final List<String> deleteCalls = java.util.Collections.synchronizedList(new ArrayList<>());
        private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        private volatile KernelBrowserSessionAdapter.KernelBrowserHttpResponse deleteResponse = response(204, "");
        private volatile RuntimeException getFailure;

        @Override
        public KernelBrowserSessionAdapter.KernelBrowserHttpResponse createBrowser(String name,
                                                                                     int timeoutSeconds,
                                                                                     boolean headless,
                                                                                     String profileName,
                                                                                     boolean profileSaveChanges) {
            createCalls.add(new CreateCall(name, timeoutSeconds, headless, profileName, profileSaveChanges));
            synchronized (createResponses) {
                KernelBrowserSessionAdapter.KernelBrowserHttpResponse response = createResponses.pollFirst();
                if (response == null) {
                    throw new AssertionError("Unexpected second browser creation");
                }
                return response;
            }
        }

        @Override
        public KernelBrowserSessionAdapter.KernelBrowserHttpResponse getBrowser(String idOrName) {
            getCalls.add(idOrName);
            if (getFailure != null) {
                throw getFailure;
            }
            return getResponses.getOrDefault(idOrName, response(404, ""));
        }

        @Override
        public KernelBrowserSessionAdapter.KernelBrowserHttpResponse deleteBrowser(String idOrName) {
            deleteCalls.add(idOrName);
            return deleteResponse;
        }

        private void enqueueCreate(KernelBrowserSessionAdapter.KernelBrowserHttpResponse response) {
            synchronized (createResponses) {
                createResponses.addLast(response);
            }
        }

        private void whenGet(String idOrName, KernelBrowserSessionAdapter.KernelBrowserHttpResponse response) {
            getResponses.put(idOrName, response);
        }

        private void lock(String name) {
            locks.computeIfAbsent(name, ignored -> new ReentrantLock()).lock();
        }

        private void unlock(String name) {
            ReentrantLock lock = locks.get(name);
            if (lock != null) {
                lock.unlock();
            }
        }

        private static KernelBrowserSessionAdapter.KernelBrowserHttpResponse response(int statusCode, String body) {
            return new KernelBrowserSessionAdapter.KernelBrowserHttpResponse(statusCode, body);
        }

        private static final class CreateCall {
            private final String name;
            private final int timeoutSeconds;
            private final boolean headless;
            private final String profileName;
            private final boolean profileSaveChanges;

            private CreateCall(String name,
                               int timeoutSeconds,
                               boolean headless,
                               String profileName,
                               boolean profileSaveChanges) {
                this.name = name;
                this.timeoutSeconds = timeoutSeconds;
                this.headless = headless;
                this.profileName = profileName;
                this.profileSaveChanges = profileSaveChanges;
            }
        }
    }
}
