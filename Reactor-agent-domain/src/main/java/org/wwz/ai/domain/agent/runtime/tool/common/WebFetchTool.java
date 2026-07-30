package org.wwz.ai.domain.agent.runtime.tool.common;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpResponse;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * WebFetch（对标 cc-haha）：抓取 URL → HTML 转文本 → 用 prompt 经小模型提炼。
 * 不再依赖 reactor-tool /web_fetch 与文件产物。
 */
@Slf4j
@Data
public class WebFetchTool implements BaseTool {

    public static final String TOOL_NAME = "WebFetch";

    private static final int MAX_URL_LENGTH = 2000;
    private static final int MAX_MARKDOWN_LENGTH = 100_000;
    private static final int MAX_SAME_HOST_REDIRECTS = 10;
    private static final long FETCH_TIMEOUT_SECONDS = 60L;
    private static final int EXTRACT_TIMEOUT_SECONDS = 90;
    private static final String USER_AGENT = "ReactorAgentWebFetch/1.0";
    private static final Pattern SCRIPT_STYLE = Pattern.compile(
            "(?is)<(script|style|noscript|svg|iframe)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern MULTI_NL = Pattern.compile("\\n{3,}");

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return """
                IMPORTANT: WebFetch WILL FAIL for authenticated or private URLs. Prefer specialized MCP tools for GitHub/Confluence/etc.

                Fetches content from a URL and processes it with a prompt using a secondary model.
                - Inputs: url (required), prompt (required — what to extract/analyze)
                - HTTP is upgraded to HTTPS
                - HTML is converted to plain text/markdown-like content
                - Cross-host redirects are NOT followed automatically; the tool returns redirect info for a new call
                - Results may be summarized if the page is very large
                - Read-only; does not write files
                """;
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> url = new LinkedHashMap<>();
        url.put("type", "string");
        url.put("description", "The URL to fetch content from (http/https)");

        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("type", "string");
        prompt.put("description", "The prompt to run on the fetched content (what to extract or analyze)");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", url);
        properties.put("prompt", prompt);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("url", "prompt"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> params = coerceMap(input);
            String rawUrl = StringUtils.trimToEmpty(valueAsString(params.get("url")));
            String prompt = StringUtils.trimToEmpty(valueAsString(params.get("prompt")));
            if (StringUtils.isBlank(rawUrl)) {
                return failure("WebFetch 失败：url 不能为空");
            }
            if (StringUtils.isBlank(prompt)) {
                return failure("WebFetch 失败：prompt 不能为空（需说明要从页面提取/分析什么）");
            }

            String upgradedUrl = upgradeToHttps(rawUrl);
            validateUrl(upgradedUrl);

            FetchResult fetch = fetchWithPermittedRedirects(upgradedUrl, 0);
            if (fetch.redirect()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("tool", "web_fetch");
                data.put("ok", Boolean.FALSE);
                data.put("redirect", Boolean.TRUE);
                data.put("url", fetch.originalUrl());
                data.put("redirectUrl", fetch.redirectUrl());
                data.put("status", fetch.statusCode());
                data.put("hint", "Call web_fetch again with redirectUrl and the same prompt.");
                data.put("prompt", prompt);
                return ToolResultPayload.fromData(data);
            }

            String markdown = truncateContent(fetch.content());
            String extracted = applyPromptToContent(prompt, markdown);
            long durationMs = System.currentTimeMillis() - start;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tool", "web_fetch");
            data.put("ok", Boolean.TRUE);
            data.put("url", fetch.finalUrl());
            data.put("status", fetch.statusCode());
            data.put("statusText", StringUtils.defaultString(fetch.statusText()));
            data.put("bytes", fetch.bytes());
            data.put("durationMs", durationMs);
            data.put("prompt", prompt);
            data.put("content", extracted);
            return ToolResultPayload.fromData(data);
        } catch (Exception e) {
            log.error("{} WebFetch execute error, input={}", requestId(), input, e);
            return failure("WebFetch 失败：" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private FetchResult fetchWithPermittedRedirects(String url, int depth) throws Exception {
        if (depth > MAX_SAME_HOST_REDIRECTS) {
            throw new IllegalStateException("Too many redirects (exceeded " + MAX_SAME_HOST_REDIRECTS + ")");
        }

        RemoteHttpResponse response = requireRemoteHttpPort().executeDetailed(RemoteHttpRequest.builder()
                .method("GET")
                .url(url)
                .headers(Map.of(
                        "Accept", "text/markdown, text/html, text/plain, */*",
                        "User-Agent", USER_AGENT
                ))
                .connectTimeoutSeconds(30L)
                .readTimeoutSeconds(FETCH_TIMEOUT_SECONDS)
                .writeTimeoutSeconds(FETCH_TIMEOUT_SECONDS)
                .callTimeoutSeconds(FETCH_TIMEOUT_SECONDS)
                .followRedirects(false)
                .build());

        int code = response.getStatusCode();
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            String location = headerIgnoreCase(response.getHeaders(), "Location");
            if (StringUtils.isBlank(location)) {
                throw new IllegalStateException("Redirect missing Location header");
            }
            String redirectUrl = URI.create(url).resolve(location.trim()).toString();
            redirectUrl = upgradeToHttps(redirectUrl);
            if (isPermittedRedirect(url, redirectUrl)) {
                return fetchWithPermittedRedirects(redirectUrl, depth + 1);
            }
            return FetchResult.redirect(url, redirectUrl, code);
        }

        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + " for " + url
                    + ": " + StringUtils.abbreviate(StringUtils.defaultString(response.getBody()), 200));
        }

        String contentType = StringUtils.defaultString(headerIgnoreCase(response.getHeaders(), "Content-Type")).toLowerCase(Locale.ROOT);
        String body = StringUtils.defaultString(response.getBody());
        String content;
        if (contentType.contains("text/html") || looksLikeHtml(body)) {
            content = htmlToText(body);
        } else {
            content = body;
        }
        if (StringUtils.isBlank(content)) {
            throw new IllegalStateException("Empty content from " + url);
        }
        String finalUrl = StringUtils.defaultIfBlank(response.getFinalUrl(), url);
        return FetchResult.content(finalUrl, code, response.getStatusText(), content, content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    private String applyPromptToContent(String prompt, String markdownContent) throws Exception {
        String modelPrompt = """
                Web page content:
                ---
                %s
                ---

                %s

                Provide a concise response based only on the content above.
                - Prefer short quotes; do not dump the entire page
                - Use quotation marks for exact language from the source
                """.formatted(markdownContent, prompt);

        ReactorConfig config = requireReactorConfig();
        String modelName = StringUtils.defaultIfBlank(config.getSummaryModelName(), config.getReactModelName());
        LLM llm = new LLM(modelName, "", agentContext.getRuntimeDependencies());
        String answer = llm.ask(
                agentContext,
                Collections.singletonList(Message.userMessage(modelPrompt, null)),
                Collections.emptyList(),
                false,
                false,
                0.0
        ).get(EXTRACT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return StringUtils.defaultIfBlank(answer, "No response from model");
    }

    private static String htmlToText(String html) {
        String cleaned = SCRIPT_STYLE.matcher(html).replaceAll(" ");
        cleaned = cleaned.replaceAll("(?i)<br\\s*/?>", "\n");
        cleaned = cleaned.replaceAll("(?i)</p>", "\n\n");
        cleaned = cleaned.replaceAll("(?i)</div>", "\n");
        cleaned = cleaned.replaceAll("(?i)</h[1-6]>", "\n\n");
        cleaned = cleaned.replaceAll("(?i)</li>", "\n");
        cleaned = cleaned.replaceAll("(?i)<li[^>]*>", "- ");
        cleaned = TAG.matcher(cleaned).replaceAll(" ");
        cleaned = decodeBasicEntities(cleaned);
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ");
        cleaned = MULTI_NL.matcher(cleaned).replaceAll("\n\n");
        return cleaned.trim();
    }

    private static String decodeBasicEntities(String text) {
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    private static boolean looksLikeHtml(String body) {
        String sample = body.length() > 500 ? body.substring(0, 500).toLowerCase(Locale.ROOT) : body.toLowerCase(Locale.ROOT);
        return sample.contains("<html") || sample.contains("<body") || sample.contains("<div") || sample.contains("<p");
    }

    private static String truncateContent(String content) {
        if (content == null || content.length() <= MAX_MARKDOWN_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_MARKDOWN_LENGTH) + "\n\n[Content truncated due to length...]";
    }

    private static void validateUrl(String url) {
        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("URL too long (max " + MAX_URL_LENGTH + ")");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        String scheme = StringUtils.defaultString(uri.getScheme()).toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Only http/https URLs are supported");
        }
        if (StringUtils.isNotBlank(uri.getUserInfo())) {
            throw new IllegalArgumentException("URL must not contain username/password");
        }
        String host = uri.getHost();
        if (StringUtils.isBlank(host)) {
            throw new IllegalArgumentException("URL hostname is required");
        }
        // 允许 localhost / 裸主机名用于内网与测试；公网域名仍应含点
        if (!"localhost".equalsIgnoreCase(host) && !host.contains(".")) {
            throw new IllegalArgumentException("URL hostname is not publicly resolvable style: " + host);
        }
    }

    private static String upgradeToHttps(String url) {
        if (url != null && url.regionMatches(true, 0, "http://", 0, 7)) {
            return "https://" + url.substring(7);
        }
        return url;
    }

    /**
     * 仅允许同主机（含 www. 增删）的 redirect；跨域返回给模型再调。
     */
    static boolean isPermittedRedirect(String originalUrl, String redirectUrl) {
        try {
            URI original = URI.create(originalUrl);
            URI redirect = URI.create(redirectUrl);
            if (!StringUtils.equalsIgnoreCase(original.getScheme(), redirect.getScheme())) {
                return false;
            }
            int originalPort = original.getPort();
            int redirectPort = redirect.getPort();
            if (originalPort != redirectPort) {
                return false;
            }
            if (StringUtils.isNotBlank(redirect.getUserInfo())) {
                return false;
            }
            String o = stripWww(StringUtils.defaultString(original.getHost()).toLowerCase(Locale.ROOT));
            String r = stripWww(StringUtils.defaultString(redirect.getHost()).toLowerCase(Locale.ROOT));
            return o.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    private static String stripWww(String hostname) {
        return hostname.startsWith("www.") ? hostname.substring(4) : hostname;
    }

    private static String headerIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
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

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String requestId() {
        return agentContext == null ? "unknown" : StringUtils.defaultString(agentContext.getRequestId(), "unknown");
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("WebFetchTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    private RemoteHttpPort requireRemoteHttpPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("WebFetchTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireRemoteHttpPort();
    }

    private record FetchResult(
            boolean redirect,
            String originalUrl,
            String redirectUrl,
            String finalUrl,
            int statusCode,
            String statusText,
            String content,
            int bytes
    ) {
        static FetchResult redirect(String original, String redirect, int code) {
            return new FetchResult(true, original, redirect, null, code, null, null, 0);
        }

        static FetchResult content(String finalUrl, int code, String statusText, String content, int bytes) {
            return new FetchResult(false, null, null, finalUrl, code, statusText, content, bytes);
        }
    }
}
