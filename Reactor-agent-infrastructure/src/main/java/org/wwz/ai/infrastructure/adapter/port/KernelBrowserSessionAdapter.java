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
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserLiveView;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSession;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionPort;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionStatus;
import org.wwz.ai.infrastructure.dao.IAiAgentKernelBrowserSessionDao;
import org.wwz.ai.infrastructure.dao.po.AiAgentKernelBrowserSession;
import org.wwz.ai.types.agent.config.KernelBrowserProperties;

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
 * Infrastructure adapter for the per-owner Kernel Browser session.
 *
 * <p>The persisted row contains only the stable owner/browser mapping. The CDP endpoint is
 * intentionally read from Kernel on every successful resolve and is never written to MySQL.</p>
 */
@Component
@EnableConfigurationProperties(KernelBrowserProperties.class)
public class KernelBrowserSessionAdapter implements KernelBrowserSessionPort {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String DEFAULT_BASE_URL = "https://api.onkernel.com";
    private static final int DEFAULT_TIMEOUT_SECONDS = 259200;
    private static final int LOCK_TIMEOUT_SECONDS = 30;
    private static final int BROWSER_NAME_HASH_LENGTH = 40;

    private final KernelBrowserProperties properties;
    private final IAiAgentKernelBrowserSessionDao sessionDao;
    private final KernelBrowserHttpClient httpClient;

    /**
     * Production constructor. The shared client supplies the application's connection pool and dispatcher.
     */
    @Autowired
    public KernelBrowserSessionAdapter(KernelBrowserProperties properties,
                                       IAiAgentKernelBrowserSessionDao sessionDao,
                                       OkHttpClient sharedClient) {
        this(properties, sessionDao, new OkHttpKernelBrowserHttpClient(
                sharedClient,
                resolveBaseUrl(properties),
                properties.getApiKey()));
    }

