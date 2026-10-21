package org.wwz.ai.domain.agent.runtime.tool.common.websearch.provider;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchBundle;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchHit;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchProvider;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grok/xAI 原生搜索。
 * <p>
 * 优先使用 {@code tools=[{type:web_search}]} server tool；
 * 失败时降级到 {@code search_parameters} live search（兼容 xAI 旧接口）。
 */
public final class GrokSearchProvider implements SearchProvider {

    private static final Pattern MARKDOWN_LINK =
            Pattern.compile("\\[([^\\]]+)]\\((https?://[^)]+)\\)");
    private static final Pattern BARE_URL =
            Pattern.compile("https?://[^\\s)>\"]+");

    private final Supplier<RemoteHttpPort> httpSupplier;

    public GrokSearchProvider(Supplier<RemoteHttpPort> httpSupplier) {
        this.httpSupplier = httpSupplier;
    }

    @Override
    public String name() {
        return "grok";
    }

    @Override
    public SearchBundle search(SearchRequest request) throws Exception {
        String endpoint = SearchSupport.joinUrl(request.config().baseUrl(), request.config().interfaceUrl());
        String filteredQuery = SearchSupport.applyDomainFilters(
                request.query(), request.allowedDomains(), request.blockedDomains());

        Exception firstError = null;
        try {
            return callWithServerTool(endpoint, request, filteredQuery);
        } catch (Exception e) {
            firstError = e;
        }
        try {
            return callWithSearchParameters(endpoint, request, filteredQuery);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Grok native search failed: server_tool=" + firstError.getMessage()
                            + "; search_parameters=" + e.getMessage(), e);
        }
    }

    private SearchBundle callWithServerTool(String endpoint, SearchRequest request, String filteredQuery) throws Exception {
        Map<String, Object> body = baseBody(request, filteredQuery,
                "You are a web search assistant. Use the web_search tool. "
                        + "Return concise findings with source titles and URLs as markdown links.");
        body.put("tools", List.of(Map.of("type", "web_search")));
        body.put("tool_choice", "auto");
        return parseChatResponse(post(endpoint, request, body), request.query(), "web_search");
    }

    private SearchBundle callWithSearchParameters(String endpoint, SearchRequest request, String filteredQuery) throws Exception {
        Map<String, Object> searchParameters = new LinkedHashMap<>();
        searchParameters.put("mode", "on");
        searchParameters.put("return_citations", true);
        searchParameters.put("max_search_results", SearchSupport.MAX_RESULTS);

        Map<String, Object> body = baseBody(request, filteredQuery,
                "You are a web search assistant. Use live web search. "
                        + "Return concise findings with source titles and URLs as markdown links.");
        body.put("search_parameters", searchParameters);
        return parseChatResponse(post(endpoint, request, body), request.query(), "search_parameters");
    }

    private Map<String, Object> baseBody(SearchRequest request, String filteredQuery, String systemPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.config().model());
        body.put("temperature", 0);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", "Search the web and summarize results for: " + filteredQuery)
        ));
        return body;
    }

    private String post(String endpoint, SearchRequest request, Map<String, Object> body) throws Exception {
        return SearchSupport.execute(
                SearchSupport.requireHttp(httpSupplier, "Grok"),
                "POST",
                endpoint,
                Map.of(
                        "Content-Type", "application/json",
                        "Authorization", "Bearer " + request.config().apiKey()
                ),
                JSON.toJSONString(body));
    }

    private SearchBundle parseChatResponse(String responseText, String originalQuery, String mode) {
        if (StringUtils.isBlank(responseText)) {
            throw new IllegalStateException("empty response");
        }
        JSONObject root = JSON.parseObject(responseText);
        if (root == null) {
            throw new IllegalStateException("invalid json");
        }
        if (root.containsKey("error")) {
            throw new IllegalStateException(String.valueOf(root.get("error")));
        }

        // 先取模型摘要，再从多种 citation/annotation 形态提取来源；没有摘要但有来源时仍可返回可用结果。
        String content = extractAssistantContent(root);
        List<SearchHit> hits = extractHits(root, content);
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

    private List<SearchHit> extractHits(JSONObject root, String content) {
        List<SearchHit> hits = new ArrayList<>();
        // citation 字段在根、choice、message 和 annotation 层级都可能出现，按优先级收集后统一去重限量。
        collectCitations(root.getJSONArray("citations"), hits);
        JSONArray choices = root.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject first = choices.getJSONObject(0);
            if (first != null) {
                collectCitations(first.getJSONArray("citations"), hits);
                JSONObject message = first.getJSONObject("message");
                if (message != null) {
                    collectCitations(message.getJSONArray("citations"), hits);
                    Object annotations = message.get("annotations");
                    if (annotations instanceof JSONArray arr) {
                        for (int i = 0; i < arr.size(); i++) {
                            JSONObject ann = arr.getJSONObject(i);
                            if (ann == null) {
                                continue;
                            }
                            String url = SearchSupport.firstNonBlank(
                                    ann.getString("url"), ann.getString("source_url"));
                            String title = SearchSupport.firstNonBlank(
                                    ann.getString("title"), ann.getString("name"), url);
                            SearchHit hit = SearchSupport.normalizeHit(title, url, ann.getString("snippet"));
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
            while (md.find() && hits.size() < SearchSupport.MAX_RESULTS) {
                SearchHit hit = SearchSupport.normalizeHit(md.group(1), md.group(2), null);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        if (hits.isEmpty() && StringUtils.isNotBlank(content)) {
            // 最后从裸 URL 兜底，确保没有结构化 citation 时仍能保留来源。
            Matcher bare = BARE_URL.matcher(content);
            while (bare.find() && hits.size() < SearchSupport.MAX_RESULTS) {
                String url = bare.group();
                SearchHit hit = SearchSupport.normalizeHit(url, url, null);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return SearchSupport.dedupeHits(hits);
    }

    private void collectCitations(JSONArray citations, List<SearchHit> hits) {
        if (citations == null) {
            return;
        }
        for (int i = 0; i < citations.size(); i++) {
            Object item = citations.get(i);
            if (item instanceof String url) {
                SearchHit hit = SearchSupport.normalizeHit(url, url, null);
                if (hit != null) {
                    hits.add(hit);
                }
                continue;
            }
            if (item instanceof JSONObject obj) {
                String url = SearchSupport.firstNonBlank(
                        obj.getString("url"), obj.getString("source_url"), obj.getString("link"));
                String title = SearchSupport.firstNonBlank(obj.getString("title"), obj.getString("name"), url);
                String snippet = SearchSupport.firstNonBlank(
                        obj.getString("snippet"), obj.getString("text"), obj.getString("description"));
                SearchHit hit = SearchSupport.normalizeHit(title, url, snippet);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
    }
}
