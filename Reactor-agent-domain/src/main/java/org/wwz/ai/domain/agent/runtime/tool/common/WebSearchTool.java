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
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web 搜索工具（对标 cc-haha WebSearchTool 的 Tavily/Brave 外部检索路径）。
 * 不依赖 Anthropic 原生 server tool。
 */
@Slf4j
@Data
public class WebSearchTool implements BaseTool {

    public static final String TOOL_NAME = "WebSearch";

    private static final int MAX_RESULTS = 8;
    private static final long HTTP_TIMEOUT_SECONDS = 30L;

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
            ResolvedProvider resolved = resolveProvider(config);
            if (resolved.provider == Provider.DISABLED) {
                return failure("WebSearch 未配置。请设置 autobots.autoagent.web_search.tavily_api_key 或 brave_api_key。");
            }

            List<SearchHit> hits = resolved.provider == Provider.TAVILY
                    ? searchTavily(query, allowedDomains, blockedDomains, resolved.apiKey)
                    : searchBrave(query, allowedDomains, blockedDomains, resolved.apiKey);

            double durationSeconds = (System.currentTimeMillis() - start) / 1000.0;
            return buildSuccess(query, resolved.provider, hits, durationSeconds);
        } catch (Exception e) {
            log.error("{} WebSearch execute error, input={}", requestId(), input, e);
            return failure("WebSearch 执行失败：" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private List<SearchHit> searchTavily(String query,
                                         List<String> allowedDomains,
                                         List<String> blockedDomains,
                                         String apiKey) throws Exception {
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

    private ToolResultPayload buildSuccess(String query,
                                           Provider provider,
                                           List<SearchHit> hits,
                                           double durationSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append("Web search results for query: \"").append(query).append("\"\n");
        sb.append("Search provider: ").append(provider.name().toLowerCase()).append('\n');
        sb.append(String.format("Duration: %.2fs\n\n", durationSeconds));
        if (hits.isEmpty()) {
            sb.append("No results found.\n");
        } else {
            int index = 1;
            for (SearchHit hit : hits) {
                sb.append(index++).append(". ").append(hit.title()).append('\n');
                sb.append("   URL: ").append(hit.url()).append('\n');
                if (StringUtils.isNotBlank(hit.snippet())) {
                    sb.append("   ").append(hit.snippet().replace('\n', ' ')).append('\n');
                }
                sb.append('\n');
            }
        }
        sb.append("REMINDER: Include the sources above in your response using markdown hyperlinks.");
        return ToolResultPayload.text(sb.toString().trim());
    }

    private ResolvedProvider resolveProvider(ReactorConfig config) {
        String mode = StringUtils.defaultIfBlank(config.getWebSearchMode(), "auto").trim().toLowerCase();
        String tavilyKey = StringUtils.trimToNull(config.getWebSearchTavilyApiKey());
        String braveKey = StringUtils.trimToNull(config.getWebSearchBraveApiKey());

        if ("disabled".equals(mode)) {
            return new ResolvedProvider(Provider.DISABLED, null);
        }
        if ("tavily".equals(mode)) {
            return tavilyKey != null
                    ? new ResolvedProvider(Provider.TAVILY, tavilyKey)
                    : new ResolvedProvider(Provider.DISABLED, null);
        }
        if ("brave".equals(mode)) {
            return braveKey != null
                    ? new ResolvedProvider(Provider.BRAVE, braveKey)
                    : new ResolvedProvider(Provider.DISABLED, null);
        }
        // auto
        if (tavilyKey != null) {
            return new ResolvedProvider(Provider.TAVILY, tavilyKey);
        }
        if (braveKey != null) {
            return new ResolvedProvider(Provider.BRAVE, braveKey);
        }
        return new ResolvedProvider(Provider.DISABLED, null);
    }

    private static String applyDomainFiltersToQuery(String query,
                                                    List<String> allowedDomains,
                                                    List<String> blockedDomains) {
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
        if (StringUtils.isBlank(title) || StringUtils.isBlank(url)) {
            return null;
        }
        return new SearchHit(title.trim(), url.trim(), StringUtils.defaultString(snippet).trim());
    }

    private ToolResultPayload failure(String message) {
        return ToolResultPayload.failure(message, message, null, message);
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
        TAVILY,
        BRAVE,
        DISABLED
    }

    private record ResolvedProvider(Provider provider, String apiKey) {
    }

    private record SearchHit(String title, String url, String snippet) {
    }
}
