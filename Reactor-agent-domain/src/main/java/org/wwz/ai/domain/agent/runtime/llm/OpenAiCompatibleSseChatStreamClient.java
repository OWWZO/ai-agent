package org.wwz.ai.domain.agent.runtime.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamListener;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI 兼容 chat/completions SSE 直解客户端。
 * <p>
 * 旁路 Spring AI {@code OpenAiApi.chatCompletionStream} 的 tool-call window/reduce 合并，
 * 逐帧把 {@code delta.tool_calls / content / reasoning_content} 映射为 {@link ChatResponse}，
 * 供既有 {@link StreamResponseHandler} 消费。
 * <p>
 * 供应商配置仍复用 {@link LLMSettings} + {@link OpenAiChatOptionsFactory}；
 * HTTP 传输走 {@link RemoteStreamPort}，不在 domain 直接 new OkHttp。
 */
@Slf4j
@Component
public class OpenAiCompatibleSseChatStreamClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiCompatibleChatCompletionRequestFactory requestFactory;

    public OpenAiCompatibleSseChatStreamClient(OpenAiCompatibleChatCompletionRequestFactory requestFactory) {
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory must not be null");
    }

    /**
     * 打开一次流式 chat/completions，返回不合并 tool-call 的 ChatResponse Flux。
     */
    public Flux<ChatResponse> stream(RemoteStreamPort remoteStreamPort,
                                     LLMSettings settings,
                                     Prompt prompt,
                                     OpenAiChatOptions options,
                                     long readTimeoutSeconds) {
        Objects.requireNonNull(remoteStreamPort, "remoteStreamPort must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(options, "options must not be null");

        String body = requestFactory.buildStreamBody(prompt, options);
        String url = resolveCompletionsUrl(settings);
        int toolCount = options.getToolCallbacks() == null ? 0 : options.getToolCallbacks().size();
        log.info("[tool-stream-diag] SSE open: url={}, model={}, bodyChars={}, tools={}, timeoutSec={}",
                url,
                options.getModel(),
                body == null ? 0 : body.length(),
                toolCount,
                readTimeoutSeconds);
        // 采样 body 是否含 tools（不打印完整 body，避免密钥/长 prompt）
        log.info("[tool-stream-diag] SSE body markers: hasToolsJson={}, hasStreamTrue={}, hasToolChoice={}",
                body != null && body.contains("\"tools\""),
                body != null && body.contains("\"stream\":true"),
                body != null && body.contains("\"tool_choice\""));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "text/event-stream");
        if (StringUtils.isNotBlank(settings.getApiKey())) {
            headers.put("Authorization", "Bearer " + settings.getApiKey().trim());
        }
        Map<String, String> optionHeaders = options.getHttpHeaders();
        if (optionHeaders != null && !optionHeaders.isEmpty()) {
            optionHeaders.forEach((k, v) -> {
                if (StringUtils.isNotBlank(k) && v != null) {
                    headers.put(k, v);
                }
            });
        }

        long timeout = readTimeoutSeconds > 0 ? readTimeoutSeconds : 300L;
        RemoteStreamRequest request = RemoteStreamRequest.builder()
                .method("POST")
                .url(url)
                .headers(headers)
                .body(body)
                .connectTimeoutSeconds(30L)
                .readTimeoutSeconds(timeout)
                .writeTimeoutSeconds(timeout)
                .callTimeoutSeconds(timeout + 30L)
                .build();

        return Flux.create(sink -> {
            RemoteStreamSession[] sessionHolder = new RemoteStreamSession[1];
            StringBuilder dataBuffer = new StringBuilder();
            // 诊断计数：确认上游是否真的多帧推送 tool_calls arguments
            int[] lineCount = new int[]{0};
            int[] dataEventCount = new int[]{0};
            int[] chatResponseCount = new int[]{0};
            int[] toolCallFrameCount = new int[]{0};
            int[] toolArgCharTotal = new int[]{0};
            try {
                sessionHolder[0] = remoteStreamPort.openStream(request, new RemoteStreamListener() {
                    @Override
                    public void onLine(String line) {
                        if (sink.isCancelled()) {
                            cancelQuietly(sessionHolder[0]);
                            return;
                        }
                        if (line == null) {
                            return;
                        }
                        lineCount[0]++;
                        if (lineCount[0] <= 5 || lineCount[0] % 50 == 0) {
                            log.info("[tool-stream-diag] SSE onLine#{} len={} prefix={}",
                                    lineCount[0],
                                    line.length(),
                                    StringUtils.left(line.trim(), 120));
                        }
                        String trimmed = line.trim();
                        // SSE 空行分隔事件；也可能是纯 data 行连续推
                        if (trimmed.isEmpty()) {
                            flushDataEvent(dataBuffer, sink, dataEventCount, chatResponseCount,
                                    toolCallFrameCount, toolArgCharTotal);
                            return;
                        }
                        if (trimmed.startsWith(":") || trimmed.startsWith("event:") || trimmed.startsWith("id:")) {
                            return;
                        }
                        if (trimmed.startsWith("data:")) {
                            String payload = trimmed.substring(5).trim();
                            if (dataBuffer.length() > 0) {
                                dataBuffer.append('\n');
                            }
                            dataBuffer.append(payload);
                            // 兼容网关把整事件塞进一行、且不发空行分隔的情况
                            if (isCompleteSsePayload(payload)) {
                                flushDataEvent(dataBuffer, sink, dataEventCount, chatResponseCount,
                                        toolCallFrameCount, toolArgCharTotal);
                            }
                            return;
                        }
                        // 非标准：整行 JSON
                        if (trimmed.startsWith("{")) {
                            if (dataBuffer.length() > 0) {
                                dataBuffer.append('\n');
                            }
                            dataBuffer.append(trimmed);
                            flushDataEvent(dataBuffer, sink, dataEventCount, chatResponseCount,
                                    toolCallFrameCount, toolArgCharTotal);
                        }
                    }

                    @Override
                    public void onClosed() {
                        flushDataEvent(dataBuffer, sink, dataEventCount, chatResponseCount,
                                toolCallFrameCount, toolArgCharTotal);
                        log.info("[tool-stream-diag] SSE onClosed: lines={}, dataEvents={}, chatResponses={}, "
                                        + "toolCallFrames={}, toolArgCharsEmitted={}",
                                lineCount[0], dataEventCount[0], chatResponseCount[0],
                                toolCallFrameCount[0], toolArgCharTotal[0]);
                        if (!sink.isCancelled()) {
                            sink.complete();
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable, Integer statusCode, String responseBody) {
                        if (sink.isCancelled()) {
                            return;
                        }
                        log.warn("[tool-stream-diag] SSE onFailure status={} bodyPrefix={} err={}",
                                statusCode,
                                StringUtils.left(responseBody, 200),
                                throwable == null ? null : throwable.getMessage());
                        String detail = throwable == null ? "unknown stream failure" : throwable.getMessage();
                        if (statusCode != null) {
                            detail = "HTTP " + statusCode + ": " + detail;
                        }
                        if (StringUtils.isNotBlank(responseBody)) {
                            detail = detail + " body=" + StringUtils.left(responseBody, 500);
                        }
                        sink.error(new IllegalStateException(detail, throwable));
                    }
                });
                log.info("[tool-stream-diag] SSE session opened ok");
            } catch (Exception e) {
                log.error("[tool-stream-diag] SSE openStream threw", e);
                sink.error(e);
                return;
            }
            sink.onCancel(() -> cancelQuietly(sessionHolder[0]));
            sink.onDispose(() -> cancelQuietly(sessionHolder[0]));
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private boolean isCompleteSsePayload(String payload) {
        if ("[DONE]".equalsIgnoreCase(payload)) {
            return true;
        }
        String t = payload.trim();
        return t.startsWith("{") && t.endsWith("}");
    }

    private void flushDataEvent(StringBuilder dataBuffer, FluxSink<ChatResponse> sink) {
        flushDataEvent(dataBuffer, sink, null, null, null, null);
    }

    private void flushDataEvent(StringBuilder dataBuffer,
                                FluxSink<ChatResponse> sink,
                                int[] dataEventCount,
                                int[] chatResponseCount,
                                int[] toolCallFrameCount,
                                int[] toolArgCharTotal) {
        if (dataBuffer.length() == 0 || sink.isCancelled()) {
            dataBuffer.setLength(0);
            return;
        }
        String payload = dataBuffer.toString().trim();
        dataBuffer.setLength(0);
        if (payload.isEmpty()) {
            return;
        }
        if (dataEventCount != null) {
            dataEventCount[0]++;
        }
        if ("[DONE]".equalsIgnoreCase(payload)) {
            log.info("[tool-stream-diag] SSE [DONE] received");
            sink.complete();
            return;
        }
        try {
            // 前几帧 + 含 tool_calls 的帧打样，确认上游是否碎片段 arguments
            boolean looksTool = payload.contains("tool_calls");
            int eventNo = dataEventCount == null ? -1 : dataEventCount[0];
            if (eventNo > 0 && (eventNo <= 8 || looksTool)) {
                log.info("[tool-stream-diag] SSE dataEvent#{} looksTool={} payloadSample={}",
                        eventNo, looksTool, StringUtils.left(payload, 280));
            }
            ChatResponse response = parseChunk(payload);
            if (response != null) {
                if (chatResponseCount != null) {
                    chatResponseCount[0]++;
                }
                AssistantMessage out = response.getResult() == null ? null : response.getResult().getOutput();
                List<AssistantMessage.ToolCall> tcs = out == null ? null : out.getToolCalls();
                if (tcs != null && !tcs.isEmpty()) {
                    if (toolCallFrameCount != null) {
                        toolCallFrameCount[0]++;
                    }
                    for (AssistantMessage.ToolCall tc : tcs) {
                        int argLen = tc.arguments() == null ? 0 : tc.arguments().length();
                        if (toolArgCharTotal != null) {
                            toolArgCharTotal[0] += argLen;
                        }
                        log.info("[tool-stream-diag] parsed tool_call delta: id='{}' name='{}' argLen={} argSample='{}'",
                                tc.id(),
                                tc.name(),
                                argLen,
                                StringUtils.left(StringUtils.defaultString(tc.arguments()), 80));
                    }
                }
                sink.next(response);
            }
        } catch (Exception e) {
            sink.error(new IllegalStateException("Failed to parse OpenAI SSE chunk: " + StringUtils.left(payload, 300), e));
        }
    }

    /**
     * 解析单帧 OpenAI chat.completion.chunk JSON → ChatResponse（不做 tool 合并）。
     */
    public ChatResponse parseChunk(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode choices = root.get("choices");
        JsonNode usageNode = root.get("usage");
        if ((choices == null || !choices.isArray() || choices.isEmpty()) && usageNode == null) {
            return null;
        }

        String finishReason = null;
        String content = null;
        String reasoning = null;
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            if (choice != null) {
                finishReason = textOrNull(choice.get("finish_reason"));
                JsonNode delta = choice.get("delta");
                if (delta == null || delta.isNull()) {
                    // 少数网关用 message 而非 delta
                    delta = choice.get("message");
                }
                if (delta != null && !delta.isNull()) {
                    content = extractTextContent(delta.get("content"));
                    reasoning = firstNonBlank(
                            textOrNull(delta.get("reasoning_content")),
                            textOrNull(delta.get("reasoning")),
                            textOrNull(delta.get("thinking")));
                    toolCalls = parseToolCalls(delta.get("tool_calls"));
                }
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        if (StringUtils.isNotEmpty(reasoning)) {
            properties.put(ReasoningContentExtractor.METADATA_REASONING_CONTENT, reasoning);
        }
        AssistantMessage.Builder messageBuilder = AssistantMessage.builder()
                .content(content == null ? "" : content)
                .properties(properties);
        if (!toolCalls.isEmpty()) {
            messageBuilder.toolCalls(toolCalls);
        }
        AssistantMessage assistantMessage = messageBuilder.build();

        Generation generation;
        if (StringUtils.isNotBlank(finishReason)) {
            generation = new Generation(assistantMessage, ChatGenerationMetadata.builder()
                    .finishReason(finishReason)
                    .build());
        } else {
            generation = new Generation(assistantMessage);
        }

        if (usageNode != null && !usageNode.isNull()) {
            Integer promptTokens = intOrNull(usageNode.get("prompt_tokens"));
            Integer completionTokens = intOrNull(usageNode.get("completion_tokens"));
            Integer totalTokens = intOrNull(usageNode.get("total_tokens"));
            if (totalTokens == null && promptTokens != null && completionTokens != null) {
                totalTokens = promptTokens + completionTokens;
            }
            DefaultUsage usage = new DefaultUsage(
                    promptTokens == null ? 0 : promptTokens,
                    completionTokens == null ? 0 : completionTokens,
                    totalTokens == null ? 0 : totalTokens);
            ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder()
                    .usage(usage);
            // 把原生 usage 节点挂到 metadata，供 LlmUsageSnapshot 细字段解析
            try {
                metadataBuilder.keyValue("usage", objectMapper.convertValue(usageNode, Object.class));
            } catch (Exception ignored) {
            }
            return new ChatResponse(List.of(generation), metadataBuilder.build());
        }
        return new ChatResponse(List.of(generation));
    }

    private List<AssistantMessage.ToolCall> parseToolCalls(JsonNode toolCallsNode) {
        List<AssistantMessage.ToolCall> result = new ArrayList<>();
        if (toolCallsNode == null || !toolCallsNode.isArray()) {
            return result;
        }
        for (JsonNode item : toolCallsNode) {
            if (item == null || item.isNull()) {
                continue;
            }
            String id = textOrNull(item.get("id"));
            String type = StringUtils.defaultIfBlank(textOrNull(item.get("type")), "function");
            JsonNode function = item.get("function");
            String name = null;
            String arguments = null;
            if (function != null && !function.isNull()) {
                name = textOrNull(function.get("name"));
                arguments = textOrNull(function.get("arguments"));
            }
            // 空碎片跳过（仅 index 无内容）
            if (StringUtils.isAllBlank(id, name, arguments)) {
                continue;
            }
            result.add(new AssistantMessage.ToolCall(
                    StringUtils.defaultString(id),
                    type,
                    StringUtils.defaultString(name),
                    StringUtils.defaultString(arguments)));
        }
        return result;
    }

    private String extractTextContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : contentNode) {
                if (part == null || part.isNull()) {
                    continue;
                }
                if (part.isTextual()) {
                    sb.append(part.asText());
                    continue;
                }
                String type = textOrNull(part.get("type"));
                if ("text".equals(type) || type == null) {
                    String text = textOrNull(part.get("text"));
                    if (text != null) {
                        sb.append(text);
                    }
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        return contentNode.asText(null);
    }

    private String resolveCompletionsUrl(LLMSettings settings) {
        String baseUrl = StringUtils.trimToEmpty(settings.getBaseUrl());
        if (StringUtils.isBlank(baseUrl)) {
            throw new IllegalArgumentException("Base URL is not configured for model: " + settings.getModel());
        }
        String path = StringUtils.isNotBlank(settings.getInterfaceUrl())
                ? settings.getInterfaceUrl().trim()
                : "/v1/chat/completions";
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String normalizedBase = StringUtils.removeEnd(baseUrl, "/");
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        // 兼容 baseUrl 已含 /v1 且 path 也以 /v1 开头
        if (normalizedBase.toLowerCase(Locale.ROOT).endsWith("/v1")
                && normalizedPath.toLowerCase(Locale.ROOT).startsWith("/v1/")) {
            normalizedPath = normalizedPath.substring(3);
        }
        return normalizedBase + normalizedPath;
    }

    private void cancelQuietly(RemoteStreamSession session) {
        if (session == null) {
            return;
        }
        try {
            session.cancel();
        } catch (Exception ignored) {
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return node.toString();
    }

    private static Integer intOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.intValue();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return null;
    }
}
