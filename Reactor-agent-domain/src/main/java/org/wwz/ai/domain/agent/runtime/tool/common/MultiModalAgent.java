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
import org.wwz.ai.domain.agent.runtime.tool.ContextIsolatableTool;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * 多模态 Agent 工具。
 * <p>
 * 通过远端流式接口完成图片/文件理解，保留阶段性文本、最终文件和取消句柄，
 * 并把生成产物绑定到当前 tool call。
 */
@Slf4j
@Data
public class MultiModalAgent implements ContextIsolatableTool {

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

    @Override
    public BaseTool isolateFor(AgentContext context) {
        if (context == null) {
            throw new IllegalArgumentException("MultiModalAgent.isolateFor context 不能为空");
        }
        MultiModalAgent copy = new MultiModalAgent();
        copy.setAgentContext(context);
        return copy;
    }

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
        // execute 建立一次独立的远端流会话，并把取消句柄保存在局部引用中；
        // 超时只取消本次 session，不影响同一工具实例在其他上下文中的调用。
        final AtomicReference<RemoteStreamSession> streamSession = new AtomicReference<>();
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

            CompletableFuture<ToolResultPayload> future =
                    callMultiModalAgentStream(request, artifactSource, streamSession);
            return future.get(MULTIMODAL_AGENT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            RemoteStreamSession session = streamSession.get();
            if (session != null) {
                session.cancel();
            }
            log.error("{} multimodalagent_tool timeout after {} minutes",
                    agentContext.getRequestId(), MULTIMODAL_AGENT_TIMEOUT_MINUTES, e);
            return buildFailurePayload("multimodalagent_tool 执行失败：多模态检索超时，请稍后重试。");
        } catch (Exception e) {
            log.error("{} multimodalagent_tool error", agentContext.getRequestId(), e);
            return buildFailurePayload("multimodalagent_tool 执行失败：" + e.getMessage());
        }
    }

    public CompletableFuture<ToolResultPayload> callMultiModalAgentStream(MultiModalAgentRequest multiModalAgentRequest,
                                                                          ToolArtifactSource artifactSource) {
        return callMultiModalAgentStream(multiModalAgentRequest, artifactSource, new AtomicReference<>());
    }

    private CompletableFuture<ToolResultPayload> callMultiModalAgentStream(
            MultiModalAgentRequest multiModalAgentRequest,
            ToolArtifactSource artifactSource,
            AtomicReference<RemoteStreamSession> streamSession) {
        // 远端响应是异步 SSE，future 负责把流生命周期收敛回同步工具契约；
        // listener 只转发事件给局部 StreamState，避免并发调用共享缓冲区。
        CompletableFuture<ToolResultPayload> future = new CompletableFuture<>();
        try {
            ReactorConfig reactorConfig = requireReactorConfig();
            String url = reactorConfig.getMultiModalAgentUrl() + "/v1/tool/mragQuery";
            log.info("{} multimodalagent_tool request {}", agentContext.getRequestId(),
                    JSONObject.toJSONString(multiModalAgentRequest));

            String[] interval = reactorConfig.getMessageInterval().getOrDefault("knowledge", "1,4").split(",");
            int firstInterval = Integer.parseInt(interval[0]);
            int sendInterval = Integer.parseInt(interval[1]);
            // 单次调用局部 StreamState，避免并行 execute 抢写实例字段
            StreamState localState = new StreamState(artifactSource, firstInterval, sendInterval, future);
            RemoteStreamSession session = requireRemoteStreamPort().openStream(RemoteStreamRequest.builder()
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
                    localState.consume(line.substring(5).trim());
                }

                @Override
                public void onClosed() {
                    // close 不是成功标志：由 StreamState.complete() 根据是否收到
                    // 有效内容决定成功或失败，并补发尚未刷出的最终 Markdown。
                    localState.complete();
                    streamSession.set(null);
                }

                @Override
                public void onFailure(Throwable throwable, Integer statusCode, String responseBody) {
                    // 失败直接完成 future，避免等待超时；当前协议不把半截答案注册为
                    // 成功 artifact，完整结果必须经过正常终态收口。
                    streamSession.set(null);
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
            streamSession.set(session);
        } catch (Exception e) {
            log.error("{} multimodalagent_tool request error", agentContext.getRequestId(), e);
            future.complete(buildFailurePayload("multimodalagent_tool 执行失败：" + e.getMessage()));
        }
        return future;
    }

    private class StreamState {
        // 每次请求独立保存增量过程、完整答案和终态标记；finalSent 防止 close 与
        // 显式 final 包竞争时重复推送。
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
            // 心跳、空包和 [DONE] 不改变业务状态；解析异常只丢弃当前 chunk，
            // 让后续合法事件仍有机会完成本次请求。
            if (!StringUtils.hasText(data) || "[DONE]".equals(data) || data.startsWith("heartbeat")) {
                return;
            }
            if (finalSent) {
                // 终态已推送后忽略后续 stop/final 重复包
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
            // 上游可能没有发送显式 final，因此 close 时以 fullContent 做最后一致性
            // 检查，并只完成一次 future。
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
        // stage 协议和 choices 协议共存：先按 stage 分流，否则按 token 累加答案；
        // 两者最终都通过相同的过程事件/最终 Markdown 出口。
        if (streamResponse == null) {
            return false;
        }

        // 细粒度 stage 事件：过程走 knowledge，答案 token 仍累加 fullContent，final 仅推答案 markdown
        if (StringUtils.hasText(streamResponse.getStage())) {
            return handleStageEvent(streamResponse, messageId, digitalEmployee, incrementalBuffer,
                    fullContent, artifactSource);
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
                // 不在此终态：等待 stage=final，以便拼上图片 markdown 等尾部片段
                flushIncrementalKnowledge(messageId, digitalEmployee, incrementalBuffer);
            }
            return false;
        }

        appendContent(streamResponse.getData(), incrementalBuffer, fullContent);
        if (Boolean.TRUE.equals(streamResponse.getIsFinal())
                && !StringUtils.hasText(streamResponse.getStage())) {
            flushIncrementalKnowledge(messageId, digitalEmployee, incrementalBuffer);
            emitFinalMarkdown(messageId, digitalEmployee, fullContent.toString(), artifactSource);
            return true;
        }
        if (shouldEmitIncremental(chunkIndex, firstInterval, sendInterval)) {
            emitIncrementalKnowledge(messageId, digitalEmployee, incrementalBuffer);
        }
        return false;
    }

    /**
     * stage 事件分流：过程 Markdown 立即 knowledge；answer/final 不写答案 buffer 的过程标题；error 失败终态。
     */
    private boolean handleStageEvent(MultiModalAgentResponse streamResponse,
                                     String messageId,
                                     String digitalEmployee,
                                     StringBuilder incrementalBuffer,
                                     StringBuilder fullContent,
                                     ToolArtifactSource artifactSource) {
        // error 是失败终态，final 是答案终态，其余 stage 仅进入 knowledge 过程区；
        // 过程标题不能混入最终答案，否则历史回放会把进度文本当成用户结果。
        String stage = streamResponse.getStage();
        String data = streamResponse.getData();

        if ("error".equalsIgnoreCase(stage)) {
            flushIncrementalKnowledge(messageId, digitalEmployee, incrementalBuffer);
            String message = StringUtils.hasText(data) ? data : "MRAG 检索失败";
            if (fullContent.length() == 0) {
                fullContent.append(message);
            }
            emitFinalMarkdown(messageId, digitalEmployee, fullContent.toString(), artifactSource);
            return true;
        }

        if ("final".equalsIgnoreCase(stage) || Boolean.TRUE.equals(streamResponse.getIsFinal())) {
            flushIncrementalKnowledge(messageId, digitalEmployee, incrementalBuffer);
            // final.data 通常为空，答案真相在 fullContent（token 累加）
            if (StringUtils.hasText(data) && fullContent.length() == 0) {
                fullContent.append(data);
            }
            emitFinalMarkdown(messageId, digitalEmployee, fullContent.toString(), artifactSource);
            return true;
        }

        if ("answer".equalsIgnoreCase(stage)) {
            // 「# 生成答案」标题进过程区，不进入答案正文
            if (StringUtils.hasText(data)) {
                emitProcessKnowledge(messageId, digitalEmployee, data);
            }
            return false;
        }

        // task/route/retrieve_round/summarize/plan_next/merge/rerank …
        if (StringUtils.hasText(data)) {
            emitProcessKnowledge(messageId, digitalEmployee, data);
        }
        return false;
    }

    private void emitProcessKnowledge(String messageId, String digitalEmployee, String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return;
        }
        MultiModalAgentResponse response = MultiModalAgentResponse.builder()
                .data(markdown)
                .isFinal(false)
                .stage("process")
                .build();
        attachToolCallId(response);
        agentContext.getPrinter().send(messageId, "knowledge", response, digitalEmployee, false);
    }

    private void flushIncrementalKnowledge(String messageId,
                                           String digitalEmployee,
                                           StringBuilder incrementalBuffer) {
        emitIncrementalKnowledge(messageId, digitalEmployee, incrementalBuffer);
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
        // 最终 Markdown 同时推送给前端并登记为 artifact，toolCallId 由当前上下文
        // 绑定，保证实时卡片与持久化产物关联到同一次工具调用。
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
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("summary", structuredOutput.getSummary());
        fields.put("markdownContent", markdownContent);
        fields.put("fileRefs", structuredOutput.getFileRefs());
        return ToolResultPayload.okData(getName(), fields, structuredOutput);
    }

    /**
     * 失败路径也返回最小 typed output，避免账本再次出现空结构。
     */
    private ToolResultPayload buildFailurePayload(String message) {
        MultimodalAgentToolOutput structuredOutput = MultimodalAgentToolOutput.builder()
                .summary(message)
                .markdownContent("")
                .build();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tool", getName());
        detail.put("summary", message);
        return ToolResultPayload.failureFrom(message, detail, structuredOutput);
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
