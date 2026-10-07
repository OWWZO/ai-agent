package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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

/**
 * WebFetch：调用 Python 抓取正文 → 用 prompt 经小模型提炼。
 * 网页抓取和正文提取委托给 reactor-tool /v1/tool/web_fetch；本端只负责 prompt 提取。
 */
@Slf4j
@Data
public class WebFetchTool implements BaseTool {

    public static final String TOOL_NAME = "WebFetch";

    private static final int MAX_URL_LENGTH = 2000;
    private static final int MAX_MARKDOWN_LENGTH = 100_000;
    private static final long WEB_FETCH_SERVICE_TIMEOUT_SECONDS = 120L;
    private static final int EXTRACT_TIMEOUT_SECONDS = 90;

    private AgentContext agentContext;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return """
                IMPORTANT: WebFetch WILL FAIL for authenticated or private URLs. Prefer specialized MCP tools for GitHub/Confluence/etc.

                Fetches and extracts content from a URL through the reactor-tool Python service, then processes it with a prompt using a secondary model.
                - Inputs: url (required), prompt (required — what to extract/analyze)
                - Python handles redirects and HTML body extraction
                - The extracted page content is analyzed only against the provided prompt
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
        String rawUrl = "";
        String prompt = "";
        try {
            Map<String, Object> params = coerceMap(input);
            rawUrl = StringUtils.trimToEmpty(valueAsString(params.get("url")));
            prompt = StringUtils.trimToEmpty(valueAsString(params.get("prompt")));
            if (StringUtils.isBlank(rawUrl)) {
                return failure("WebFetch 失败：url 不能为空", rawUrl, prompt);
            }
            if (StringUtils.isBlank(prompt)) {
                return failure("WebFetch 失败：prompt 不能为空（需说明要从页面提取/分析什么）", rawUrl, prompt);
            }

            validateUrl(rawUrl);

            FetchedPage fetch = fetchFromPython(rawUrl);
            String markdown = truncateContent(fetch.content());
            ExtractOutcome extracted = applyPromptToContent(prompt, markdown);
            long durationMs = System.currentTimeMillis() - start;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tool", "web_fetch");
            data.put("ok", Boolean.TRUE);
            data.put("url", fetch.finalUrl());
            data.put("status", fetch.statusCode());
            if (StringUtils.isNotBlank(fetch.statusText())) {
                data.put("statusText", fetch.statusText());
            }
            data.put("bytes", fetch.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            data.put("durationMs", durationMs);
            data.put("prompt", prompt);
            data.put("contentFormat", fetch.contentFormat());
            data.put("contentSource", fetch.contentSource());
            data.put("wordCount", fetch.wordCount());
            if (fetch.metadata() != null && !fetch.metadata().isEmpty()) {
                data.put("metadata", fetch.metadata());
            }
            data.put("content", extracted.content());
            data.put("degraded", extracted.degraded());
            if (extracted.degraded()) {
                data.put("extractError", extracted.errorSummary());
                data.put("hint", "Page fetch succeeded; model extract failed, returned truncated page text.");
            }
            return ToolResultPayload.fromData(data);
        } catch (FetchHttpException e) {
            log.warn("{} WebFetch remote response failed, url={}, status={}", requestId(), e.url, e.statusCode);
            Map<String, Object> detail = failureDetails(rawUrl, prompt);
            detail.put("status", e.statusCode);
            detail.put("statusText", StringUtils.defaultString(e.statusText));
            detail.put("responseBody", e.responseBody);
            return ToolResultPayload.failureFrom("WebFetch 失败：" + e.getMessage(), detail);
        } catch (Exception e) {
            log.error("{} WebFetch execute error, input={}", requestId(), input, e);
            return failure("WebFetch 失败：" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()),
                    rawUrl, prompt);
        }
    }

    private FetchedPage fetchFromPython(String url) throws Exception {
        ReactorConfig config = requireReactorConfig();
        String endpoint = buildWebFetchEndpoint(config.getWebFetchUrl());
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("requestId", StringUtils.defaultIfBlank(requestId(), "web-fetch"));
        requestBody.put("url", url);
        requestBody.put("timeoutSeconds", WEB_FETCH_SERVICE_TIMEOUT_SECONDS);

        RemoteHttpResponse response = requireRemoteHttpPort().executeDetailed(RemoteHttpRequest.builder()
                .method("POST")
                .url(endpoint)
                .headers(Map.of("Accept", "application/json", "Content-Type", "application/json"))
                .body(JSON.toJSONString(requestBody))
                .connectTimeoutSeconds(30L)
                .readTimeoutSeconds(WEB_FETCH_SERVICE_TIMEOUT_SECONDS)
                .writeTimeoutSeconds(30L)
                .callTimeoutSeconds(WEB_FETCH_SERVICE_TIMEOUT_SECONDS)
                .build());

        int statusCode = response.getStatusCode();
        String responseBody = StringUtils.defaultString(response.getBody());
        if (statusCode < 200 || statusCode >= 300) {
            throw new FetchHttpException(
                    statusCode,
                    url,
                    response.getStatusText(),
                    StringUtils.abbreviate(responseBody, 2000)
            );
        }

        JSONObject root;
        try {
            root = JSON.parseObject(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid web_fetch response from reactor-tool", e);
        }
        if (root == null) {
            throw new IllegalStateException("Empty web_fetch response from reactor-tool");
        }
        Integer resultCode = root.getInteger("code");
        if (resultCode != null && resultCode != 200) {
            throw new FetchHttpException(
                    resultCode,
                    url,
                    root.getString("message"),
                    StringUtils.abbreviate(responseBody, 2000)
            );
        }

        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            throw new IllegalStateException("web_fetch response does not contain data");
        }
        String content = StringUtils.trimToEmpty(data.getString("content"));
        if (content.isBlank()) {
            throw new IllegalStateException("web_fetch response content is empty");
        }
        Integer fetchedStatusCode = data.getInteger("statusCode");
        Integer wordCount = data.getInteger("wordCount");
        return new FetchedPage(
                StringUtils.defaultIfBlank(data.getString("finalUrl"), url),
                fetchedStatusCode == null ? 200 : fetchedStatusCode,
                StringUtils.defaultString(data.getString("statusText")),
                content,
                StringUtils.defaultString(data.getString("contentFormat")),
                StringUtils.defaultString(data.getString("contentSource")),
                wordCount == null ? 0 : wordCount,
                data.getJSONObject("metadata")
        );
    }

    private static String buildWebFetchEndpoint(String configuredUrl) {
        String base = StringUtils.removeEnd(StringUtils.trimToEmpty(configuredUrl), "/");
        if (base.isBlank()) {
            throw new IllegalStateException("autobots.autoagent.web_fetch_url is not configured");
        }
        if (base.endsWith("/v1/tool/web_fetch")) {
            return base;
        }
        return base + "/v1/tool/web_fetch";
    }

    private ExtractOutcome applyPromptToContent(String prompt, String markdownContent) {
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

        try {
            ReactorConfig config = requireReactorConfig();
            String modelName = StringUtils.defaultIfBlank(config.getSummaryModelName(), config.getReactModelName());
            LLM llm = new LLM(modelName, "", agentContext.getRuntimeDependencies());
            // Prefer stream aggregation: some OpenAI-compatible gateways return empty/truncated
            // non-stream JSON bodies that Spring AI cannot deserialize as ChatCompletion.
            String answer = llm.ask(
                    agentContext,
                    Collections.singletonList(Message.userMessage(modelPrompt, null)),
                    Collections.emptyList(),
                    true,
                    false,
                    0.0
            ).get(EXTRACT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return ExtractOutcome.success(StringUtils.defaultIfBlank(answer, "No response from model"));
        } catch (Exception e) {
            String root = rootCauseSummary(e);
            log.warn("{} WebFetch model extract failed, degrade to page text, root={}", requestId(), root, e);
            String degraded = """
                    [WebFetch degraded: model extract failed]
                    error: %s
                    prompt: %s