    /**
     * Package-independent test seam. Tests can provide a fake HTTP layer without a real API key or network.
     */
    public KernelBrowserSessionAdapter(KernelBrowserProperties properties,
                                       IAiAgentKernelBrowserSessionDao sessionDao,
                                       KernelBrowserHttpClient httpClient) {
        this.properties = Objects.requireNonNull(properties, "KernelBrowserProperties must not be null");
        this.sessionDao = Objects.requireNonNull(sessionDao, "IAiAgentKernelBrowserSessionDao must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "KernelBrowserHttpClient must not be null");
    }

    /**
     * Small HTTP seam used by the adapter and replaced by a deterministic fake in tests.
     */
    public interface KernelBrowserHttpClient {

        KernelBrowserHttpResponse createBrowser(String name,
                                                int timeoutSeconds,
                                                boolean headless,
                                                String profileName,
                                                boolean profileSaveChanges);

        KernelBrowserHttpResponse getBrowser(String idOrName);

        KernelBrowserHttpResponse deleteBrowser(String idOrName);
    }

    /**
     * HTTP response value used by the fake seam. The body is kept private to the adapter's parsing path.
     */
    public static final class KernelBrowserHttpResponse {

        private final int statusCode;
        private final String body;

        public KernelBrowserHttpResponse(int statusCode, String body) {
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
    public KernelBrowserSession resolveForOwner(String ownerKey) {
        String normalizedOwnerKey = requireOwnerKey(ownerKey);
        requireConfigured();
        try {
            BrowserResolution resolution = withOwnerLock(normalizedOwnerKey,
                    () -> resolveLocked(normalizedOwnerKey));
            requireCdpEndpoint(resolution.browser());
            return KernelBrowserSession.builder()
                    .ownerKey(normalizedOwnerKey)
                    .kernelSessionId(resolution.browser().sessionId())
                    .kernelBrowserName(resolution.browser().name())
                    .cdpWsUrl(resolution.browser().cdpWsUrl())
                    .reconstructed(resolution.reconstructed())
                    .build();
        } catch (RuntimeException e) {
            throw sanitizedFailure("resolveForOwner", e);
        }
    }

    @Override
    public KernelBrowserSessionStatus statusForOwner(String ownerKey) {
        String normalizedOwnerKey = requireOwnerKey(ownerKey);
        try {
            AiAgentKernelBrowserSession row = sessionDao.queryByOwnerKey(normalizedOwnerKey);
            if (row == null) {
                return KernelBrowserSessionStatus.builder()
                        .exists(false)
                        .reconstructed(false)
                        .build();
            }
            return KernelBrowserSessionStatus.builder()
                    .exists(true)
                    .kernelSessionId(row.getKernelSessionId())
                    .lastUsedAt(row.getLastUsedAt())
                    .reconstructed(false)
                    .build();
        } catch (RuntimeException e) {
            throw sanitizedFailure("statusForOwner", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KernelBrowserLiveView ensureLiveView(String ownerKey) {
        String normalizedOwnerKey = requireOwnerKey(ownerKey);
        requireConfigured();
        try {
            BrowserResolution resolution = withOwnerLock(normalizedOwnerKey,
                    () -> resolveLocked(normalizedOwnerKey));
            requireCdpEndpoint(resolution.browser());
            if (StringUtils.isBlank(resolution.browser().liveViewUrl())) {
                throw new IllegalStateException("Kernel response did not include a live view URL");
            }
            return KernelBrowserLiveView.builder()
                    .browserLiveViewUrl(resolution.browser().liveViewUrl())
                    .kernelSessionId(resolution.browser().sessionId())
                    .reconstructed(resolution.reconstructed())
                    .build();
        } catch (RuntimeException e) {
            throw sanitizedFailure("ensureLiveView", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForOwner(String ownerKey) {
        String normalizedOwnerKey = requireOwnerKey(ownerKey);
        try {
            withOwnerLock(normalizedOwnerKey, () -> {
                AiAgentKernelBrowserSession row = sessionDao.queryByOwnerKey(normalizedOwnerKey);
                if (row != null && isConfigured()) {
                    String idOrName = StringUtils.firstNonBlank(
                            row.getKernelSessionId(), row.getKernelBrowserName());
                    if (StringUtils.isNotBlank(idOrName)) {
                        KernelBrowserHttpResponse response = httpClient.deleteBrowser(idOrName);
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
                sessionDao.deleteByOwnerKey(normalizedOwnerKey);
                return null;
            });
        } catch (RuntimeException e) {
            throw sanitizedFailure("deleteForOwner", e);
        }
    }

    private BrowserResolution resolveLocked(String ownerKey) {
        String desiredName = stableBrowserName(ownerKey);
        AiAgentKernelBrowserSession row = sessionDao.queryByOwnerKey(ownerKey);
        if (row == null) {
            return createAndPersist(ownerKey, desiredName, false);
        }

        String sessionId = StringUtils.trimToNull(row.getKernelSessionId());
        if (sessionId != null) {
            BrowserSnapshot browser = getBrowser(sessionId, row.getKernelBrowserName());
            if (browser != null) {
                return touchOrPersistExisting(ownerKey, row, browser, false);
            }
        }

        // The session id may be gone while the named browser is still recoverable.
        String browserName = StringUtils.defaultIfBlank(row.getKernelBrowserName(), desiredName);
        BrowserSnapshot browser = getBrowser(browserName, desiredName);
        if (browser != null) {
            return touchOrPersistExisting(ownerKey, row, browser, false);
        }

        // Both references are gone. A new browser is an explicit reconstruction, never a silent resume.
        return createAndPersist(ownerKey, desiredName, true);
    }

    private BrowserResolution createAndPersist(String ownerKey, String browserName, boolean reconstructed) {
        String profileName = StringUtils.trimToNull(properties.getProfileName());
        KernelBrowserHttpResponse response = httpClient.createBrowser(
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
                throw new IllegalStateException("Kernel browser name conflict could not be reconciled");
            }
            return persistAndResolve(ownerKey, existing, reconstructed);
        }
        if (!isSuccessful(response.getStatusCode())) {
            throw remoteFailure("POST browser", response.getStatusCode());
        }
        BrowserSnapshot created = parseBrowser(response, browserName);
        return persistAndResolve(ownerKey, created, reconstructed);
    }

    private BrowserResolution persistAndResolve(String ownerKey,
                                                BrowserSnapshot browser,
                                                boolean reconstructed) {
        BrowserSnapshot normalized = normalizeBrowser(browser);
        persistMapping(ownerKey, normalized);
        return new BrowserResolution(normalized, reconstructed);
    }

    private BrowserResolution touchOrPersistExisting(String ownerKey,
                                                      AiAgentKernelBrowserSession existingRow,
                                                      BrowserSnapshot browser,
                                                      boolean reconstructed) {
        BrowserSnapshot normalized = normalizeBrowser(browser);
        if (sameMapping(existingRow, normalized)) {
            int updated = sessionDao.updateLastUsedAt(ownerKey, LocalDateTime.now());
            if (updated == 0) {
                persistMapping(ownerKey, normalized);
            }
        } else {
            persistMapping(ownerKey, normalized);
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

    private boolean sameMapping(AiAgentKernelBrowserSession row, BrowserSnapshot browser) {
        return Objects.equals(row.getKernelSessionId(), browser.sessionId())
                && Objects.equals(row.getKernelBrowserName(), browser.name());
    }

    private void persistMapping(String ownerKey, BrowserSnapshot browser) {
        AiAgentKernelBrowserSession mappedByName = sessionDao.queryByKernelBrowserName(browser.name());
        if (mappedByName != null && !ownerKey.equals(mappedByName.getOwnerKey())) {
            throw new IllegalStateException("Kernel browser name is mapped to another owner");
        }

        LocalDateTime now = LocalDateTime.now();
        AiAgentKernelBrowserSession row = new AiAgentKernelBrowserSession();
        row.setOwnerKey(ownerKey);
        row.setKernelSessionId(browser.sessionId());
        row.setKernelBrowserName(browser.name());
        row.setLastUsedAt(now);
        row.setCreateTime(now);
        row.setUpdateTime(now);

        int updated = sessionDao.updateSession(row);
        if (updated == 0) {
            try {
                sessionDao.insert(row);
            } catch (RuntimeException insertFailure) {
                // The owner lock normally makes this unnecessary; the re-read keeps persistence idempotent if a
                // database caller inserted the unique owner row before this insert completed.
                AiAgentKernelBrowserSession concurrentRow = sessionDao.queryByOwnerKey(ownerKey);
                if (concurrentRow == null) {
                    throw insertFailure;
                }
                sessionDao.updateSession(row);
            }
        }
    }

    private BrowserSnapshot getBrowser(String idOrName, String fallbackName) {
        KernelBrowserHttpResponse response = httpClient.getBrowser(idOrName);
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

    private BrowserSnapshot parseBrowser(KernelBrowserHttpResponse response, String fallbackName) {
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
            throw sanitizedFailure("parse Kernel browser response", e);
        }
    }

    private <T> T withOwnerLock(String ownerKey, Supplier<T> action) {
        String lockName = ownerLockName(ownerKey);
        Integer lockResult = sessionDao.getLock(lockName, LOCK_TIMEOUT_SECONDS);
        if (!Integer.valueOf(1).equals(lockResult)) {
            throw new IllegalStateException("Could not acquire Kernel browser owner lock");
        }
        try {
            return action.get();
        } finally {
            sessionDao.releaseLock(lockName);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Kernel Browser is not configured");
        }
    }

    private String requireOwnerKey(String ownerKey) {
        String normalized = StringUtils.trimToNull(ownerKey);
        if (normalized == null) {
            throw new IllegalArgumentException("ownerKey must not be blank");
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

    private String stableBrowserName(String ownerKey) {
        String prefix = StringUtils.defaultIfBlank(properties.getBrowserNamePrefix(), "rb").trim();
        if (!prefix.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalStateException("browserNamePrefix contains unsupported characters");
        }
        String name = prefix + "-" + sha256Hex(ownerKey).substring(0, BROWSER_NAME_HASH_LENGTH);
        if (name.length() > 255 || !name.matches("[a-zA-Z0-9._-]{1,255}")) {
            throw new IllegalStateException("Kernel browser name is invalid");
        }
        return name;
    }

    private String ownerLockName(String ownerKey) {
        return "kernel-browser-owner-" + sha256Hex(ownerKey).substring(0, BROWSER_NAME_HASH_LENGTH);
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
        String detail = KernelBrowserCredentialRedactor.redact(cause.getMessage(), properties.getApiKey());
        if (StringUtils.isBlank(detail)) {
            detail = "unknown error";
        }
        return new IllegalStateException(operation + " failed: " + detail);
    }

    private static boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static String resolveBaseUrl(KernelBrowserProperties properties) {
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

    private static final class OkHttpKernelBrowserHttpClient implements KernelBrowserHttpClient {

        private final OkHttpClient sharedClient;
        private final String baseUrl;
        private final String apiKey;

        private OkHttpKernelBrowserHttpClient(OkHttpClient sharedClient, String baseUrl, String apiKey) {
            this.sharedClient = Objects.requireNonNull(sharedClient, "sharedClient must not be null");
            this.baseUrl = trimTrailingSlash(baseUrl);
            this.apiKey = apiKey == null ? "" : apiKey.trim();
        }

        @Override
        public KernelBrowserHttpResponse createBrowser(String name,
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
        public KernelBrowserHttpResponse getBrowser(String idOrName) {
            return execute("GET", "/browsers/" + encodePathSegment(idOrName), null);
        }

        @Override
        public KernelBrowserHttpResponse deleteBrowser(String idOrName) {
            return execute("DELETE", "/browsers/" + encodePathSegment(idOrName), null);
        }

        private KernelBrowserHttpResponse execute(String method, String path, String body) {
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
                return new KernelBrowserHttpResponse(response.code(), responseBody);
            } catch (IOException | RuntimeException e) {
                String message = KernelBrowserCredentialRedactor.redact(e.getMessage(), apiKey);
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
