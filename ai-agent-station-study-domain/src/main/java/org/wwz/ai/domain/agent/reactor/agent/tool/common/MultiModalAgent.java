package org.wwz.ai.domain.agent.reactor.agent.tool.common;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.reactor.agent.dto.FileRequest;
import org.wwz.ai.domain.agent.reactor.agent.dto.MultiModalAgentRequest;
import org.wwz.ai.domain.agent.reactor.agent.dto.MultiModalAgentResponse;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
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
    private volatile Call activeCall;

    @Override
    public String getName() {
        return "multimodalagent_tool";
    }

    @Override
    public String getDescription() {
        String defaultDesc = "本工具用于查询与用户相关的知识，作为在线知识的补充。支持文本和图像等多模态数据检索，能够高效访问和获取用户专属的知识信息。";
        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
        return StringUtils.hasText(reactorConfig.getMultiModalAgentDesc())
                ? reactorConfig.getMultiModalAgentDesc()
                : defaultDesc;
    }

    @Override
    public Map<String, Object> toParams() {
        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
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
                return "multimodalagent_tool 执行失败：question 不能为空。";
            }

            ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
            if (!StringUtils.hasText(reactorConfig.getMultiModalAgentUrl())) {
                return "multimodalagent_tool 执行失败：未配置 multimodalagent_url。";
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

            CompletableFuture<String> future = callMultiModalAgentStream(request, artifactSource);
            return future.get(MULTIMODAL_AGENT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            if (activeCall != null && !activeCall.isCanceled()) {
                activeCall.cancel();
            }
            log.error("{} multimodalagent_tool timeout after {} minutes",
                    agentContext.getRequestId(), MULTIMODAL_AGENT_TIMEOUT_MINUTES, e);
            return "multimodalagent_tool 执行失败：多模态检索超时，请稍后重试。";
        } catch (Exception e) {
            log.error("{} multimodalagent_tool error", agentContext.getRequestId(), e);
            return "multimodalagent_tool 执行失败：" + e.getMessage();
        } finally {
            activeCall = null;
        }
    }

    public CompletableFuture<String> callMultiModalAgentStream(MultiModalAgentRequest multiModalAgentRequest,
                                                               ToolArtifactSource artifactSource) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(MULTIMODAL_AGENT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(MULTIMODAL_AGENT_IO_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .writeTimeout(MULTIMODAL_AGENT_IO_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .callTimeout(MULTIMODAL_AGENT_IO_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .build();

            ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
            ReactorConfig reactorConfig = applicationContext.getBean(ReactorConfig.class);
            String url = reactorConfig.getMultiModalAgentUrl() + "/v1/tool/mragQuery";
            RequestBody body = RequestBody.create(
                    JSONObject.toJSONString(multiModalAgentRequest),
                    MediaType.parse("application/json")
            );

            log.info("{} multimodalagent_tool request {}", agentContext.getRequestId(),
                    JSONObject.toJSONString(multiModalAgentRequest));

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            String[] interval = reactorConfig.getMessageInterval().getOrDefault("knowledge", "1,4").split(",");
            int firstInterval = Integer.parseInt(interval[0]);
            int sendInterval = Integer.parseInt(interval[1]);

            Call call = client.newCall(request);
            activeCall = call;
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("{} multimodalagent_tool on failure", agentContext.getRequestId(), e);
                    future.complete("multimodalagent_tool 执行失败：无法连接多模态检索服务。");
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            log.error("{} multimodalagent_tool request error, code={}",
                                    agentContext.getRequestId(), response.code());
                            future.complete("multimodalagent_tool 执行失败：上游服务返回异常状态 " + response.code() + "。");
                            return;
                        }

                        int chunkIndex = 1;
                        String messageId = StringUtil.getUUID();
                        boolean finalSent = false;
                        StringBuilder incrementalBuffer = new StringBuilder();
                        StringBuilder fullContent = new StringBuilder();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));
                        String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) {
                                continue;
                            }

                            String data = line.substring(5).trim();
                            if (!StringUtils.hasText(data)) {
                                continue;
                            }
                            if ("[DONE]".equals(data)) {
                                break;
                            }
                            if (data.startsWith("heartbeat")) {
                                continue;
                            }

                            MultiModalAgentResponse streamResponse;
                            try {
                                streamResponse = JSONObject.parseObject(data, MultiModalAgentResponse.class);
                            } catch (Exception parseException) {
                                log.warn("{} multimodalagent_tool parse chunk failed, raw={}",
                                        agentContext.getRequestId(), data, parseException);
                                continue;
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

                        if (!finalSent && fullContent.length() > 0) {
                            emitFinalMarkdown(messageId, digitalEmployee, fullContent.toString(), artifactSource);
                            finalSent = true;
                        }

                        if (fullContent.length() == 0) {
                            future.complete("multimodalagent_tool 执行失败：上游未返回有效内容。");
                            return;
                        }

                        future.complete(fullContent.toString());
                    } catch (Exception e) {
                        log.error("{} multimodalagent_tool request error", agentContext.getRequestId(), e);
                        future.complete("multimodalagent_tool 执行失败：" + e.getMessage());
                    } finally {
                        activeCall = null;
                    }
                }
            });
        } catch (Exception e) {
            log.error("{} multimodalagent_tool request error", agentContext.getRequestId(), e);
            future.complete("multimodalagent_tool 执行失败：" + e.getMessage());
        }
        return future;
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
        agentContext.getPrinter().send(messageId, "markdown", response, digitalEmployee, true);
        uploadMarkdownArtifact(markdownContent, artifactSource);
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
}
