package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamListener;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamSession;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactBinding;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.MultiModalAgentRequest;
import org.wwz.ai.domain.agent.runtime.dto.MultiModalAgentResponse;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.MultimodalAgentToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRefMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Data
public class MultiModalAgent implements BaseTool {

    /**
     * 多模态检索整体超时，避免上游流式接口异常时挂住整个 Agent 执行链。
     */
    private static final long MULTIMODAL_AGENT_TIMEOUT_MINUTES = 10L;

    /**
     * 多模态检索连接超时。
     */
    private static final long MULTIMODAL_AGENT_CONNECT_TIMEOUT_SECONDS = 30L;

    /**
     * 多模态检索读写超时。
     */
    private static final long MULTIMODAL_AGENT_IO_TIMEOUT_MINUTES = 10L;

    private AgentContext agentContext;

    /**
     * 当前正在执行的 HTTP 调用，用于超时后主动取消。
     */
    private volatile RemoteStreamSession activeStreamSession;

    @Override
    public String getName() {
        return "multimodalagent_tool";
    }

    @Override
    public String getDescription() {
        String defaultDesc = "本工具用于查询与用户相关的知识，作为在线知识的补充。支持文本和图像等多模态数据检索，能够高效访问和获取用户专属的知识信息。";
        ReactorConfig reactorConfig = requireReactorConfig();
        return StringUtils.hasText(reactorConfig.getMultiModalAgentDesc())
                ? reactorConfig.getMultiModalAgentDesc()
                : defaultDesc;
    }

    @Override
    public Map<String, Object> toParams() {
        ReactorConfig reactorConfig = requireReactorConfig();
        if (!reactorConfig.getMultiModalAgentParams().isEmpty()) {
            return reactorConfig.getMultiModalAgentParams();
        }

        Map<String, Object> questionParam = new HashMap<>();
        questionParam.put("type", "string");
        questionParam.put("description", "查询所需要的question，需要在知识库中进行检索的检索短语或句子。");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("question", questionParam);
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("question"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = (Map<String, Object>) input;
            String question = params.get("question") == null ? "" : String.valueOf(params.get("question")).trim();
            if (!StringUtils.hasText(question)) {
                return buildFailurePayload("multimodalagent_tool 执行失败：question 不能为空。");
            }

            ReactorConfig reactorConfig = requireReactorConfig();
            if (!StringUtils.hasText(reactorConfig.getMultiModalAgentUrl())) {
                return buildFailurePayload("multimodalagent_tool 执行失败：未配置 multimodalagent_url。");
            }

            Map<String, Object> streamMode = new HashMap<>();
            streamMode.put("mode", "token");
            streamMode.put("token", 10);

            MultiModalAgentRequest request = MultiModalAgentRequest.builder()
                    .requestId(agentContext.getSessionId())
                    .question(question)
                    .query(agentContext.getQuery())
                    .stream(true)
                    .contentStream(Boolean.TRUE.equals(agentContext.getIsStream()))
                    .streamMode(streamMode)
                    .build();
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());

            CompletableFuture<ToolResultPayload> future = callMultiModalAgentStream(request, artifactSource);
            return future.get(MULTIMODAL_AGENT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            if (activeStreamSession != null) {
                activeStreamSession.cancel();
            }
            log.error("{} multimodalagent_tool timeout after {} minutes",
                    agentContext.getRequestId(), MULTIMODAL_AGENT_TIMEOUT_MINUTES, e);
            return buildFailurePayload("multimodalagent_tool 执行失败：多模态检索超时，请稍后重试。");
        } catch (Exception e) {
            log.error("{} multimodalagent_tool error", agentContext.getRequestId(), e);
            return buildFailurePayload("multimodalagent_tool 执行失败：" + e.getMessage());
        } finally {
            activeStreamSession = null;
        }
    }

