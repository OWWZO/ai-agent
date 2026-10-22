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
 * Exa 搜索：POST /search + x-api-key，正文走 contents.text。
 */
public final class ExaSearchProvider implements SearchProvider {

    private final Supplier<RemoteHttpPort> httpSupplier;

    public ExaSearchProvider(Supplier<RemoteHttpPort> httpSupplier) {
        this.httpSupplier = httpSupplier;
    }

    @Override
    public String name() {
        return "exa";
    }

    @Override
    public SearchBundle search(SearchRequest request) throws Exception {
        String endpoint = StringUtils.defaultIfBlank(
                request.config().baseUrl(), "https://api.exa.ai/search").trim();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", request.query());
        body.put("numResults", SearchSupport.MAX_RESULTS);
        body.put("useAutoprompt", true);
        body.put("contents", Map.of("text", Map.of("maxCharacters", 2000)));
        if (!request.allowedDomains().isEmpty()) {
            body.put("includeDomains", request.allowedDomains());
        }
        if (!request.blockedDomains().isEmpty()) {
            body.put("excludeDomains", request.blockedDomains());
        }

        String responseText = SearchSupport.execute(
                SearchSupport.requireHttp(httpSupplier, "Exa"),
                "POST",
                endpoint,
                Map.of(
                        "Accept", "application/json",
                        "Content-Type", "application/json",
                        "x-api-key", request.config().apiKey()
                ),
                JSON.toJSONString(body));

        JSONObject root = JSON.parseObject(responseText);
        JSONArray results = root == null ? null : root.getJSONArray("results");
        List<SearchHit> hits = new ArrayList<>();
        if (results == null) {
            return new SearchBundle(hits, null);
        }
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String snippet = SearchSupport.firstNonBlank(
                    item.getString("text"),
                    item.getString("extract"),
                    item.getString("summary"),
                    item.getString("snippet"));
            if (StringUtils.isNotBlank(snippet) && snippet.length() > 500) {
                snippet = snippet.substring(0, 500);
            }
            SearchHit hit = SearchSupport.normalizeHit(item.getString("title"), item.getString("url"), snippet);
            if (hit != null) {
                hits.add(hit);
            }
        }
        return new SearchBundle(hits, null);
    }
}
