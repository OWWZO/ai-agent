package org.wwz.ai.domain.agent.reactor.agent.tool.common;


import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.reactor.agent.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.reactor.agent.dto.DataAnalysisRequest;
import org.wwz.ai.domain.agent.reactor.agent.dto.DataAnalysisResponse;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DataAnalysisToolOutput;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolFileRefMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60000, TimeUnit.SECONDS)
                    .readTimeout(30000, TimeUnit.SECONDS)
                    .writeTimeout(30000, TimeUnit.SECONDS)
                    .callTimeout(30000, TimeUnit.SECONDS)
                    .build();

            ReactorConfig duccConfig = requireReactorConfig();
            String url = duccConfig.getDataAnalysisUrl() + "/v1/tool/auto_analysis";

            RequestBody body = RequestBody.create(
                    JSONObject.toJSONString(analysisRequest),
                    MediaType.parse("application/json")
            );

            log.info("{} auto_analysis request {}", agentContext.getRequestId(), JSONObject.toJSONString(analysisRequest));
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(body);
            Request request = requestBuilder.build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("{} auto_analysis on failure", agentContext.getRequestId(), e);
                    future.complete(buildFailurePayload("data_analysis 执行失败：无法连接数据分析服务。"));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    log.info("{} auto_analysis response {} {} {}", agentContext.getRequestId(), response, response.code(), response.body());
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            log.error("{} auto_analysis request error", agentContext.getRequestId());
                            future.complete(buildFailurePayload("data_analysis 执行失败：上游服务返回异常状态 " + response.code() + "。"));
                            return;
                        }

                        String line;
                        BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));
                        String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
                        String messageId = StringUtil.getUUID();
                        String toolCallId = artifactSource == null ? null : artifactSource.getToolCallId();
                        StringBuilder fullContentBuilder = new StringBuilder();
                        List<CodeInterpreterResponse.FileInfo> finalFileInfo = new ArrayList<>();
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data: ")) {
                                continue;
                            }
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) {
                                break;
                            }
                            if ("heartbeat".equals(data)) {
                                continue;
                            }
                            log.info("{} auto_analysis recv data: {}", agentContext.getRequestId(), data);
                            try {
                                DataAnalysisResponse analysisResponse = JSONObject.parseObject(data, DataAnalysisResponse.class);
                                if (analysisResponse == null) {
                                    continue;
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
                        future.complete(buildSuccessPayload(analysisRequest, fullContentBuilder.toString(), finalFileInfo));
                    } catch (Exception e) {
                        log.error("{} auto_analysis request error", agentContext.getRequestId(), e);
                        future.complete(buildFailurePayload("data_analysis 执行失败：" + e.getMessage()));
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
}
