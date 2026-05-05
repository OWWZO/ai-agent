package org.wwz.ai.domain.agent.runtime.tool.common;


import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamListener;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.dto.DataAnalysisRequest;
import org.wwz.ai.domain.agent.runtime.dto.DataAnalysisResponse;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DataAnalysisToolOutput;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRefMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Slf4j
@Data
public class DataAnalysisTool implements BaseTool {
    private AgentContext agentContext;

    @Override
    public String getName() {
        return "data_analysis";
    }

    @Override
    public String getDescription() {
        String desc = "这是一个数据分析工具，可以查询并分析数据";
        ReactorConfig reactorConfig = requireReactorConfig();
        StringBuilder description = new StringBuilder(reactorConfig.getDataAnalysisToolDesc().isEmpty() ? desc : reactorConfig.getDataAnalysisToolDesc());
        return description.toString();
    }

    @Override
    public Map<String, Object> toParams() {
        ReactorConfig reactorConfig = requireReactorConfig();
        if (!reactorConfig.getDataAnalysisToolParams().isEmpty()) {
            return reactorConfig.getDataAnalysisToolParams();
        }

        Map<String, Object> taskParam = new HashMap<>();
        taskParam.put("type", "string");
        taskParam.put("description", "task");

        Map<String, Object> businessKnowledgeParam = new HashMap<>();
        businessKnowledgeParam.put("type", "string");
        businessKnowledgeParam.put("description", "businessKnowledge");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("task", taskParam);
        properties.put("businessKnowledge", businessKnowledgeParam);
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("task", "businessKnowledge"));

        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> params = (Map<String, Object>) input;
            String task = (String) params.getOrDefault("task", "");
            String businessKnowledge = (String) params.getOrDefault("businessKnowledge", "");

