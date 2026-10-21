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

/**
 * OpenAI Responses API 原生搜索。
 * <p>
 * Responses API 不使用 Chat Completions 的 messages 或 search_parameters，
 * 而是通过 input + web_search_preview 工具返回 output annotations。
 */
public final class GptSearchProvider implements SearchProvider {

    private final Supplier<RemoteHttpPort> httpSupplier;

    public GptSearchProvider(Supplier<RemoteHttpPort> httpSupplier) {
        this.httpSupplier = httpSupplier;
    }

    @Override
    public String name() {
        return "gpt";
    }

    @Override
    public SearchBundle search(SearchRequest request) throws Exception {
        Map<String, Object> webSearch = new LinkedHashMap<>();
        webSearch.put("type", "web_search_preview");
        if (!request.allowedDomains().isEmpty()) {
            webSearch.put("filters", Map.of("allowed_domains", request.allowedDomains()));
        } else if (!request.blockedDomains().isEmpty()) {
            webSearch.put("filters", Map.of("blocked_domains", request.blockedDomains()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.config().model());
        body.put("input", request.query());
        body.put("tools", List.of(webSearch));

        String endpoint = SearchSupport.joinUrl(request.config().baseUrl(), request.config().interfaceUrl());
        String responseText = SearchSupport.execute(
                SearchSupport.requireHttp(httpSupplier, "GPT"),
                "POST",
                endpoint,
                Map.of(
                        "Content-Type", "application/json",
                        "Authorization", "Bearer " + request.config().apiKey()
                ),
                JSON.toJSONString(body));

        return parseResponse(responseText, request.query());
    }

    private SearchBundle parseResponse(String responseText, String originalQuery) {
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

        String summary = StringUtils.trimToEmpty(root.getString("output_text"));
        List<SearchHit> hits = new ArrayList<>();
        JSONArray output = root.getJSONArray("output");
        if (output != null) {
            for (int i = 0; i < output.size(); i++) {
                JSONObject item = output.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                collectContent(item.getJSONArray("content"), hits, summary);
            }
        }
        if (StringUtils.isBlank(summary)) {
            summary = SearchSupport.extractAssistantContent(root);
        }
        if (StringUtils.isBlank(summary) && hits.isEmpty()) {
            throw new IllegalStateException("no content/citations from gpt");
        }
        return new SearchBundle(
                SearchSupport.dedupeHits(hits),
                StringUtils.defaultIfBlank(summary, "GPT search completed for: " + originalQuery));
    }

    private void collectContent(JSONArray content, List<SearchHit> hits, String summary) {
        if (content == null) {
            return;
        }
        for (int i = 0; i < content.size(); i++) {
            JSONObject part = content.getJSONObject(i);
            if (part == null) {
                continue;
            }
            String text = part.getString("text");
            if (StringUtils.isNotBlank(text) && StringUtils.isBlank(summary)) {
                summary = text;
            }
            JSONArray annotations = part.getJSONArray("annotations");
            if (annotations == null) {
                continue;
            }
            for (int j = 0; j < annotations.size(); j++) {
                JSONObject annotation = annotations.getJSONObject(j);
                if (annotation == null || !"url_citation".equals(annotation.getString("type"))) {
                    continue;
                }
                SearchHit hit = SearchSupport.normalizeHit(
                        SearchSupport.firstNonBlank(annotation.getString("title"), annotation.getString("url")),
                        annotation.getString("url"),
                        text);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
    }

    /** Responses API 与 Chat Completions 的正文位置不同，这里统一兜底。 */
}
