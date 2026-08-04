package org.wwz.ai.domain.agent.memory.ltm;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LTM 编排器：单外部 Provider 守卫、prefetch/sync/边界钩子。
 */
@Slf4j
public class LtmManager {

    private final List<MemoryProvider> providers = new CopyOnWriteArrayList<>();
    private final Map<String, MemoryProvider> toolRoutes = Collections.synchronizedMap(new LinkedHashMap<>());
    private final AtomicBoolean hasExternal = new AtomicBoolean(false);
    private final long prefetchTimeoutMs;
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ltm-bg");
        t.setDaemon(true);
        return t;
    });

    public LtmManager() {
        this(8000L);
    }

    public LtmManager(long prefetchTimeoutMs) {
        this.prefetchTimeoutMs = Math.max(100L, prefetchTimeoutMs);
    }

    public synchronized boolean addProvider(MemoryProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (provider.isExternal()) {
            if (!hasExternal.compareAndSet(false, true)) {
                log.warn("Rejected memory provider '{}' — external provider already registered", provider.name());
                return false;
            }
        }
        if (!provider.isAvailable()) {
            log.info("Memory provider '{}' not available; skip", provider.name());
            if (provider.isExternal()) {
                hasExternal.set(false);
            }
            return false;
        }
        providers.add(provider);
        for (Map<String, Object> schema : provider.getToolSchemas()) {
            Object name = schema == null ? null : schema.get("name");
            if (name instanceof String toolName && !toolName.isBlank() && !toolRoutes.containsKey(toolName)) {
                toolRoutes.put(toolName, provider);
            }
        }
        log.info("Memory provider '{}' registered (external={})", provider.name(), provider.isExternal());
        return true;
    }

    public void initializeAll(String sessionId, LtmOwner owner, Map<String, Object> context) {
        Map<String, Object> ctx = context == null ? Map.of() : context;
        for (MemoryProvider provider : providers) {
            try {
                provider.initialize(sessionId, owner, ctx);
            } catch (Exception e) {
                log.warn("Memory provider '{}' initialize failed: {}", provider.name(), e.toString());
            }
        }
    }

    public String buildSystemPrompt() {
        List<String> blocks = new ArrayList<>();
        for (MemoryProvider provider : providers) {
            try {
                String block = provider.systemPromptBlock();
                if (block != null && !block.isBlank()) {
                    blocks.add(block.trim());
                }
            } catch (Exception e) {
                log.warn("Memory provider '{}' systemPromptBlock failed: {}", provider.name(), e.toString());
            }
        }
        return String.join("\n\n", blocks);
    }

    public String prefetchAll(String query, String sessionId) {
        if (query == null || query.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (MemoryProvider provider : providers) {
            try {
                String result = prefetchOne(provider, query, sessionId);
                if (result != null && !result.isBlank()) {
                    parts.add(result.trim());
                }
            } catch (Exception e) {
                log.debug("Memory provider '{}' prefetch failed: {}", provider.name(), e.toString());
            }
        }
        return MemoryContextFence.buildBlock(String.join("\n\n", parts));
    }

    private String prefetchOne(MemoryProvider provider, String query, String sessionId) throws Exception {
        if (!provider.isExternal()) {
            return provider.prefetch(query, sessionId);
        }
        Future<String> future = background.submit(() -> provider.prefetch(query, sessionId));
        try {
            return future.get(prefetchTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            log.warn("Memory provider '{}' prefetch timed out/failed after {}ms", provider.name(), prefetchTimeoutMs);
            return "";
        }
    }

    public void queuePrefetchAll(String query, String sessionId) {
        if (query == null || query.isBlank()) {
            return;
        }
        background.execute(() -> {
            for (MemoryProvider provider : providers) {
                try {
                    provider.queuePrefetch(query, sessionId);
                } catch (Exception e) {
                    log.debug("queuePrefetch failed for {}: {}", provider.name(), e.toString());
                }
            }
        });
    }

    public void syncAll(String userContent, String assistantContent, String sessionId, List<Map<String, Object>> messages) {
        if (userContent == null || userContent.isBlank()) {
            return;
        }
        List<Map<String, Object>> msgSnapshot = messages == null ? List.of() : List.copyOf(messages);
        background.execute(() -> {
            for (MemoryProvider provider : providers) {
                try {
                    provider.syncTurn(userContent, assistantContent == null ? "" : assistantContent, sessionId, msgSnapshot);
                } catch (Exception e) {
                    log.warn("Memory provider '{}' syncTurn failed: {}", provider.name(), e.toString());
                }
            }
        });
    }

    public String onPreCompress(List<Map<String, Object>> messages) {
        List<String> parts = new ArrayList<>();
        for (MemoryProvider provider : providers) {
            try {
                String part = provider.onPreCompress(messages == null ? List.of() : messages);
                if (part != null && !part.isBlank()) {
                    parts.add(part.trim());
                }
            } catch (Exception e) {
                log.debug("onPreCompress failed for {}: {}", provider.name(), e.toString());
            }
        }
        return String.join("\n\n", parts);
    }

    public void commitSessionBoundaryAsync(List<Map<String, Object>> messages,
                                           String newSessionId,
                                           String parentSessionId,
                                           boolean reset) {
        List<Map<String, Object>> snapshot = messages == null ? List.of() : List.copyOf(messages);
        background.execute(() -> {
            for (MemoryProvider provider : providers) {
                try {
                    provider.onSessionEnd(snapshot);
                } catch (Exception e) {
                    log.warn("onSessionEnd failed for {}: {}", provider.name(), e.toString());
                }
            }
            for (MemoryProvider provider : providers) {
                try {
                    provider.onSessionSwitch(newSessionId, parentSessionId == null ? "" : parentSessionId, reset, false);
                } catch (Exception e) {
                    log.warn("onSessionSwitch failed for {}: {}", provider.name(), e.toString());
                }
            }
        });
    }

    public void notifyMemoryToolWrite(String action, String target, String content, Map<String, Object> metadata) {
        if (metadata != null && Boolean.TRUE.equals(metadata.get("skip_memory"))) {
            return;
        }
        Map<String, Object> meta = metadata == null ? Map.of() : metadata;
        for (MemoryProvider provider : providers) {
            if (!provider.isExternal()) {
                continue;
            }
            try {
                provider.onMemoryWrite(action, target, content, meta);
            } catch (Exception e) {
                log.debug("onMemoryWrite mirror failed for {}: {}", provider.name(), e.toString());
            }
        }
    }

    public boolean hasTool(String toolName) {
        return toolRoutes.containsKey(toolName);
    }

    public String handleToolCall(String toolName, Map<String, Object> args) {
        MemoryProvider provider = toolRoutes.get(toolName);
        if (provider == null) {
            return "{\"success\":false,\"message\":\"No memory provider handles tool " + toolName + "\"}";
        }
        try {
            return provider.handleToolCall(toolName, args == null ? Map.of() : args);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    public List<MemoryProvider> getProviders() {
        return List.copyOf(providers);
    }

    public void shutdownAll() {
        background.shutdown();
        try {
            background.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (int i = providers.size() - 1; i >= 0; i--) {
            try {
                providers.get(i).shutdown();
            } catch (Exception e) {
                log.warn("shutdown failed for {}: {}", providers.get(i).name(), e.toString());
            }
        }
    }
}