            DataAnalysisRequest request = DataAnalysisRequest.builder()
                    .request_id(agentContext.getSessionId())
                    .erp("reactor")
                    .task(task)
                    .modelCodeList(Arrays.asList("modelCode"))
                    .businessKnowledge(businessKnowledge)
                    .build();
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());

            // 调用流式 API
            Future<ToolResultPayload> future = callAutoAnalysisStream(request, artifactSource);
            return future.get();
        } catch (Exception e) {
            log.error("{} auto_analysis agent error", agentContext.getRequestId(), e);
            String message = "data_analysis 执行失败：" + StringUtils.defaultIfBlank(e.getMessage(), "未知异常");
            agentContext.getPrinter().send("tool_result", AgentResponse.ToolResult.builder()
                    .toolName("数据分析智能体")
                    .toolParam(new HashMap<>())
                    .toolResult("执行失败")
                    .build());
            return buildFailurePayload(message);
        }
    }

    /**
     * 调用自动分析 API。
     */
    public CompletableFuture<ToolResultPayload> callAutoAnalysisStream(DataAnalysisRequest analysisRequest,
                                                                       ToolArtifactSource artifactSource) {
        CompletableFuture<ToolResultPayload> future = new CompletableFuture<>();
        try {
            ReactorConfig duccConfig = requireReactorConfig();
            String url = duccConfig.getDataAnalysisUrl() + "/v1/tool/auto_analysis";
            log.info("{} auto_analysis request {}", agentContext.getRequestId(), JSONObject.toJSONString(analysisRequest));
            String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
            String messageId = StringUtil.getUUID();
            String toolCallId = artifactSource == null ? null : artifactSource.getToolCallId();
            StringBuilder fullContentBuilder = new StringBuilder();
            List<CodeInterpreterResponse.FileInfo> finalFileInfo = new ArrayList<>();

            requireRemoteStreamPort().openStream(RemoteStreamRequest.builder()
                    .method("POST")
                    .url(url)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(JSONObject.toJSONString(analysisRequest))
                    .connectTimeoutSeconds(60000L)
                    .readTimeoutSeconds(30000L)
                    .writeTimeoutSeconds(30000L)
                    .callTimeoutSeconds(30000L)
                    .build(), new RemoteStreamListener() {
                @Override
                public void onOpen() {
                    log.info("{} auto_analysis stream opened", agentContext.getRequestId());
                }

                @Override
                public void onLine(String line) {
                    if (!line.startsWith("data:")) {
                        return;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data) || "heartbeat".equals(data)) {
                        return;
                    }
                    log.info("{} auto_analysis recv data: {}", agentContext.getRequestId(), data);
                    try {
                        DataAnalysisResponse analysisResponse = JSONObject.parseObject(data, DataAnalysisResponse.class);
                        if (analysisResponse == null) {
                            return;
                        }
                        String chunkText = analysisResponse.getData() == null
                                ? ""
                                : String.valueOf(analysisResponse.getData());
                        if (StringUtils.isNotBlank(chunkText)) {
                            fullContentBuilder.append(chunkText).append("\n");
                        }
                        if (Objects.nonNull(analysisResponse.getFileInfo()) && !analysisResponse.getFileInfo().isEmpty()) {
                            finalFileInfo.clear();
                            finalFileInfo.addAll(analysisResponse.getFileInfo());
                            for (CodeInterpreterResponse.FileInfo fileInfo : analysisResponse.getFileInfo()) {
                                File file = File.builder()
                                        .fileName(fileInfo.getFileName())
                                        .ossUrl(fileInfo.getOssUrl())
                                        .domainUrl(fileInfo.getDomainUrl())
                                        .fileSize(fileInfo.getFileSize())
                                        .description(fileInfo.getFileName())
                                        .isInternalFile(false)
                                        .build();
                                agentContext.registerGeneratedArtifact(artifactSource, file);
                            }
                        }

                        analysisResponse.setTask(analysisRequest.getTask());
                        analysisResponse.setToolCallId(toolCallId);
                        if (Boolean.TRUE.equals(analysisResponse.getIsFinal())) {
                            analysisResponse.setData(fullContentBuilder.toString());
                            agentContext.getPrinter().send(messageId, "data_analysis",
                                    analysisResponse, digitalEmployee, true);
                        } else {
                            agentContext.getPrinter().send(messageId, "data_analysis",
                                    analysisResponse, digitalEmployee, false);
                        }
                    } catch (Exception parseException) {
                        log.warn("{} auto_analysis parse response error: {}", agentContext.getRequestId(), parseException.getMessage());
                    }
                }

                @Override
                public void onClosed() {
                    if (!future.isDone()) {
                        future.complete(buildSuccessPayload(analysisRequest, fullContentBuilder.toString(), finalFileInfo));
                    }
                }

                @Override
                public void onFailure(Throwable throwable, Integer statusCode, String responseBody) {
                    log.error("{} auto_analysis request error, code={}, body={}",
                            agentContext.getRequestId(), statusCode, responseBody, throwable);
                    if (!future.isDone()) {
                        if (statusCode != null) {
                            future.complete(buildFailurePayload("data_analysis 执行失败：上游服务返回异常状态 " + statusCode + "。"));
                        } else {
                            future.complete(buildFailurePayload("data_analysis 执行失败：" + throwable.getMessage()));
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("{} auto_analysis request error", agentContext.getRequestId(), e);
            future.complete(buildFailurePayload("data_analysis 执行失败：" + e.getMessage()));
        }

        return future;
    }

    /**
     * 数据分析结果需要保留任务文本、结果摘要和文件引用，便于 replay 还原分析卡片。
     */
    private ToolResultPayload buildSuccessPayload(DataAnalysisRequest request,
                                                  String data,
                                                  List<CodeInterpreterResponse.FileInfo> fileInfo) {
        String normalizedData = StringUtils.defaultIfBlank(data, "分析结果为空").trim();
        return ToolResultPayload.structured(
                normalizedData,
                normalizedData,
                DataAnalysisToolOutput.builder()
                        .task(request.getTask())
                        .summary(abbreviate(normalizedData, 160))
                        .content(normalizedData)
                        .fileRefs(ToolFileRefMapper.fromCodeInterpreterFileInfo(fileInfo))
                        .build()
        );
    }

    /**
     * 失败结果统一返回最小 typed output，避免只剩日志没有账本事实。
     */
    private ToolResultPayload buildFailurePayload(String message) {
        return ToolResultPayload.failure(
                message,
                message,
                DataAnalysisToolOutput.builder()
                        .summary(message)
                        .content("")
                        .build(),
                message
        );
    }

    private String abbreviate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("DataAnalysisTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    private RemoteStreamPort requireRemoteStreamPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("DataAnalysisTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireRemoteStreamPort();
    }
}
