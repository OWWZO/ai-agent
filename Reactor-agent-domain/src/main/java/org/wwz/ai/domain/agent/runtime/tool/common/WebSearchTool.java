package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web 搜索工具。
 * 优先级：Grok/xAI 原生 server tool（web_search / search_parameters）→ Exa → Tavily → Brave。
 */
@Slf4j
@Data
public class WebSearchTool implements BaseTool {

    public static final String TOOL_NAME = "WebSearch";

    private static final int MAX_RESULTS = 8;
    private static final long HTTP_TIMEOUT_SECONDS = 60L;
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)]+)\\)");
    private static final Pattern BARE_URL = Pattern.compile("https?://[^\\s)>\"]+");

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
                Prefer Grok native web search when available; otherwise Exa/Tavily/Brave.
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
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> params = coerceMap(input);
            String query = StringUtils.trimToEmpty(valueAsString(params.get("query")));
            if (StringUtils.isBlank(query) || query.length() < 2) {
                return failure("WebSearch 失败：query 至少 2 个字符");
            }

            List<String> allowedDomains = readStringList(params.get("allowed_domains"));
            List<String> blockedDomains = readStringList(params.get("blocked_domains"));
            if (!allowedDomains.isEmpty() && !blockedDomains.isEmpty()) {
                return failure("WebSearch 失败：不能同时指定 allowed_domains 与 blocked_domains");
            }

            ReactorConfig config = requireReactorConfig();
            List<ProviderPlan> plans = resolveProviderPlans(config);
            if (plans.isEmpty()) {
                return failure("WebSearch 未配置。请配置 Grok/xAI（web_search.grok_* 或 llm.default）或 exa/tavily/brave API key。");
            }

            Exception lastError = null;
            // provider 按优先级逐个尝试；单个供应商超时或响应格式异常时，继续使用下一个可用后端。
            for (ProviderPlan plan : plans) {
                try {
                    SearchBundle bundle = executePlan(plan, query, allowedDomains, blockedDomains);
                    double durationSeconds = (System.currentTimeMillis() - start) / 1000.0;
                    return buildSuccess(query, plan.provider(), bundle, durationSeconds);
                } catch (Exception e) {
                    lastError = e;
                    log.warn("{} WebSearch provider {} failed, try next: {}",
                            requestId(), plan.provider(), e.getMessage());
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

    private SearchBundle executePlan(ProviderPlan plan,
                                     String query,
                                     List<String> allowedDomains,
                                     List<String> blockedDomains) throws Exception {
        return switch (plan.provider()) {
            case GROK -> searchGrok(query, allowedDomains, blockedDomains, plan);
            case EXA -> new SearchBundle(searchExa(query, allowedDomains, blockedDomains, plan), null);
            case TAVILY -> new SearchBundle(searchTavily(query, allowedDomains, blockedDomains, plan.apiKey()), null);
            case BRAVE -> new SearchBundle(searchBrave(query, allowedDomains, blockedDomains, plan.apiKey()), null);
            case DISABLED -> throw new IllegalStateException("disabled");
        };
    }

    /**
     * Grok/xAI 原生搜索：
     * 1) tools=[{type:web_search}] server tool
     * 2) 兼容 search_parameters live search
     */
    private SearchBundle searchGrok(String query,
                                    List<String> allowedDomains,
                                    List<String> blockedDomains,
                                    ProviderPlan plan) throws Exception {
        String endpoint = joinUrl(plan.baseUrl(), plan.interfaceUrl());
        String filteredQuery = applyDomainFiltersToQuery(query, allowedDomains, blockedDomains);

        Exception firstError = null;
        try {
            // 新接口优先使用 server tool；保留 search_parameters 作为 xAI 兼容接口的降级路径。
            return callGrokWithServerTool(endpoint, plan, filteredQuery, query);
        } catch (Exception e) {
            firstError = e;
            log.info("{} Grok web_search server tool failed, fallback search_parameters: {}",
                    requestId(), e.getMessage());
        }
        try {
            return callGrokWithSearchParameters(endpoint, plan, filteredQuery, query);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Grok native search failed: server_tool=" + firstError.getMessage()
                            + "; search_parameters=" + e.getMessage(), e);
        }
    }

    private SearchBundle callGrokWithServerTool(String endpoint,
                                                ProviderPlan plan,
                                                String filteredQuery,
                                                String originalQuery) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", plan.model());
        body.put("temperature", 0);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "You are a web search assistant. Use the web_search tool. "
                                + "Return concise findings with source titles and URLs as markdown links."),
                Map.of("role", "user", "content",
                        "Search the web and summarize results for: " + filteredQuery)
        ));
        body.put("tools", List.of(Map.of("type", "web_search")));
        body.put("tool_choice", "auto");

        String responseText = postJson(endpoint, plan.apiKey(), body);
        return parseGrokChatResponse(responseText, originalQuery, "web_search");
    }

    private SearchBundle callGrokWithSearchParameters(String endpoint,
                                                      ProviderPlan plan,
                                                      String filteredQuery,
                                                      String originalQuery) throws Exception {
        Map<String, Object> searchParameters = new LinkedHashMap<>();
        searchParameters.put("mode", "on");
        searchParameters.put("return_citations", true);
        searchParameters.put("max_search_results", MAX_RESULTS);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", plan.model());
        body.put("temperature", 0);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "You are a web search assistant. Use live web search. "
                                + "Return concise findings with source titles and URLs as markdown links."),
                Map.of("role", "user", "content",
                        "Search the web and summarize results for: " + filteredQuery)
        ));
        body.put("search_parameters", searchParameters);

        String responseText = postJson(endpoint, plan.apiKey(), body);
        return parseGrokChatResponse(responseText, originalQuery, "search_parameters");
    }

    private SearchBundle parseGrokChatResponse(String responseText,
                                               String originalQuery,
                                               String mode) {
        if (StringUtils.isBlank(responseText)) {
            throw new IllegalStateException("empty response");
        }
        JSONObject root = JSON.parseObject(responseText);
        if (root == null) {
            throw new IllegalStateException("invalid json");
        }
        if (root.containsKey("error")) {
            Object err = root.get("error");
            throw new IllegalStateException(String.valueOf(err));
        }

        // 先取模型摘要，再从多种 citation/annotation 形态提取来源；没有摘要但有来源时仍可返回可用结果。
        String content = extractAssistantContent(root);
        List<SearchHit> hits = extractHitsFromGrok(root, content);
        if (StringUtils.isBlank(content) && hits.isEmpty()) {
            throw new IllegalStateException("no content/citations from grok (" + mode + ")");
        }
        String summary = StringUtils.isNotBlank(content)
                ? content.trim()
                : "Grok search completed for: " + originalQuery;
        return new SearchBundle(hits, summary);
    }

    private String extractAssistantContent(JSONObject root) {
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject first = choices.getJSONObject(0);
        if (first == null) {
            return "";
        }
        JSONObject message = first.getJSONObject("message");
        if (message == null) {
            return first.getString("text");
        }
        Object content = message.get("content");
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof JSONArray parts) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                Object part = parts.get(i);
                if (part instanceof String s) {
                    sb.append(s);
                } else if (part instanceof JSONObject obj) {
                    String t = obj.getString("text");
                    if (StringUtils.isNotBlank(t)) {
                        sb.append(t);
                    }
                }
            }
            return sb.toString();
        }
        return message.getString("content");
    }

    private List<SearchHit> extractHitsFromGrok(JSONObject root, String content) {
        List<SearchHit> hits = new ArrayList<>();
        // citation 字段在根、choice、message 和 annotation 层级都可能出现，按优先级收集后统一去重限量。
        collectCitationArray(root.getJSONArray("citations"), hits);
        JSONArray choices = root.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject first = choices.getJSONObject(0);
            if (first != null) {
                collectCitationArray(first.getJSONArray("citations"), hits);
                JSONObject message = first.getJSONObject("message");
                if (message != null) {
                    collectCitationArray(message.getJSONArray("citations"), hits);
                    Object annotations = message.get("annotations");
                    if (annotations instanceof JSONArray arr) {
                        for (int i = 0; i < arr.size(); i++) {
                            JSONObject ann = arr.getJSONObject(i);
                            if (ann == null) {
                                continue;
                            }
                            String url = firstNonBlank(ann.getString("url"), ann.getString("source_url"));
                            String title = firstNonBlank(ann.getString("title"), ann.getString("name"), url);
                            SearchHit hit = normalizeHit(title, url, ann.getString("snippet"));
                            if (hit != null) {
                                hits.add(hit);
                            }
                        }
                    }
                }
            }
        }
        if (hits.isEmpty() && StringUtils.isNotBlank(content)) {
            // 兼容只在正文中输出 markdown 链接的模型响应。
            Matcher md = MARKDOWN_LINK.matcher(content);
            while (md.find() && hits.size() < MAX_RESULTS) {
                SearchHit hit = normalizeHit(md.group(1), md.group(2), null);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        if (hits.isEmpty() && StringUtils.isNotBlank(content)) {
            // 最后从裸 URL 兜底，确保没有结构化 citation 时仍能保留来源。
            Matcher bare = BARE_URL.matcher(content);
            while (bare.find() && hits.size() < MAX_RESULTS) {
                String url = bare.group();
                SearchHit hit = normalizeHit(url, url, null);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return dedupeHits(hits);
    }

    private void collectCitationArray(JSONArray citations, List<SearchHit> hits) {
        if (citations == null) {
            return;
        }
        for (int i = 0; i < citations.size(); i++) {
            Object item = citations.get(i);
            if (item instanceof String url) {
                SearchHit hit = normalizeHit(url, url, null);
                if (hit != null) {
                    hits.add(hit);
                }
                continue;
            }
            if (item instanceof JSONObject obj) {
                String url = firstNonBlank(obj.getString("url"), obj.getString("source_url"), obj.getString("link"));
                String title = firstNonBlank(obj.getString("title"), obj.getString("name"), url);
                String snippet = firstNonBlank(obj.getString("snippet"), obj.getString("text"), obj.getString("description"));
                SearchHit hit = normalizeHit(title, url, snippet);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
    }

    private List<SearchHit> searchExa(String query,
                                      List<String> allowedDomains,
                                      List<String> blockedDomains,
                                      ProviderPlan plan) throws Exception {
        // 与 reactor-tool ExaSearch 对齐：POST /search + x-api-key，正文走 contents.text。
        String endpoint = StringUtils.defaultIfBlank(plan.baseUrl(), "https://api.exa.ai/search").trim();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("numResults", MAX_RESULTS);
        body.put("useAutoprompt", true);
        body.put("contents", Map.of(
                "text", Map.of("maxCharacters", 2000)
        ));
        if (!allowedDomains.isEmpty()) {
            body.put("includeDomains", allowedDomains);
        }
        if (!blockedDomains.isEmpty()) {
            body.put("excludeDomains", blockedDomains);
        }

        String responseText = requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                .method("POST")
                .url(endpoint)
                .headers(Map.of(
                        "Accept", "application/json",
                        "Content-Type", "application/json",
                        "x-api-key", plan.apiKey()
                ))
                .body(JSON.toJSONString(body))
                .connectTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .readTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .writeTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .callTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .build());

        JSONObject root = JSON.parseObject(responseText);
        JSONArray results = root == null ? null : root.getJSONArray("results");
        List<SearchHit> hits = new ArrayList<>();
        if (results == null) {
            return hits;
        }
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String snippet = firstNonBlank(
                    item.getString("text"),
                    item.getString("extract"),
                    item.getString("summary"),
                    item.getString("snippet")
            );
            if (StringUtils.isNotBlank(snippet) && snippet.length() > 500) {
                snippet = snippet.substring(0, 500);
            }
            SearchHit hit = normalizeHit(item.getString("title"), item.getString("url"), snippet);
            if (hit != null) {
                hits.add(hit);
            }
        }
        return hits;
    }

    private List<SearchHit> searchTavily(String query,
                                         List<String> allowedDomains,
                                         List<String> blockedDomains,
                                         String apiKey) throws Exception {
        // Tavily 原生支持域名白名单/黑名单，优先放入请求字段，避免只靠自然语言过滤。
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("max_results", MAX_RESULTS);
        body.put("search_depth", "basic");
        body.put("include_answer", false);
        if (!allowedDomains.isEmpty()) {
            body.put("include_domains", allowedDomains);
        }
        if (!blockedDomains.isEmpty()) {
            body.put("exclude_domains", blockedDomains);
        }

        String responseText = requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                .method("POST")
                .url("https://api.tavily.com/search")
                .headers(Map.of(
                        "Content-Type", "application/json",
                        "Authorization", "Bearer " + apiKey
                ))
                .body(JSON.toJSONString(body))
                .connectTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .readTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .writeTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .callTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .build());

        JSONObject root = JSON.parseObject(responseText);
        JSONArray results = root == null ? null : root.getJSONArray("results");
        List<SearchHit> hits = new ArrayList<>();
        if (results == null) {
            return hits;
        }
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            if (item == null) {
                continue;
            }
            SearchHit hit = normalizeHit(item.getString("title"), item.getString("url"), item.getString("content"));
            if (hit != null) {
                hits.add(hit);
            }
        }
        return hits;
    }

    private List<SearchHit> searchBrave(String query,
                                        List<String> allowedDomains,
                                        List<String> blockedDomains,
                                        String apiKey) throws Exception {
        // Brave 通过查询语法表达域名过滤；响应结构位于 web.results，与 Tavily 不同，统一在此处转成 SearchHit。
        String filteredQuery = applyDomainFiltersToQuery(query, allowedDomains, blockedDomains);
        String url = "https://api.search.brave.com/res/v1/web/search?q="
                + URLEncoder.encode(filteredQuery, StandardCharsets.UTF_8)
                + "&count=" + MAX_RESULTS;

        String responseText = requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                .method("GET")
                .url(url)
                .headers(Map.of(
                        "Accept", "application/json",
                        "X-Subscription-Token", apiKey
                ))
                .connectTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .readTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .writeTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .callTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .build());

        JSONObject root = JSON.parseObject(responseText);
        JSONObject web = root == null ? null : root.getJSONObject("web");
        JSONArray results = web == null ? null : web.getJSONArray("results");
        List<SearchHit> hits = new ArrayList<>();
        if (results == null) {
            return hits;
        }
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            if (item == null) {
                continue;
            }
            SearchHit hit = normalizeHit(item.getString("title"), item.getString("url"), item.getString("description"));
            if (hit != null) {
                hits.add(hit);
            }
        }
        return hits;
    }

    private String postJson(String url, String apiKey, Map<String, Object> body) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        return requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                .method("POST")
                .url(url)
                .headers(headers)
                .body(JSON.toJSONString(body))
                .connectTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .readTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .writeTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .callTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .build());
    }

    private ToolResultPayload buildSuccess(String query,
                                           Provider provider,
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
        data.put("provider", provider.name().toLowerCase(Locale.ROOT));
        data.put("durationSec", Math.round(durationSeconds * 100.0) / 100.0);
        if (StringUtils.isNotBlank(bundle.summary())) {
            data.put("summary", bundle.summary().trim());
        }
        data.put("hits", hitRows);
        data.put("reminder", "Cite sources with markdown hyperlinks in the final answer.");
        return ToolResultPayload.fromData(data);
    }

    /**
     * auto: grok → exa → tavily → brave
     * grok / exa / tavily / brave / disabled 为强制模式
     */
    private List<ProviderPlan> resolveProviderPlans(ReactorConfig config) {
        String mode = StringUtils.defaultIfBlank(config.getWebSearchMode(), "auto").trim().toLowerCase(Locale.ROOT);
        List<ProviderPlan> plans = new ArrayList<>();

        if ("disabled".equals(mode)) {
            return plans;
        }

        ProviderPlan grok = resolveGrokPlan(config);
        ProviderPlan exa = resolveExaPlan(config);
        String tavilyKey = StringUtils.trimToNull(config.getWebSearchTavilyApiKey());
        String braveKey = StringUtils.trimToNull(config.getWebSearchBraveApiKey());

        if ("grok".equals(mode) || "xai".equals(mode)) {
            if (grok != null) {
                plans.add(grok);
            }
            return plans;
        }
        if ("exa".equals(mode)) {
            if (exa != null) {
                plans.add(exa);
            }
            return plans;
        }
        if ("tavily".equals(mode)) {
            if (tavilyKey != null) {
                plans.add(new ProviderPlan(Provider.TAVILY, tavilyKey, null, null, null));
            }
            return plans;
        }
        if ("brave".equals(mode)) {
            if (braveKey != null) {
                plans.add(new ProviderPlan(Provider.BRAVE, braveKey, null, null, null));
            }
            return plans;
        }

        // auto 只加入实际具备凭据的 Provider，执行阶段才能按顺序安全尝试而不制造无意义错误。
        if (grok != null) {
            plans.add(grok);
        }
        if (exa != null) {
            plans.add(exa);
        }
        if (tavilyKey != null) {
            plans.add(new ProviderPlan(Provider.TAVILY, tavilyKey, null, null, null));
        }
        if (braveKey != null) {
            plans.add(new ProviderPlan(Provider.BRAVE, braveKey, null, null, null));
        }
        return plans;
    }

    private ProviderPlan resolveExaPlan(ReactorConfig config) {
        String apiKey = StringUtils.trimToNull(config.getWebSearchExaApiKey());
        if (apiKey == null) {
            return null;
        }
        String searchUrl = StringUtils.defaultIfBlank(
                config.getWebSearchExaSearchUrl(),
                "https://api.exa.ai/search"
        ).trim();
        return new ProviderPlan(Provider.EXA, apiKey, searchUrl, null, null);
    }

    private ProviderPlan resolveGrokPlan(ReactorConfig config) {
        String apiKey = firstNonBlank(
                StringUtils.trimToNull(config.getWebSearchGrokApiKey()),
                null
        );
        String baseUrl = firstNonBlank(
                StringUtils.trimToNull(config.getWebSearchGrokBaseUrl()),
                null
        );
        String model = firstNonBlank(
                StringUtils.trimToNull(config.getWebSearchGrokModel()),
                null
        );
        String interfaceUrl = firstNonBlank(
                StringUtils.trimToNull(config.getWebSearchGrokInterfaceUrl()),
                "/v1/chat/completions"
        );

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

        return new ProviderPlan(Provider.GROK, apiKey, baseUrl, interfaceUrl, model);
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

    private static String applyDomainFiltersToQuery(String query,
                                                    List<String> allowedDomains,
                                                    List<String> blockedDomains) {
        // allowed 与 blocked 已在入口互斥校验；这里仅负责转换为各 provider 都能理解的 site 语法。
        StringBuilder sb = new StringBuilder();
        if (!allowedDomains.isEmpty()) {
            sb.append('(');
            for (int i = 0; i < allowedDomains.size(); i++) {
                if (i > 0) {
                    sb.append(" OR ");
                }
                sb.append("site:").append(allowedDomains.get(i));
            }
            sb.append(") ");
        }
        for (String domain : blockedDomains) {
            sb.append("-site:").append(domain).append(' ');
        }
        sb.append(query);
        return sb.toString().trim();
    }

    private static SearchHit normalizeHit(String title, String url, String snippet) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String safeTitle = StringUtils.defaultIfBlank(title, url).trim();
        return new SearchHit(safeTitle, url.trim(), StringUtils.defaultString(snippet).trim());
    }

    private static List<SearchHit> dedupeHits(List<SearchHit> hits) {
        Map<String, SearchHit> map = new LinkedHashMap<>();
        // LinkedHashMap 同时保证 URL 去重和 provider 返回顺序，达到统一的 MAX_RESULTS 上限。
        for (SearchHit hit : hits) {
            if (hit == null || StringUtils.isBlank(hit.url())) {
                continue;
            }
            map.putIfAbsent(hit.url(), hit);
            if (map.size() >= MAX_RESULTS) {
                break;
            }
        }
        return new ArrayList<>(map.values());
    }

    private static String joinUrl(String baseUrl, String interfaceUrl) {
        String base = StringUtils.removeEnd(StringUtils.trimToEmpty(baseUrl), "/");
        String path = StringUtils.defaultIfBlank(interfaceUrl, "/v1/chat/completions").trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private ToolResultPayload failure(String message) {
        return ToolResultPayload.failureFrom(message, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> coerceMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
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

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
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

    private RemoteHttpPort requireRemoteHttpPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("WebSearchTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireRemoteHttpPort();
    }

    private enum Provider {
        GROK,
        EXA,
        TAVILY,
        BRAVE,
        DISABLED
    }

    private record ProviderPlan(Provider provider,
                                String apiKey,
                                String baseUrl,
                                String interfaceUrl,
                                String model) {
    }

    private record SearchBundle(List<SearchHit> hits, String summary) {
    }

    private record SearchHit(String title, String url, String snippet) {
    }
}
