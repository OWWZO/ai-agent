package org.wwz.ai.infrastructure.adapter.port;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserLiveView;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSession;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSessionPort;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSessionStatus;
import org.wwz.ai.infrastructure.dao.IAiAgentBrowserSessionDao;
import org.wwz.ai.infrastructure.dao.po.AiAgentBrowserSession;
import org.wwz.ai.types.agent.config.AgentBrowserProperties;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Infrastructure adapter for the current user's Agent Browser session.
 *
 * <p>The persisted row contains only the stable user/browser mapping in {@code owner_key}. The CDP endpoint is
 * intentionally read from Kernel on every successful resolve and is never written to MySQL.</p>
 */
@Component
@EnableConfigurationProperties(AgentBrowserProperties.class)
public class AgentBrowserSessionAdapter implements AgentBrowserSessionPort {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String DEFAULT_BASE_URL = "https://api.onkernel.com";
    private static final int DEFAULT_TIMEOUT_SECONDS = 259200;
    private static final int LOCK_TIMEOUT_SECONDS = 30;
    private static final int BROWSER_NAME_HASH_LENGTH = 40;

    private final AgentBrowserProperties properties;
    private final IAiAgentBrowserSessionDao sessionDao;
    private final AgentBrowserHttpClient httpClient;

    /**
     * Production constructor. The shared client supplies the application's connection pool and dispatcher.
     */
    @Autowired
    public AgentBrowserSessionAdapter(AgentBrowserProperties properties,
                                      IAiAgentBrowserSessionDao sessionDao,
                                       OkHttpClient sharedClient) {
        this(properties, sessionDao, new OkHttpAgentBrowserHttpClient(
                sharedClient,
                resolveBaseUrl(properties),
                properties.getApiKey()));
    }

    /**
     * Package-independent test seam. Tests can provide a fake HTTP layer without a real API key or network.
     */
    public AgentBrowserSessionAdapter(AgentBrowserProperties properties,
                                      IAiAgentBrowserSessionDao sessionDao,
                                      AgentBrowserHttpClient httpClient) {
        this.properties = Objects.requireNonNull(properties, "AgentBrowserProperties must not be null");
        this.sessionDao = Objects.requireNonNull(sessionDao, "IAiAgentBrowserSessionDao must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "AgentBrowserHttpClient must not be null");
    }

    /**
     * Small HTTP seam used by the adapter and replaced by a deterministic fake in tests.
     */
    public interface AgentBrowserHttpClient {

        AgentBrowserHttpResponse createBrowser(String name,
                                                int timeoutSeconds,
                                                boolean headless,
                                                String profileName,
                                                boolean profileSaveChanges);

        AgentBrowserHttpResponse getBrowser(String idOrName);

        AgentBrowserHttpResponse deleteBrowser(String idOrName);
    }

    /**
     * HTTP response value used by the fake seam. The body is kept private to the adapter's parsing path.
     */
    public static final class AgentBrowserHttpResponse {

        private final int statusCode;
        private final String body;

        public AgentBrowserHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }
    }

