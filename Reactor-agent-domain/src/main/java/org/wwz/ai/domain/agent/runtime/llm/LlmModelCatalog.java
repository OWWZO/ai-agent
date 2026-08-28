package org.wwz.ai.domain.agent.runtime.llm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.repository.ILlmModelConfigRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 把「请求里的 model 引用」解析成可出站的 {@link LLMSettings}。
 * <p>
 * 模型目录语义：
 * <ul>
 *   <li>真相源是 DB（管理台 CRUD），不是 yml</li>
 *   <li>短 TTL 缓存压重复查询；管理写后 {@link #invalidateAll()} 立即失效</li>
 *   <li>key / base 每轮现取，不冻进长生命周期 ChatClient</li>
 * </ul>
 * 未命中 DB 时返回 empty，由调用方决定是否允许兼容配置兜底。
 */
@Component
public class LlmModelCatalog {

    public static final String DEFAULT_MODEL = "default";
    /** 备用配置重复使用 modelId 时使用的内部行引用，不暴露给用户配置。 */
    public static final String BINDING_REF_PREFIX = "__db_binding__:";

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
     * 解析模型配置。命中 DB 返回 settings；未命中返回 empty。
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

    /**
     * 从 MySQL 模型用途标记中解析备用模型。备用模型应将 model_usage 设为 fallback。
     */
    public Optional<String> resolveFallbackModelName(String primaryModel) {
        return resolveFallbackModelNames(primaryModel).stream().findFirst();
    }

    /**
     * 按模型目录顺序解析全部备用模型，主模型本身不会重复加入候选列表。
     */
    public List<String> resolveFallbackModelNames(String primaryModel) {
        List<LlmModelBinding> bindings = loadUsable(System.currentTimeMillis());
        if (bindings.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> fallbackModels = new ArrayList<>();
        LlmModelBinding primary = pick(bindings, primaryModel);
        for (LlmModelBinding binding : bindings) {
            if (!isFallback(binding.getModelUsage())) {
                continue;
            }
            if (sameBinding(binding, primary)) {
                continue;
            }
            String modelRef = toFallbackReference(binding, bindings);
            if (StringUtils.isNotBlank(modelRef)) {
                fallbackModels.add(modelRef.trim());
            }
        }
        return fallbackModels;
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
            for (LlmModelBinding binding : bindings) {
                if (!isFallback(binding.getModelUsage())) {
                    return binding;
                }
            }
            return bindings.get(0);
        }

        if (id.startsWith(BINDING_REF_PREFIX)) {
            Long bindingId = parseBindingId(id);
            if (bindingId == null) {
                return null;
            }
            for (LlmModelBinding binding : bindings) {
                if (Objects.equals(bindingId, binding.getId())) {
                    return binding;
                }
            }
            return null;
        }

        LlmModelBinding fallbackById = null;
        for (LlmModelBinding b : bindings) {
            if (id.equals(b.getModelId())) {
                if (!isFallback(b.getModelUsage())) {
                    return b;
                }
                if (fallbackById == null) {
                    fallbackById = b;
                }
            }
        }
        if (fallbackById != null) {
            return fallbackById;
        }

        LlmModelBinding fallbackByName = null;
        for (LlmModelBinding b : bindings) {
            if (id.equals(b.getModelName())) {
                if (!isFallback(b.getModelUsage())) {
                    return b;
                }
                if (fallbackByName == null) {
                    fallbackByName = b;
                }
            }
        }
        return fallbackByName;
    }

    static boolean isFallback(String modelUsage) {
        if (modelUsage == null) {
            return false;
        }
        String normalized = modelUsage.trim().toLowerCase(java.util.Locale.ROOT);
        return "fallback".equals(normalized)
                || "backup".equals(normalized)
                || "备用".equals(normalized)
                || "备用模型".equals(normalized);
    }

    private static String toFallbackReference(LlmModelBinding binding, List<LlmModelBinding> bindings) {
        String modelRef = StringUtils.defaultIfBlank(binding.getModelId(), binding.getModelName());
        if (StringUtils.isBlank(modelRef)) {
            return null;
        }
        if (pick(bindings, modelRef) == binding || binding.getId() == null) {
            return modelRef;
        }
        return BINDING_REF_PREFIX + binding.getId();
    }

    private static boolean sameBinding(LlmModelBinding left, LlmModelBinding right) {
        if (left == null || right == null) {
            return false;
        }
        return left == right
                || (left.getId() != null && Objects.equals(left.getId(), right.getId()));
    }

    private static Long parseBindingId(String modelRef) {
        try {
            return Long.valueOf(modelRef.substring(BINDING_REF_PREFIX.length()));
        } catch (RuntimeException ignored) {
            return null;
        }
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
                // 未配置时保留 0，让 RuntimeDependencies 使用 yml/default；不能在这里提前写死默认值，
                // 否则 enrichSamplingFromYml 无法区分“数据库未配置”和“数据库配置为 100000”。
                .maxInputTokens(binding.getContextWindow() != null && binding.getContextWindow() > 0
                        ? binding.getContextWindow()
                        : 0)
                .functionCallType("function_call")
                .build();
    }
}
