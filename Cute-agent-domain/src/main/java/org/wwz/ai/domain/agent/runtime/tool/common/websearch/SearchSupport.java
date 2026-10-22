package org.wwz.ai.domain.agent.runtime.tool.common.websearch;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 各 provider 共用的 HTTP / 结果归一工具。
 */
public final class SearchSupport {

    /** 单次搜索最多返回条数。 */
    public static final int MAX_RESULTS = 8;
    /** provider HTTP 超时。 */
    public static final long HTTP_TIMEOUT_SECONDS = 60L;

    private SearchSupport() {
    }

    /** 按 provider 语义把域名过滤拼进查询串。 */
    public static String applyDomainFilters(String query,
                                            List<String> allowedDomains,
                                            List<String> blockedDomains) {
        // allowed 与 blocked 已在入口互斥校验；这里仅负责转换为各 provider 都能理解的 site 语法。
        StringBuilder sb = new StringBuilder();
        if (allowedDomains != null && !allowedDomains.isEmpty()) {
            sb.append('(');
            for (int i = 0; i < allowedDomains.size(); i++) {
                if (i > 0) {
                    sb.append(" OR ");
                }
                sb.append("site:").append(allowedDomains.get(i));
            }
            sb.append(") ");
        }
        if (blockedDomains != null) {
            for (String domain : blockedDomains) {
                sb.append("-site:").append(domain).append(' ');
            }
        }
        sb.append(query);
        return sb.toString().trim();
    }

    /** 归一单条结果；url 为空返回 null。 */
    public static SearchHit normalizeHit(String title, String url, String snippet) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String safeTitle = StringUtils.defaultIfBlank(title, url).trim();
        return new SearchHit(safeTitle, url.trim(), StringUtils.defaultString(snippet).trim());
    }

    /** 按 URL 去重并截断到 {@link #MAX_RESULTS}，保持 provider 返回顺序。 */
    public static List<SearchHit> dedupeHits(List<SearchHit> hits) {
        Map<String, SearchHit> map = new LinkedHashMap<>();
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

    /** 返回第一个非空白值（已 trim）。 */
    public static String firstNonBlank(String... values) {
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

    /** 拼接 baseUrl 与 interfaceUrl。 */
    public static String joinUrl(String baseUrl, String interfaceUrl) {
        String base = StringUtils.removeEnd(StringUtils.trimToEmpty(baseUrl), "/");
        String path = StringUtils.defaultIfBlank(interfaceUrl, "/v1/chat/completions").trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    /**
     * 发一次带超时的 HTTP 请求。
     *
     * @param httpPort 由调用方注入，便于测试替换
     */
    public static String execute(RemoteHttpPort httpPort,
                                 String method,
                                 String url,
                                 Map<String, String> headers,
                                 String body) throws IOException {
        RemoteHttpRequest request = RemoteHttpRequest.builder()
                .method(method)
                .url(url)
                .headers(headers)
                .body(body)
                .connectTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .readTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .writeTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .callTimeoutSeconds(HTTP_TIMEOUT_SECONDS)
                .build();
        return httpPort.execute(request);
    }

    /** 惰性取 HTTP 端口，避免 provider 构造时就要求依赖齐备。 */
    public static RemoteHttpPort requireHttp(Supplier<RemoteHttpPort> supplier, String providerName) {
        RemoteHttpPort port = supplier.get();
        if (port == null) {
            throw new IllegalStateException(providerName + " 缺少 RemoteHttpPort");
        }
        return port;
    }

    /**
     * 从 OpenAI chat completion 响应里取 assistant 正文。
     * <p>
     * content 有四种形状：字符串、parts 数组（字符串或 {"text":...} 混合）、
     * 缺 message 时回退 choice.text、其它类型走 message 原值。任一环节缺失都返回空串，
     * 由调用方决定是否改用 citations。
     */
    public static String extractAssistantContent(JSONObject root) {
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
}