    @Override
    public boolean isConfigured() {
        return properties.isEnabled() && StringUtils.isNotBlank(properties.getApiKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentBrowserSession resolveForUser(String userId) {
        String normalizedUserId = requireUserId(userId);
        requireConfigured();
        try {
            BrowserResolution resolution = withUserLock(normalizedUserId,
                    () -> resolveLocked(normalizedUserId));
            requireCdpEndpoint(resolution.browser());
            return AgentBrowserSession.builder()
                    .userId(normalizedUserId)
                    .agentSessionId(resolution.browser().sessionId())
                    .agentBrowserName(resolution.browser().name())
                    .cdpWsUrl(resolution.browser().cdpWsUrl())
                    .reconstructed(resolution.reconstructed())
                    .build();
        } catch (RuntimeException e) {
            throw sanitizedFailure("resolveForUser", e);
        }
    }

    @Override
    public AgentBrowserSessionStatus statusForUser(String userId) {
        String normalizedUserId = requireUserId(userId);
        try {
            AiAgentBrowserSession row = sessionDao.queryByOwnerKey(normalizedUserId);
            if (row == null) {
                return AgentBrowserSessionStatus.builder()
                        .exists(false)
                        .reconstructed(false)
                        .build();
            }
            return AgentBrowserSessionStatus.builder()
                    .exists(true)
                    .agentSessionId(row.getAgentSessionId())
                    .lastUsedAt(row.getLastUsedAt())
                    .reconstructed(false)
                    .build();
        } catch (RuntimeException e) {
            throw sanitizedFailure("statusForUser", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentBrowserLiveView ensureLiveView(String userId) {
        String normalizedUserId = requireUserId(userId);
        requireConfigured();
        try {
            BrowserResolution resolution = withUserLock(normalizedUserId,
                    () -> resolveLocked(normalizedUserId));
            requireCdpEndpoint(resolution.browser());
            if (StringUtils.isBlank(resolution.browser().liveViewUrl())) {
                throw new IllegalStateException("Kernel response did not include a live view URL");
            }
            return AgentBrowserLiveView.builder()
                    .browserLiveViewUrl(resolution.browser().liveViewUrl())
                    .agentSessionId(resolution.browser().sessionId())
                    .reconstructed(resolution.reconstructed())
                    .build();
        } catch (RuntimeException e) {
            throw sanitizedFailure("ensureLiveView", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForUser(String userId) {
        String normalizedUserId = requireUserId(userId);
        try {
            withUserLock(normalizedUserId, () -> {
                AiAgentBrowserSession row = sessionDao.queryByOwnerKey(normalizedUserId);
                if (row != null && isConfigured()) {
                    String idOrName = StringUtils.firstNonBlank(
                            row.getAgentSessionId(), row.getAgentBrowserName());
                    if (StringUtils.isNotBlank(idOrName)) {
                        AgentBrowserHttpResponse response = httpClient.deleteBrowser(idOrName);
                        if (response == null) {
                            throw new IllegalStateException("Kernel delete returned no response");
                        }
                        int statusCode = response.getStatusCode();
                        if (!isSuccessful(statusCode) && statusCode != 404) {
                            throw remoteFailure("DELETE browser", statusCode);
                        }
                    }
                }
                // A remote 404 is already the desired remote state; local cleanup remains mandatory.
                sessionDao.deleteByOwnerKey(normalizedUserId);
                return null;
            });
        } catch (RuntimeException e) {
            throw sanitizedFailure("deleteForUser", e);
        }
    }

    private BrowserResolution resolveLocked(String userId) {
        String desiredName = stableBrowserName(userId);
        AiAgentBrowserSession row = sessionDao.queryByOwnerKey(userId);
        if (row == null) {
            return createAndPersist(userId, desiredName, false);
        }

        String sessionId = StringUtils.trimToNull(row.getAgentSessionId());
        if (sessionId != null) {
            BrowserSnapshot browser = getBrowser(sessionId, row.getAgentBrowserName());
            if (browser != null) {
                return touchOrPersistExisting(userId, row, browser, false);
            }
        }

        // The session id may be gone while the named browser is still recoverable.
        String browserName = StringUtils.defaultIfBlank(row.getAgentBrowserName(), desiredName);
        BrowserSnapshot browser = getBrowser(browserName, desiredName);
        if (browser != null) {
            return touchOrPersistExisting(userId, row, browser, false);
        }

        // Both references are gone. A new browser is an explicit reconstruction, never a silent resume.
        return createAndPersist(userId, desiredName, true);
    }

    private BrowserResolution createAndPersist(String userId, String browserName, boolean reconstructed) {
        String profileName = StringUtils.trimToNull(properties.getProfileName());
        AgentBrowserHttpResponse response = httpClient.createBrowser(
                browserName,
                resolveBrowserTimeoutSeconds(),
                false,
                profileName,
                properties.isProfileSaveChanges());
        if (response == null) {
            throw new IllegalStateException("Kernel create returned no response");
        }
        if (response.getStatusCode() == 409) {
            // A conflict means the stable name already owns a browser. Reconcile by name instead of creating another one.
            BrowserSnapshot existing = getBrowser(browserName, browserName);
            if (existing == null) {
                throw new IllegalStateException("Agent browser name conflict could not be reconciled");
            }
            return persistAndResolve(userId, existing, reconstructed);
        }
        if (!isSuccessful(response.getStatusCode())) {
            throw remoteFailure("POST browser", response.getStatusCode());
        }
        BrowserSnapshot created = parseBrowser(response, browserName);
        return persistAndResolve(userId, created, reconstructed);
    }

    private BrowserResolution persistAndResolve(String userId,
                                                BrowserSnapshot browser,
                                                boolean reconstructed) {
        BrowserSnapshot normalized = normalizeBrowser(browser);
        persistMapping(userId, normalized);
        return new BrowserResolution(normalized, reconstructed);
    }

    private BrowserResolution touchOrPersistExisting(String userId,
                                                       AiAgentBrowserSession existingRow,
                                                      BrowserSnapshot browser,
                                                      boolean reconstructed) {
        BrowserSnapshot normalized = normalizeBrowser(browser);
        if (sameMapping(existingRow, normalized)) {
            int updated = sessionDao.updateLastUsedAt(userId, LocalDateTime.now());
            if (updated == 0) {
                persistMapping(userId, normalized);
            }
        } else {
            persistMapping(userId, normalized);
        }
        return new BrowserResolution(normalized, reconstructed);
    }

    private BrowserSnapshot normalizeBrowser(BrowserSnapshot browser) {
        requireCdpEndpoint(browser);
        String browserName = StringUtils.trimToNull(browser.name());
        if (browserName == null) {
            throw new IllegalStateException("Kernel response did not include a browser name");
        }
        return browser.withName(browserName);
    }

    private boolean sameMapping(AiAgentBrowserSession row, BrowserSnapshot browser) {
        return Objects.equals(row.getAgentSessionId(), browser.sessionId())
                && Objects.equals(row.getAgentBrowserName(), browser.name());
    }

    private void persistMapping(String userId, BrowserSnapshot browser) {
        AiAgentBrowserSession mappedByName = sessionDao.queryByAgentBrowserName(browser.name());
        if (mappedByName != null && !userId.equals(mappedByName.getOwnerKey())) {
            throw new IllegalStateException("Agent browser name is mapped to another userId");
        }

        LocalDateTime now = LocalDateTime.now();
        AiAgentBrowserSession row = new AiAgentBrowserSession();
        row.setOwnerKey(userId);
        row.setAgentSessionId(browser.sessionId());
        row.setAgentBrowserName(browser.name());
        row.setLastUsedAt(now);
        row.setCreateTime(now);
        row.setUpdateTime(now);

        int updated = sessionDao.updateSession(row);
        if (updated == 0) {
            try {
                sessionDao.insert(row);
            } catch (RuntimeException insertFailure) {
                // The user lock normally makes this unnecessary; the re-read keeps persistence idempotent if a
                // database caller inserted the unique owner_key row before this insert completed.
                AiAgentBrowserSession concurrentRow = sessionDao.queryByOwnerKey(userId);
                if (concurrentRow == null) {
                    throw insertFailure;
                }
                sessionDao.updateSession(row);
            }
        }
    }

    private BrowserSnapshot getBrowser(String idOrName, String fallbackName) {
        AgentBrowserHttpResponse response = httpClient.getBrowser(idOrName);
        if (response == null) {
            throw new IllegalStateException("Kernel get returned no response");
        }
        if (response.getStatusCode() == 404) {
            return null;
        }
        if (!isSuccessful(response.getStatusCode())) {
            throw remoteFailure("GET browser", response.getStatusCode());
        }
        return parseBrowser(response, fallbackName);
    }

    private BrowserSnapshot parseBrowser(AgentBrowserHttpResponse response, String fallbackName) {
        try {
            JSONObject json = JSON.parseObject(response.getBody());
            if (json == null) {
                throw new IllegalStateException("Kernel response body was empty");
            }
            String sessionId = StringUtils.trimToNull(json.getString("session_id"));
            if (sessionId == null) {
                throw new IllegalStateException("Kernel response did not include a session id");
            }
            String name = StringUtils.defaultIfBlank(json.getString("name"), fallbackName);
            return new BrowserSnapshot(
                    sessionId,
                    StringUtils.trimToNull(name),
                    json.getString("cdp_ws_url"),
                    json.getString("browser_live_view_url"));
        } catch (RuntimeException e) {
            throw sanitizedFailure("parse Agent browser response", e);
        }
    }

    private <T> T withUserLock(String userId, Supplier<T> action) {
        String lockName = userLockName(userId);
        Integer lockResult = sessionDao.getLock(lockName, LOCK_TIMEOUT_SECONDS);
        if (!Integer.valueOf(1).equals(lockResult)) {
            throw new IllegalStateException("Could not acquire Agent browser user lock");
        }
        try {
            return action.get();
        } finally {
            sessionDao.releaseLock(lockName);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Agent Browser is not configured");
        }
    }

    private String requireUserId(String userId) {
        String normalized = StringUtils.trimToNull(userId);
        if (normalized == null) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        return normalized;
    }

    private void requireCdpEndpoint(BrowserSnapshot browser) {
        if (browser == null || StringUtils.isBlank(browser.cdpWsUrl())) {
            throw new IllegalStateException("Kernel response did not include a CDP endpoint");
        }
    }

    private int resolveBrowserTimeoutSeconds() {
        return properties.getTimeoutSeconds() > 0
                ? properties.getTimeoutSeconds()
                : DEFAULT_TIMEOUT_SECONDS;
    }

    private String stableBrowserName(String userId) {
        String prefix = StringUtils.defaultIfBlank(properties.getBrowserNamePrefix(), "rb").trim();
        if (!prefix.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalStateException("browserNamePrefix contains unsupported characters");
        }
        String name = prefix + "-" + sha256Hex(userId).substring(0, BROWSER_NAME_HASH_LENGTH);
        if (name.length() > 255 || !name.matches("[a-zA-Z0-9._-]{1,255}")) {
            throw new IllegalStateException("Agent browser name is invalid");
        }
        return name;
    }

    private String userLockName(String userId) {
        return "agent-browser-owner-" + sha256Hex(userId).substring(0, BROWSER_NAME_HASH_LENGTH);
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private IllegalStateException remoteFailure(String operation, int statusCode) {
        return new IllegalStateException(operation + " returned HTTP " + statusCode);
    }

    private IllegalStateException sanitizedFailure(String operation, RuntimeException cause) {
        String detail = AgentBrowserCredentialRedactor.redact(cause.getMessage(), properties.getApiKey());
        if (StringUtils.isBlank(detail)) {
            detail = "unknown error";
        }
        return new IllegalStateException(operation + " failed: " + detail);
    }

    private static boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static String resolveBaseUrl(AgentBrowserProperties properties) {
        return StringUtils.defaultIfBlank(properties.getBaseUrl(), DEFAULT_BASE_URL);
    }

    private record BrowserResolution(BrowserSnapshot browser, boolean reconstructed) {
    }

    private record BrowserSnapshot(String sessionId,
                                   String name,
                                   String cdpWsUrl,
                                   String liveViewUrl) {

        private BrowserSnapshot withName(String resolvedName) {
            return new BrowserSnapshot(sessionId, resolvedName, cdpWsUrl, liveViewUrl);
        }
    }

    private static final class OkHttpAgentBrowserHttpClient implements AgentBrowserHttpClient {

        private final OkHttpClient sharedClient;
        private final String baseUrl;
        private final String apiKey;

        private OkHttpAgentBrowserHttpClient(OkHttpClient sharedClient, String baseUrl, String apiKey) {
            this.sharedClient = Objects.requireNonNull(sharedClient, "sharedClient must not be null");
            this.baseUrl = trimTrailingSlash(baseUrl);
            this.apiKey = apiKey == null ? "" : apiKey.trim();
        }

        @Override
        public AgentBrowserHttpResponse createBrowser(String name,
                                                       int timeoutSeconds,
                                                       boolean headless,
                                                       String profileName,
                                                       boolean profileSaveChanges) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", name);
            payload.put("timeout_seconds", timeoutSeconds);
            payload.put("headless", headless);
            if (StringUtils.isNotBlank(profileName)) {
                Map<String, Object> profile = new LinkedHashMap<>();
                profile.put("name", profileName.trim());
                profile.put("save_changes", profileSaveChanges);
                payload.put("profile", profile);
            }
            return execute("POST", "/browsers", JSON.toJSONString(payload));
        }

        @Override
        public AgentBrowserHttpResponse getBrowser(String idOrName) {
            return execute("GET", "/browsers/" + encodePathSegment(idOrName), null);
        }

        @Override
        public AgentBrowserHttpResponse deleteBrowser(String idOrName) {
            return execute("DELETE", "/browsers/" + encodePathSegment(idOrName), null);
        }

        private AgentBrowserHttpResponse execute(String method, String path, String body) {
            Request.Builder builder = new Request.Builder()
                    .url(baseUrl + path)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json");
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, RequestBody.create(body, JSON_MEDIA_TYPE));
            } else {
                builder.method(method, null);
            }
            try (Response response = sharedClient.newCall(builder.build()).execute()) {
                String responseBody = response.body() == null ? null : response.body().string();
                return new AgentBrowserHttpResponse(response.code(), responseBody);
            } catch (IOException | RuntimeException e) {
                String message = AgentBrowserCredentialRedactor.redact(e.getMessage(), apiKey);
                throw new IllegalStateException(StringUtils.defaultIfBlank(message, "Kernel HTTP request failed"));
            }
        }

        private static String encodePathSegment(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        }

        private static String trimTrailingSlash(String value) {
            String normalized = StringUtils.defaultIfBlank(value, DEFAULT_BASE_URL).trim();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
    }
}
