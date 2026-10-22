package org.wwz.ai.domain.agent.runtime.tool.common.websearch.provider;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchBundle;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchHit;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchProvider;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchSupport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Brave 搜索。域名过滤通过查询语法表达，响应结构位于 {@code web.results}。
 */
public final class BraveSearchProvider implements SearchProvider {

    private final Supplier<RemoteHttpPort> httpSupplier;

    public BraveSearchProvider(Supplier<RemoteHttpPort> httpSupplier) {
        this.httpSupplier = httpSupplier;
    }

    @Override
    public String name() {
        return "brave";
    }

    @Override
    public SearchBundle search(SearchRequest request) throws Exception {
        String filteredQuery = SearchSupport.applyDomainFilters(
                request.query(), request.allowedDomains(), request.blockedDomains());
        String url = "https://api.search.brave.com/res/v1/web/search?q="
                + URLEncoder.encode(filteredQuery, StandardCharsets.UTF_8)
                + "&count=" + SearchSupport.MAX_RESULTS;

        String responseText = SearchSupport.execute(
                SearchSupport.requireHttp(httpSupplier, "Brave"),
                "GET",
                url,
                Map.of(
                        "Accept", "application/json",
                        "X-Subscription-Token", request.config().apiKey()
                ),
                null);

        JSONObject root = JSON.parseObject(responseText);
        JSONObject web = root == null ? null : root.getJSONObject("web");
        JSONArray results = web == null ? null : web.getJSONArray("results");
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
                    item.getString("title"), item.getString("url"), item.getString("description"));
            if (hit != null) {
                hits.add(hit);
            }
        }
        return new SearchBundle(hits, null);
    }
}
