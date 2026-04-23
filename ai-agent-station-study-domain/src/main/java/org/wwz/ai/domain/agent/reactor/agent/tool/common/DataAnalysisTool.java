package org.wwz.ai.domain.agent.reactor.agent.tool.common;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.context.ApplicationContext;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.reactor.agent.dto.DataAnalysisRequest;
import org.wwz.ai.domain.agent.reactor.agent.dto.DataAnalysisResponse;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;

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
        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
        StringBuilder description = new StringBuilder(reactorConfig.getDataAnalysisToolDesc().isEmpty() ? desc : reactorConfig.getDataAnalysisToolDesc());
        return description.toString();
    }

    @Override
    public Map<String, Object> toParams() {
        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
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
        long startTime = System.currentTimeMillis();
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

            // 调用流式 API
            Future<String> future = callAutoAnalysisStream(request);
            Object object =  future.get();
            return object;
        } catch (Exception e) {
            log.error("{} auto_analysis agent error", agentContext.getRequestId(), e);
        }
        agentContext.getPrinter().send("tool_result", AgentResponse.ToolResult.builder()
                .toolName("数据分析智能体")
                .toolParam(new HashMap<>())
                .toolResult("执行失败")
                .build());
        return null;
    }
    
    /**
     * 调用自动分析API
     */
    public CompletableFuture<String> callAutoAnalysisStream(DataAnalysisRequest analysisRequest) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60000, TimeUnit.SECONDS) // 设置连接超时时间为 60 秒
                    .readTimeout(30000, TimeUnit.SECONDS)    // 设置读取超时时间为 300 秒
                    .writeTimeout(30000, TimeUnit.SECONDS)   // 设置写入超时时间为 300 秒
                    .callTimeout(30000, TimeUnit.SECONDS)    // 设置调用超时时间为 300 秒
                    .build();

            ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
            ReactorConfig duccConfig = applicationContext.getBean(ReactorConfig.class);
            // 使用默认的自动分析API URL
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
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    log.info("{} auto_analysis response {} {} {}", agentContext.getRequestId(), response, response.code(), response.body());
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            log.error("{} auto_analysis request error", agentContext.getRequestId());
                            future.completeExceptionally(new IOException("Unexpected response code: " + response));
                            return;
                        }

                        String line;
                        BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));
                        String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
                        String result = "分析结果为空"; // 默认输出
                        String messageId = StringUtil.getUUID();
                        StringBuilder fullContentBuilder = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            // 处理SSE格式的数据
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (data.equals("[DONE]")) {
                                    break;
                                }
                                if (data.equals("heartbeat")) {
                                    // 心跳消息，跳过处理
                                    continue;
                                }
                                log.info("{} auto_analysis recv data: {}", agentContext.getRequestId(), data);
                                try {
                                    // 累积全量内容
                                    DataAnalysisResponse analysisResponse = JSONObject.parseObject(data, DataAnalysisResponse.class);
                                    fullContentBuilder.append(analysisResponse.getData()).append("\n");
                                    // 接收文件
                                    if (Objects.nonNull(analysisResponse.getFileInfo()) && !analysisResponse.getFileInfo().isEmpty()) {
                                        for (CodeInterpreterResponse.FileInfo fileInfo: analysisResponse.getFileInfo()) {
                                            File file = File.builder()
                                                    .fileName(fileInfo.getFileName())
                                                    .ossUrl(fileInfo.getOssUrl())
                                                    .domainUrl(fileInfo.getDomainUrl())
                                                    .fileSize(fileInfo.getFileSize())
                                                    .description(fileInfo.getFileName()) // fileName用作描述
                                                    .isInternalFile(false)
                                                    .build();
                                            agentContext.getProductFiles().add(file);
                                            agentContext.getTaskProductFiles().add(file);
                                        }
                                    }

                                    // 当收到最终结果时，输出全量内容
                                    if (Boolean.TRUE.equals(analysisResponse.getIsFinal())) {
                                        analysisResponse.setTask(analysisRequest.getTask());
                                        analysisResponse.setData(fullContentBuilder.toString());
                                        agentContext.getPrinter().send(messageId, "data_analysis",
                                                analysisResponse, digitalEmployee, true);
                                        result = fullContentBuilder.toString();
                                    } else {
                                        analysisResponse.setTask(analysisRequest.getTask());
                                        agentContext.getPrinter().send(messageId, "data_analysis",
                                                analysisResponse, digitalEmployee, false);
                                        result = JSON.toJSONString(analysisResponse.getData());
                                    }
                                } catch (Exception parseException) {
                                    log.warn("{} auto_analysis parse response error: {}", agentContext.getRequestId(), parseException.getMessage());
                                }
                            }
                        }
                        future.complete(result);
                    } catch (Exception e) {
                        log.error("{} auto_analysis request error", agentContext.getRequestId(), e);
                        future.completeExceptionally(e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("{} auto_analysis request error", agentContext.getRequestId(), e);
            future.completeExceptionally(e);
        }

        return future;
    }
}