    public CompletableFuture<ToolResultPayload> callMultiModalAgentStream(MultiModalAgentRequest multiModalAgentRequest,
                                                                          ToolArtifactSource artifactSource) {
        CompletableFuture<ToolResultPayload> future = new CompletableFuture<>();
        try {
            ReactorConfig reactorConfig = requireReactorConfig();
            String url = reactorConfig.getMultiModalAgentUrl() + "/v1/tool/mragQuery";
            log.info("{} multimodalagent_tool request {}", agentContext.getRequestId(),
                    JSONObject.toJSONString(multiModalAgentRequest));

            String[] interval = reactorConfig.getMessageInterval().getOrDefault("knowledge", "1,4").split(",");
            int firstInterval = Integer.parseInt(interval[0]);
            int sendInterval = Integer.parseInt(interval[1]);
            activeStreamSession = requireRemoteStreamPort().openStream(RemoteStreamRequest.builder()
                    .method("POST")
                    .url(url)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(JSONObject.toJSONString(multiModalAgentRequest))
                    .connectTimeoutSeconds(MULTIMODAL_AGENT_CONNECT_TIMEOUT_SECONDS)
                    .readTimeoutSeconds(TimeUnit.MINUTES.toSeconds(MULTIMODAL_AGENT_IO_TIMEOUT_MINUTES))
                    .writeTimeoutSeconds(TimeUnit.MINUTES.toSeconds(MULTIMODAL_AGENT_IO_TIMEOUT_MINUTES))
                    .callTimeoutSeconds(TimeUnit.MINUTES.toSeconds(MULTIMODAL_AGENT_IO_TIMEOUT_MINUTES))
                    .build(), new RemoteStreamListener() {
                @Override
                public void onOpen() {
                    log.info("{} multimodalagent_tool stream opened", agentContext.getRequestId());
                }

                @Override
                public void onLine(String line) {
                    if (!line.startsWith("data:")) {
                        return;
                    }
                    streamState(artifactSource, firstInterval, sendInterval, future).consume(line.substring(5).trim());
                }

                @Override
                public void onClosed() {
                    streamState(artifactSource, firstInterval, sendInterval, future).complete();
                    activeStreamSession = null;
                }

                @Override
                public void onFailure(Throwable throwable, Integer statusCode, String responseBody) {
                    activeStreamSession = null;
                    log.error("{} multimodalagent_tool request error, code={}, body={}",
                            agentContext.getRequestId(), statusCode, responseBody, throwable);
                    if (!future.isDone()) {
                        if (statusCode != null) {
                            future.complete(buildFailurePayload("multimodalagent_tool 执行失败：上游服务返回异常状态 " + statusCode + "。"));
                        } else {
                            future.complete(buildFailurePayload("multimodalagent_tool 执行失败：" + throwable.getMessage()));
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("{} multimodalagent_tool request error", agentContext.getRequestId(), e);
            future.complete(buildFailurePayload("multimodalagent_tool 执行失败：" + e.getMessage()));
        }
        return future;
    }

    private StreamState streamState(ToolArtifactSource artifactSource,
                                    int firstInterval,
                                    int sendInterval,
                                    CompletableFuture<ToolResultPayload> future) {
        if (streamState == null) {
            streamState = new StreamState(artifactSource, firstInterval, sendInterval, future);
        }
        return streamState;
    }

    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
    private transient StreamState streamState;

    private class StreamState {
        private final ToolArtifactSource artifactSource;
        private final int firstInterval;
        private final int sendInterval;
        private final CompletableFuture<ToolResultPayload> future;
        private final String messageId = StringUtil.getUUID();
        private final String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
        private final StringBuilder incrementalBuffer = new StringBuilder();
        private final StringBuilder fullContent = new StringBuilder();
        private int chunkIndex = 1;
        private boolean finalSent;

        private StreamState(ToolArtifactSource artifactSource,
                            int firstInterval,
                            int sendInterval,
                            CompletableFuture<ToolResultPayload> future) {
            this.artifactSource = artifactSource;
            this.firstInterval = firstInterval;
            this.sendInterval = sendInterval;
            this.future = future;
        }

        private void consume(String data) {
            if (!StringUtils.hasText(data) || "[DONE]".equals(data) || data.startsWith("heartbeat")) {
                return;
            }

            MultiModalAgentResponse streamResponse;
            try {
                streamResponse = JSONObject.parseObject(data, MultiModalAgentResponse.class);
            } catch (Exception parseException) {
                log.warn("{} multimodalagent_tool parse chunk failed, raw={}",
                        agentContext.getRequestId(), data, parseException);
                return;
            }

            boolean finished = handleChunk(
                    streamResponse,
                    messageId,
                    digitalEmployee,
                    incrementalBuffer,
                    fullContent,
                    firstInterval,
                    sendInterval,
                    chunkIndex,
                    artifactSource
            );
            if (finished) {
                finalSent = true;
            }
            chunkIndex++;
        }

        private void complete() {
            if (!finalSent && fullContent.length() > 0) {
                emitFinalMarkdown(messageId, digitalEmployee, fullContent.toString(), artifactSource);
                finalSent = true;
            }
            if (future.isDone()) {
                return;
            }
            if (fullContent.length() == 0) {
                future.complete(buildFailurePayload("multimodalagent_tool 执行失败：上游未返回有效内容。"));
                return;
            }
            future.complete(buildSuccessPayload(fullContent.toString(), artifactSource));
        }
    }

    private boolean handleChunk(MultiModalAgentResponse streamResponse,
                                String messageId,
                                String digitalEmployee,
                                StringBuilder incrementalBuffer,
                                StringBuilder fullContent,
                                int firstInterval,
                                int sendInterval,
                                int chunkIndex,
                                ToolArtifactSource artifactSource) {
        if (streamResponse == null) {
            return false;
        }

        if (streamResponse.getChoices() != null && !streamResponse.getChoices().isEmpty()) {
            MultiModalAgentResponse.Choice choice = streamResponse.getChoices().get(0);
            MultiModalAgentResponse.Delta delta = choice.getDelta();
            String content = delta == null ? null : delta.getContent();
            appendContent(content, incrementalBuffer, fullContent);
            if (shouldEmitIncremental(chunkIndex, firstInterval, sendInterval)) {
                emitIncrementalKnowledge(messageId, digitalEmployee, incrementalBuffer);
            }
            if ("stop".equalsIgnoreCase(choice.getFinishReason())) {
                emitFinalMarkdown(messageId, digitalEmployee, fullContent.toString(), artifactSource);
                return true;
            }
            return false;
        }

        appendContent(streamResponse.getData(), incrementalBuffer, fullContent);
        if (Boolean.TRUE.equals(streamResponse.getIsFinal())) {
            emitFinalMarkdown(messageId, digitalEmployee, fullContent.toString(), artifactSource);
            return true;
        }
        if (shouldEmitIncremental(chunkIndex, firstInterval, sendInterval)) {
            emitIncrementalKnowledge(messageId, digitalEmployee, incrementalBuffer);
        }
        return false;
    }

    private void appendContent(String content, StringBuilder incrementalBuffer, StringBuilder fullContent) {
        if (content == null || content.isEmpty()) {
            return;
        }
        fullContent.append(content);
        if (Boolean.TRUE.equals(agentContext.getIsStream())) {
            incrementalBuffer.append(content);
        }
    }

    private boolean shouldEmitIncremental(int chunkIndex, int firstInterval, int sendInterval) {
        return Boolean.TRUE.equals(agentContext.getIsStream())
                && (chunkIndex == firstInterval || chunkIndex % sendInterval == 0);
    }

    private void emitIncrementalKnowledge(String messageId,
                                          String digitalEmployee,
                                          StringBuilder incrementalBuffer) {
        if (incrementalBuffer.length() == 0) {
            return;
        }
        MultiModalAgentResponse response = MultiModalAgentResponse.builder()
                .data(incrementalBuffer.toString())
                .isFinal(false)
                .build();
        attachToolCallId(response);
        agentContext.getPrinter().send(messageId, "knowledge", response, digitalEmployee, false);
        incrementalBuffer.setLength(0);
    }

    private void emitFinalMarkdown(String messageId,
                                   String digitalEmployee,
                                   String markdownContent,
                                   ToolArtifactSource artifactSource) {
        if (!StringUtils.hasText(markdownContent)) {
            return;
        }

        MultiModalAgentResponse response = MultiModalAgentResponse.builder()
                .data(markdownContent)
                .isFinal(true)
                .build();
        attachToolCallId(response);
        agentContext.getPrinter().send(messageId, "markdown", response, digitalEmployee, true);
        uploadMarkdownArtifact(markdownContent, artifactSource);
    }

    /**
     * 让知识检索阶段和最终 Markdown 结果都带上 toolCallId，
     * 前端才能把实时占位和真正结果准确折叠为同一张卡片。
     */
    private void attachToolCallId(MultiModalAgentResponse response) {
        ToolArtifactSource currentSource = agentContext.getCurrentToolArtifactSource();
        if (response == null || currentSource == null) {
            return;
        }
        response.setToolCallId(currentSource.getToolCallId());
    }

    private void uploadMarkdownArtifact(String markdownContent, ToolArtifactSource artifactSource) {
        FileTool fileTool = new FileTool();
        fileTool.setAgentContext(agentContext);

        String baseName = StringUtils.hasText(agentContext.getQuery())
                ? agentContext.getQuery()
                : "多模态检索结果";
        String fileName = StringUtil.removeSpecialChars(baseName + "的多模态检索结果.md");
        if (!StringUtils.hasText(fileName)) {
            fileName = "多模态检索结果.md";
        }

        String fileDesc = markdownContent.substring(0, Math.min(markdownContent.length(), 120));
        FileRequest fileRequest = FileRequest.builder()
                .requestId(agentContext.getRequestId())
                .fileName(fileName)
                .description(fileDesc)
                .content(markdownContent)
                .build();
        fileTool.uploadFile(fileRequest, false, false, artifactSource);
    }

    /**
     * 多模态检索成功后，同时保留 Markdown 正文和稳定文件引用，便于后续 replay 重建。
     */
    private ToolResultPayload buildSuccessPayload(String markdownContent, ToolArtifactSource artifactSource) {
        List<Map<String, Object>> fileInfo = buildFileInfo(artifactSource);
        MultimodalAgentToolOutput structuredOutput = MultimodalAgentToolOutput.builder()
                .summary(buildSummary(markdownContent))
                .markdownContent(markdownContent)
                .fileRefs(ToolFileRefMapper.fromGenericFileInfo(fileInfo))
                .build();
        return ToolResultPayload.structured(
                markdownContent,
                markdownContent,
                structuredOutput
        );
    }

    /**
     * 失败路径也返回最小 typed output，避免账本再次出现空结构。
     */
    private ToolResultPayload buildFailurePayload(String message) {
        return ToolResultPayload.failure(
                message,
                message,
                MultimodalAgentToolOutput.builder()
                        .summary(message)
                        .markdownContent("")
                        .build(),
                message
        );
    }

    /**
     * 生成面向账本的轻量摘要，避免 projector 必须重新解析完整 Markdown。
     */
    private String buildSummary(String markdownContent) {
        String normalized = markdownContent == null ? "" : markdownContent
                .replaceAll("!\\[[^\\]]*\\]\\([^\\)]*\\)", " ")
                .replaceAll("[#>*`]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }

    /**
     * 优先复用 artifact 账本中已经登记的稳定文件信息，不重复造一套文件来源。
     */
    private List<Map<String, Object>> buildFileInfo(ToolArtifactSource artifactSource) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (artifactSource == null || !StringUtils.hasText(artifactSource.getToolCallId())) {
            return result;
        }
        List<ToolArtifactBinding> bindings = agentContext.getArtifactBindingsByToolCallId(artifactSource.getToolCallId());
        if (bindings == null) {
            return result;
        }
        for (ToolArtifactBinding binding : bindings) {
            if (binding == null || binding.getFile() == null) {
                continue;
            }
            File file = binding.getFile();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileName", file.getFileName());
            item.put("ossUrl", file.getOssUrl());
            item.put("domainUrl", file.getDomainUrl());
            item.put("fileSize", file.getFileSize());
            result.add(item);
        }
        return result;
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("MultiModalAgent 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    private RemoteStreamPort requireRemoteStreamPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("MultiModalAgent 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireRemoteStreamPort();
    }
}
