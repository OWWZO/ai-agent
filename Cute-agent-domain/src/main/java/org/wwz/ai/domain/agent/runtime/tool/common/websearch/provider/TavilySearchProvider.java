package org.wwz.ai.domain.agent.runtime.tool.common.websearch.provider;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
 * Tavily 搜索。原生支持域名白名单/黑名单，优先放入请求字段。
 */
public final class TavilySearchProvider implements SearchProvider {

    private final Supplier<RemoteHttpPort> httpSupplier;

    public TavilySearchProvider(Supplier<RemoteHttpPort> httpSupplier) {
        this.httpSupplier = httpSupplier;
    }

    @Override
    public String name() {
        return "tavily";
    }

    @Override
    public SearchBundle search(SearchRequest request) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", request.query());
        body.put("max_results", SearchSupport.MAX_RESULTS);
        body.put("search_depth", "basic");
        body.put("include_answer", false);
        if (!request.allowedDomains().isEmpty()) {
            body.put("include_domains", request.allowedDomains());
        }
        if (!request.blockedDomains().isEmpty()) {
            body.put("exclude_domains", request.blockedDomains());
        }

        String responseText = SearchSupport.execute(
                SearchSupport.requireHttp(httpSupplier, "Tavily"),
                "POST",
                "https://api.tavily.com/search",
                Map.of(
                        "Content-Type", "application/json",
                        "Authorization", "Bearer " + request.config().apiKey()
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
            SearchHit hit = SearchSupport.normalizeHit(
                    item.getString("title"), item.getString("url"), item.getString("content"));
            if (hit != null) {
                hits.add(hit);
            }
        }
        return new SearchBundle(hits, null);
    }
}