                    page text:
                    %s
                    """.formatted(root, prompt, markdownContent);
            return ExtractOutcome.degraded(degraded, root);
        }
    }

    private static String rootCauseSummary(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = StringUtils.defaultIfBlank(cursor.getMessage(), cursor.getClass().getSimpleName());
        return cursor.getClass().getSimpleName() + ": " + message;
    }

    private record ExtractOutcome(String content, boolean degraded, String errorSummary) {
        private static ExtractOutcome success(String content) {
            return new ExtractOutcome(content, false, null);
        }

        private static ExtractOutcome degraded(String content, String errorSummary) {
            return new ExtractOutcome(content, true, errorSummary);
        }
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

    private ToolResultPayload failure(String message, String url, String prompt) {
        return ToolResultPayload.failureFrom(message, failureDetails(url, prompt));
    }

    private Map<String, Object> failureDetails(String url, String prompt) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", "tool_error");
        detail.put("tool", "web_fetch");
        if (StringUtils.isNotBlank(url)) {
            detail.put("url", url);
        }
        if (StringUtils.isNotBlank(prompt)) {
            detail.put("prompt", prompt);
        }
        return detail;
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

    private static final class FetchHttpException extends IllegalStateException {
        private final int statusCode;
        private final String url;
        private final String statusText;
        private final String responseBody;

        private FetchHttpException(int statusCode,
                                   String url,
                                   String statusText,
                                   String responseBody) {
            super("HTTP " + statusCode + " for " + url + ": " + responseBody);
            this.statusCode = statusCode;
            this.url = url;
            this.statusText = statusText;
            this.responseBody = responseBody;
        }
    }

    private record FetchedPage(
            String finalUrl,
            int statusCode,
            String statusText,
            String content,
            String contentFormat,
            String contentSource,
            int wordCount,
            JSONObject metadata
    ) {
    }
}
