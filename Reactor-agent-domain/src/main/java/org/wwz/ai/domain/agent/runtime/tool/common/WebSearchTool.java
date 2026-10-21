package org.wwz.ai.domain.agent.runtime.tool.common;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolArgs;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchBundle;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchHit;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchProvider;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchProvider.ProviderConfig;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchProvider.SearchRequest;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.provider.BraveSearchProvider;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.provider.ExaSearchProvider;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.provider.GptSearchProvider;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.provider.GrokSearchProvider;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.provider.TavilySearchProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Web 搜索工具。
 * 优先级：Grok/xAI 原生搜索 → GPT/OpenAI Responses API → Exa → Tavily → Brave。
 * <p>
 * 各 provider 的请求与响应解析在 {@code websearch.provider} 包；本类只负责
 * 配置解析、优先级编排、失败续试和结果序列化。
 */
@Slf4j
@Data
public class WebSearchTool implements BaseTool {

    public static final String TOOL_NAME = "WebSearch";

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        String currentMonthYear = java.time.YearMonth.now().toString();
        return """
                Search the web for up-to-date information beyond the model knowledge cutoff.
                Prefer Grok or GPT native web search when available; otherwise Exa/Tavily/Brave.
                Returns titles, URLs and snippets. After answering, include a Sources section with markdown links.

                CRITICAL:
                - Use the current period (%s) in queries for recent docs/events
                - Prefer this for current events / latest docs; use deep_search for multi-hop research if available

                Domain filtering:
                - allowed_domains: only include these domains
                - blocked_domains: exclude these domains
                - Do not set both at once
                """.formatted(currentMonthYear);
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "The search query to use");

        Map<String, Object> allowed = new LinkedHashMap<>();
        allowed.put("type", "array");
        allowed.put("description", "Only include search results from these domains");
        allowed.put("items", Map.of("type", "string"));

        Map<String, Object> blocked = new LinkedHashMap<>();
        blocked.put("type", "array");
        blocked.put("description", "Never include search results from these domains");
        blocked.put("items", Map.of("type", "string"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", query);
        properties.put("allowed_domains", allowed);
        properties.put("blocked_domains", blocked);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("query"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> params = ToolArgs.mapStrict(input);
            // 必须用 ToolArgs.str：String.valueOf(null) 会得到字面量 "null"，
            // 长度 4 能绕过下面的 >=2 校验，把 "null" 当查询词发给 provider。
            String query = ToolArgs.str(params.get("query"));
            if (StringUtils.isBlank(query) || query.length() < 2) {
                return failure("WebSearch 失败：query 至少 2 个字符");
            }

            List<String> allowedDomains = readStringList(params.get("allowed_domains"));
            List<String> blockedDomains = readStringList(params.get("blocked_domains"));
            if (!allowedDomains.isEmpty() && !blockedDomains.isEmpty()) {
                return failure("WebSearch 失败：不能同时指定 allowed_domains 与 blocked_domains");
            }

            List<ProviderPlan> plans = resolveProviderPlans(requireReactorConfig());
            if (plans.isEmpty()) {
                return failure("WebSearch 未配置。请配置 GPT/OpenAI（web_search.gpt_*）、Grok/xAI 或 exa/tavily/brave API key。");
            }
            // 缺失 HTTP 端口是配置问题，不是某个 provider 的失败：提前抛，
            // 否则每个 provider 都会以"Grok 缺少 RemoteHttpPort"的名义失败一遍。
            requireHttpPort();

            Exception lastError = null;
            // provider 按优先级逐个尝试；单个供应商超时或响应格式异常时，继续使用下一个可用后端。
            for (ProviderPlan plan : plans) {
                try {
                    SearchProvider provider = newProvider(plan);
                    SearchBundle bundle = provider.search(new SearchRequest(
                            query, allowedDomains, blockedDomains, plan.config()));
                    double durationSeconds = (System.currentTimeMillis() - start) / 1000.0;
                    return buildSuccess(query, plan.name(), bundle, durationSeconds);
                } catch (Exception e) {
                    lastError = e;
                    log.warn("{} WebSearch provider {} failed, try next: {}",
                            requestId(), plan.name(), e.getMessage());
                }
            }
            String msg = "WebSearch 全部 provider 失败"
                    + (lastError == null ? "" : "：" + lastError.getMessage());
            return failure(msg);
        } catch (Exception e) {
            log.error("{} WebSearch execute error, input={}", requestId(), input, e);
            return failure("WebSearch 执行失败：" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /** 按 plan 构造 provider；HTTP 端口惰性取，避免 provider 构造时就要求依赖齐备。 */
    private SearchProvider newProvider(ProviderPlan plan) {
        return switch (plan.name()) {
            case "grok" -> new GrokSearchProvider(this::optionalRemoteHttpPort);
            case "gpt" -> new GptSearchProvider(this::optionalRemoteHttpPort);
            case "exa" -> new ExaSearchProvider(this::optionalRemoteHttpPort);
            case "tavily" -> new TavilySearchProvider(this::optionalRemoteHttpPort);
            case "brave" -> new BraveSearchProvider(this::optionalRemoteHttpPort);
            default -> throw new IllegalStateException("unknown provider: " + plan.name());
        };
    }

    private ToolResultPayload buildSuccess(String query,
                                           String providerName,
                                           SearchBundle bundle,
                                           double durationSeconds) {
        List<SearchHit> hits = bundle.hits() == null ? List.of() : bundle.hits();
        List<Map<String, Object>> hitRows = new ArrayList<>(hits.size());
        for (SearchHit hit : hits) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", hit.title());
            row.put("url", hit.url());
            if (StringUtils.isNotBlank(hit.snippet())) {
                row.put("snippet", hit.snippet());
            }
            hitRows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", "web_search");
        data.put("ok", Boolean.TRUE);
        data.put("query", query);
        data.put("provider", providerName);
        data.put("durationSec", Math.round(durationSeconds * 100.0) / 100.0);
        if (StringUtils.isNotBlank(bundle.summary())) {
            data.put("summary", bundle.summary().trim());
        }
        data.put("hits", hitRows);
        data.put("reminder", "Cite sources with markdown hyperlinks in the final answer.");
        return ToolResultPayload.fromData(data);
    }

    /**
     * auto: grok → gpt → exa → tavily → brave
     * gpt / grok / exa / tavily / brave / disabled 为强制模式
     */
    private List<ProviderPlan> resolveProviderPlans(ReactorConfig config) {
        String mode = StringUtils.defaultIfBlank(config.getWebSearchMode(), "auto").trim().toLowerCase(Locale.ROOT);
        List<ProviderPlan> plans = new ArrayList<>();
        if ("disabled".equals(mode)) {
            return plans;
        }

        ProviderPlan grok = resolveGrokPlan(config);
        ProviderPlan gpt = resolveGptPlan(config);
        ProviderPlan exa = resolveExaPlan(config);
        String tavilyKey = StringUtils.trimToNull(config.getWebSearchTavilyApiKey());
        String braveKey = StringUtils.trimToNull(config.getWebSearchBraveApiKey());

        // 强制模式：只放该 provider，配置缺失则返回空列表（由调用方报「未配置」）。
        switch (mode) {
            case "grok", "xai" -> addIfPresent(plans, grok);
            case "gpt", "openai" -> addIfPresent(plans, gpt);
            case "exa" -> addIfPresent(plans, exa);
            case "tavily" -> addKeyOnly(plans, "tavily", tavilyKey);
            case "brave" -> addKeyOnly(plans, "brave", braveKey);
            default -> {
                // auto 只加入实际具备凭据的 Provider，执行阶段才能按顺序安全尝试而不制造无意义错误。
                addIfPresent(plans, grok);
                addIfPresent(plans, gpt);
                addIfPresent(plans, exa);
                addKeyOnly(plans, "tavily", tavilyKey);
                addKeyOnly(plans, "brave", braveKey);
            }
        }
        return plans;
    }

    private static void addIfPresent(List<ProviderPlan> plans, ProviderPlan plan) {
        if (plan != null) {
            plans.add(plan);
        }
    }

    private static void addKeyOnly(List<ProviderPlan> plans, String name, String apiKey) {
        if (apiKey != null) {
            plans.add(new ProviderPlan(name, new ProviderConfig(apiKey, null, null, null)));
        }
    }

    private ProviderPlan resolveGptPlan(ReactorConfig config) {
        String apiKey = StringUtils.trimToNull(config.getWebSearchGptApiKey());
        if (apiKey == null) {
            return null;
        }
        String baseUrl = StringUtils.defaultIfBlank(
                config.getWebSearchGptBaseUrl(), "https://api.openai.com/v1").trim();
        String model = StringUtils.defaultIfBlank(config.getWebSearchGptModel(), "gpt-4.1").trim();
        String interfaceUrl = StringUtils.defaultIfBlank(
                config.getWebSearchGptInterfaceUrl(), "/responses").trim();
        return new ProviderPlan("gpt", new ProviderConfig(apiKey, baseUrl, interfaceUrl, model));
    }

    private ProviderPlan resolveExaPlan(ReactorConfig config) {
        String apiKey = StringUtils.trimToNull(config.getWebSearchExaApiKey());
        if (apiKey == null) {
            return null;
        }
        String searchUrl = StringUtils.defaultIfBlank(
                config.getWebSearchExaSearchUrl(), "https://api.exa.ai/search").trim();
        return new ProviderPlan("exa", new ProviderConfig(apiKey, searchUrl, null, null));
    }

    private ProviderPlan resolveGrokPlan(ReactorConfig config) {
        String apiKey = StringUtils.trimToNull(config.getWebSearchGrokApiKey());
        String baseUrl = StringUtils.trimToNull(config.getWebSearchGrokBaseUrl());
        String model = StringUtils.trimToNull(config.getWebSearchGrokModel());
        String interfaceUrl = StringUtils.defaultIfBlank(
                config.getWebSearchGrokInterfaceUrl(), "/v1/chat/completions");

        LLMSettings llm = resolveAgentLlmSettings();
        if (llm != null) {
            // WebSearch 专用配置优先，缺失项才借用当前 Agent LLM 配置，避免搜索设置覆盖对话模型。
            if (apiKey == null) {
                apiKey = StringUtils.trimToNull(llm.getApiKey());
            }
            if (baseUrl == null) {
                baseUrl = StringUtils.trimToNull(llm.getBaseUrl());
            }
            if (model == null) {
                model = StringUtils.trimToNull(llm.getModel());
            }
            if (StringUtils.isBlank(config.getWebSearchGrokInterfaceUrl())
                    && StringUtils.isNotBlank(llm.getInterfaceUrl())) {
                interfaceUrl = llm.getInterfaceUrl().trim();
            }
        }

        if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(baseUrl) || StringUtils.isBlank(model)) {
            return null;
        }

        // auto 下仅当模型像 grok/xai，或显式配置了 grok_* 时启用
        boolean explicitGrokConfig = StringUtils.isNotBlank(config.getWebSearchGrokApiKey())
                || StringUtils.isNotBlank(config.getWebSearchGrokBaseUrl())
                || StringUtils.isNotBlank(config.getWebSearchGrokModel());
        if (!explicitGrokConfig && !looksLikeGrokModel(model) && !looksLikeXaiEndpoint(baseUrl)) {
            // auto 模式不能把任意 OpenAI 兼容模型误当成 Grok 搜索后端，避免错误发送 search 参数。
            return null;
        }
        return new ProviderPlan("grok", new ProviderConfig(apiKey, baseUrl, interfaceUrl, model));
    }

    private LLMSettings resolveAgentLlmSettings() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return null;
        }
        try {
            String modelName = null;
            if (agentContext.getRuntimeDependencies().getReactorConfig() != null) {
                modelName = agentContext.getRuntimeDependencies().getReactorConfig().getReactModelName();
            }
            return agentContext.getRuntimeDependencies().resolveLlmSettings(modelName);
        } catch (Exception e) {
            log.debug("{} resolve llm settings for web search skipped: {}", requestId(), e.getMessage());
            return null;
        }
    }

    private static boolean looksLikeGrokModel(String model) {
        if (StringUtils.isBlank(model)) {
            return false;
        }
        String m = model.toLowerCase(Locale.ROOT);
        return m.contains("grok") || m.contains("xai");
    }

    private static boolean looksLikeXaiEndpoint(String baseUrl) {
        if (StringUtils.isBlank(baseUrl)) {
            return false;
        }
        String u = baseUrl.toLowerCase(Locale.ROOT);
        return u.contains("x.ai") || u.contains("xai");
    }

    private List<String> readStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (StringUtils.isNotBlank(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private ToolResultPayload failure(String message) {
        return ToolResultPayload.failureFrom(message, null);
    }

    private String requestId() {
        return agentContext == null ? "unknown" : StringUtils.defaultString(agentContext.getRequestId(), "unknown");
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("WebSearchTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    /** 惰性取 HTTP 端口；缺失时返回 null，由 provider 在使用处报错。 */
    /**
     * 取 HTTP 端口。provider 构造时只收 Supplier，真正发请求时才解析，
     * 但 execute() 已在进入循环前校验过一次，这里的失败说明依赖装配缺失。
     */
    private RemoteHttpPort optionalRemoteHttpPort() {
        return requireHttpPort();
    }

    /** 依赖缺失时立即抛错，不让每个 provider 都以自己的名义失败一遍。 */
    private RemoteHttpPort requireHttpPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("WebSearchTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireRemoteHttpPort();
    }

    /**
     * 一个待尝试的 provider：名称 + 运行配置。
     */
    private record ProviderPlan(String name, ProviderConfig config) {
    }
}
