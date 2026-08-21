package org.wwz.ai.domain.agent.runtime.llm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.repository.ILlmModelConfigRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 把「请求里的 model 引用」解析成可出站的 {@link LLMSettings}。
 * <p>
 * 对齐 agentic-rag 的 ModelCatalog 语义：
 * <ul>
 *   <li>真相源是 DB（管理台 CRUD），不是 yml</li>
 *   <li>短 TTL 缓存压重复查询；管理写后 {@link #invalidateAll()} 立即失效</li>
 *   <li>key / base 每轮现取，不冻进长生命周期 ChatClient</li>
 * </ul>
 * 未命中 DB 时返回 empty，由调用方回落 yml（兼容本地 dev）。
 */
@Component
public class LlmModelCatalog {

    public static final String DEFAULT_MODEL = "default";

    private final ILlmModelConfigRepository repository;
    private final LlmChatModelResolver chatModelResolver;
    private final long cacheMs;

    private volatile List<LlmModelBinding> cache = Collections.emptyList();
    private volatile long fetchedAt;

    public LlmModelCatalog(ILlmModelConfigRepository repository,
                           LlmChatModelResolver chatModelResolver,
                           @Value("${agent.llm.model-cache-ms:60000}") long cacheMs) {
        this.repository = repository;
        this.chatModelResolver = chatModelResolver;
        this.cacheMs = Math.max(0L, cacheMs);
    }

    /**
     * 解析模型配置。命中 DB 返回 settings；未命中返回 empty（调用方走 yml）。
     *
     * @param modelRef modelId、上游 modelName，或空/default 表示取第一个可用
     */
    public Optional<LLMSettings> resolve(String modelRef) {
        return resolve(modelRef, System.currentTimeMillis());
    }

    Optional<LLMSettings> resolve(String modelRef, long nowMs) {
        List<LlmModelBinding> bindings = loadUsable(nowMs);
        if (bindings.isEmpty()) {
            return Optional.empty();
        }
        LlmModelBinding picked = pick(bindings, modelRef);
        if (picked == null) {
            return Optional.empty();
        }
        return Optional.of(toSettings(picked));
    }

    /** 管理台写模型/API 后调用：清绑定缓存 + ChatModel 实例缓存。 */
    public void invalidateAll() {
        cache = Collections.emptyList();
        fetchedAt = 0L;
        chatModelResolver.invalidateAll();
    }

    private List<LlmModelBinding> loadUsable(long nowMs) {
        if (!cache.isEmpty() && cacheMs > 0 && nowMs - fetchedAt < cacheMs) {
            return cache;
        }
        try {
            List<LlmModelBinding> loaded = repository.listUsable();
            if (loaded == null) {
                loaded = Collections.emptyList();
            }
            cache = List.copyOf(loaded);
            fetchedAt = nowMs;
            return cache;
        } catch (Exception e) {
            if (!cache.isEmpty()) {
                return cache;
            }
            return Collections.emptyList();
        }
    }

    /**
     * 选模型：id → 上游名；空/default 取第一个。
     * 匹配不上返回 null（不静默回落别的模型）。
     */
    public static LlmModelBinding pick(List<LlmModelBinding> bindings, String modelRef) {
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        String id = modelRef == null ? "" : modelRef.trim();
        if (id.isEmpty() || DEFAULT_MODEL.equalsIgnoreCase(id)) {
            return bindings.get(0);
        }
        for (LlmModelBinding b : bindings) {
            if (id.equals(b.getModelId())) {
                return b;
            }
        }
        for (LlmModelBinding b : bindings) {
            if (id.equals(b.getModelName())) {
                return b;
            }
        }
        return null;
    }

    public static LLMSettings toSettings(LlmModelBinding binding) {
        String path = StringUtils.trimToNull(binding.getCompletionsPath());
        return LLMSettings.builder()
                .model(binding.getModelName())
                .baseUrl(StringUtils.trimToEmpty(binding.getBaseUrl()))
                .apiKey(StringUtils.defaultString(binding.getApiKey()))
                .interfaceUrl(path != null ? path : "/chat/completions")
                .maxTokens(16384)
                .temperature(0.0)
                .maxInputTokens(binding.getContextWindow() != null && binding.getContextWindow() > 0
                        ? binding.getContextWindow()
                        : 100000)
                .functionCallType("function_call")
                .build();
    }
}
