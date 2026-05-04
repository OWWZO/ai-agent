package org.wwz.ai.domain.agent.reactor.agent.tool.common;


import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.reactor.agent.dto.CodeInterpreterRequest;
import org.wwz.ai.domain.agent.reactor.agent.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolFileRefMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Data

public class ReportTool implements BaseTool {
    private AgentContext agentContext;

    @Override
    public String getName() {
        return "report_tool";
    }

    @Override
    public String getDescription() {
        String desc = "这是一个报告工具，可以通过编写HTML、MarkDown报告";
        ReactorConfig reactorConfig = requireReactorConfig();
        return reactorConfig.getReportToolDesc().isEmpty() ? desc : reactorConfig.getReportToolDesc();
    }

    @Override
    public Map<String, Object> toParams() {

        ReactorConfig reactorConfig = requireReactorConfig();
        if (!reactorConfig.getReportToolParams().isEmpty()) {
            return reactorConfig.getReportToolParams();
        }

        Map<String, Object> taskParam = new HashMap<>();
        taskParam.put("type", "string");
        taskParam.put("description", "需要完成的任务以及完成任务需要的数据，需要尽可能详细");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("task", taskParam);
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("task"));

        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> params = (Map<String, Object>) input;
            String task = (String) params.get("task");
            String fileDescription = (String) params.get("fileDescription");
            String fileName = (String) params.get("fileName");
            String fileType = (String) params.get("fileType");

            if (StringUtils.isBlank(fileName)) {
                String errMessage = "文件名参数为空，无法生成报告。";
                log.error("{} {}", agentContext.getRequestId(), errMessage);
                return buildFailurePayload(errMessage);
            }

            List<String> fileNames = agentContext.getProductFiles().stream().map(File::getFileName).collect(Collectors.toList());
            Map<String, Object> streamMode = new HashMap<>();
            streamMode.put("mode", "token");
            streamMode.put("token", 10);
            CodeInterpreterRequest request = CodeInterpreterRequest.builder()
                    .requestId(agentContext.getSessionId()) // 适配多轮对话
                    .query(agentContext.getQuery())
                    .task(task)
                    .fileNames(fileNames)
                    .fileName(fileName)
                    .fileDescription(fileDescription)
                    .stream(true)
                    .contentStream(agentContext.getIsStream())
                    .streamMode(streamMode)
                    .fileType(fileType)
                    .templateType(agentContext.getTemplateType())
                    .build();
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
            // 调用流式 API
            Future<ToolResultPayload> future = callCodeAgentStream(request, artifactSource);
            return future.get();
        } catch (Exception e) {
            log.error("{} report_tool error", agentContext.getRequestId(), e);
            return buildFailurePayload("report_tool 执行失败：" + e.getMessage());
        }
    }

    /**
     * 调用 CodeAgent
     */
    public CompletableFuture<ToolResultPayload> callCodeAgentStream(CodeInterpreterRequest codeRequest,
                                                                    ToolArtifactSource artifactSource) {
        CompletableFuture<ToolResultPayload> future = new CompletableFuture<>();
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60000, TimeUnit.SECONDS) // 设置连接超时时间为 1 分钟
                    .readTimeout(60000, TimeUnit.SECONDS)    // 设置读取超时时间为 10 分钟
                    .writeTimeout(60000, TimeUnit.SECONDS)   // 设置写入超时时间为 10 分钟
                    .callTimeout(6000, TimeUnit.SECONDS)    // 设置调用超时时间为 10 分钟
                    .build();

            ReactorConfig reactorConfig = requireReactorConfig();
            String url = reactorConfig.getCodeInterpreterUrl() + "/v1/tool/report";
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    JSONObject.toJSONString(codeRequest)
            );

            log.info("{} report_tool request {}", agentContext.getRequestId(), JSONObject.toJSONString(codeRequest));
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(body);
            Request request = requestBuilder.build();

            String[] interval = reactorConfig.getMessageInterval().getOrDefault("report", "1,4").split(",");
            int firstInterval = Integer.parseInt(interval[0]);
            int sendInterval = Integer.parseInt(interval[1]);
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("{} report_tool on failure", agentContext.getRequestId(), e);
                    future.complete(buildFailurePayload("report_tool 执行失败：无法连接报告生成服务。"));
                }

                @Override
                public void onResponse(Call call, Response response) {

                    log.info("{} report_tool response {} {} {}", agentContext.getRequestId(), response, response.code(), response.body());
                    CodeInterpreterResponse codeResponse = CodeInterpreterResponse.builder()
                            .codeOutput("report_tool 执行失败") // 默认输出
                            .build();
                    try {
                        ResponseBody responseBody = response.body();
                        if (!response.isSuccessful() || responseBody == null) {
                            log.error("{} report_tool request error.", agentContext.getRequestId());
                            future.complete(buildFailurePayload("report_tool 执行失败：上游服务返回异常状态 " + response.code() + "。"));
                            return;
                        }

                        int index = 1;
                        StringBuilder stringBuilderIncr = new StringBuilder();
                        String line;
                        String messageId = StringUtil.getUUID();
                        String toolCallId = artifactSource == null ? null : artifactSource.getToolCallId();
                        // 获取数字人名称
                        String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
                        BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (data.equals("[DONE]")) {
                                    break;
                                }
                                if (index == 1 || index % 100 == 0) {
                                    log.info("{} report_tool recv data: {}", agentContext.getRequestId(), data);
                                }
                                if (data.startsWith("heartbeat")) {
                                    continue;
                                }
                                codeResponse = JSONObject.parseObject(data, CodeInterpreterResponse.class);
                                codeResponse.setToolCallId(toolCallId);
                                if (codeResponse.getIsFinal()) {
                                    // report_tool 只会输出一个文件，使用模型输出的文件名和描述
                                    if (Objects.nonNull(codeResponse.getFileInfo())) {
                                        for (CodeInterpreterResponse.FileInfo fileInfo : codeResponse.getFileInfo()) {
                                            File file = File.builder()
                                                    .fileName(codeRequest.getFileName())
                                                    .fileSize(fileInfo.getFileSize())
                                                    .ossUrl(fileInfo.getOssUrl())
                                                    .domainUrl(fileInfo.getDomainUrl())
                                                    .description(codeRequest.getFileDescription())
                                                    .isInternalFile(false)
                                                    .build();
                                            agentContext.registerGeneratedArtifact(artifactSource, file);
                                        }
                                    }
                                    agentContext.getPrinter().send(messageId, codeRequest.getFileType(), codeResponse, digitalEmployee, true);
                                } else {
                                    stringBuilderIncr.append(codeResponse.getData());
                                    if (index == firstInterval || index % sendInterval == 0) {
                                        codeResponse.setData(stringBuilderIncr.toString());
                                        agentContext.getPrinter().send(messageId, codeRequest.getFileType(), codeResponse, digitalEmployee, false);
                                        stringBuilderIncr.setLength(0);
                                    }
                                }
                                index++;
                            }
                        }
                    } catch (Exception e) {
                        log.error("{} report_tool request error", agentContext.getRequestId(), e);
                        future.complete(buildFailurePayload("report_tool 执行失败：" + e.getMessage()));
                        return;
                    }
                    // 统一使用data字段，兼容历史codeOutput逻辑
                    String result = StringUtils.isNotBlank(codeResponse.getData()) ? codeResponse.getData() : codeResponse.getCodeOutput();
                    future.complete(buildSuccessPayload(codeRequest, codeResponse, result));
                }
            });
        } catch (Exception e) {
            log.error("{} report_tool request error", agentContext.getRequestId(), e);
            future.complete(buildFailurePayload("report_tool 执行失败：" + e.getMessage()));
        }

        return future;
    }

    /**
     * 报告工具需要保留文件类型、正文内容和文件引用，便于历史回放还原 Markdown/HTML/PPT 展示。
     */
    private ToolResultPayload buildSuccessPayload(CodeInterpreterRequest codeRequest,
                                                  CodeInterpreterResponse codeResponse,
                                                  String result) {
        String normalizedResult = StringUtils.defaultString(result);
        return ToolResultPayload.structured(
                normalizedResult,
                normalizedResult,
                ReportToolOutput.builder()
                        .fileType(codeRequest.getFileType())
                        .summary(abbreviate(normalizedResult, 160))
                        .content(normalizedResult)
                        .fileRefs(ToolFileRefMapper.fromCodeInterpreterFileInfo(codeResponse == null ? null : codeResponse.getFileInfo()))
                        .build()
        );
    }

    /**
     * 失败路径统一返回最小 typed output，避免 rich tool 落回空结构。
     */
    private ToolResultPayload buildFailurePayload(String message) {
        return ToolResultPayload.failure(
                message,
                message,
                ReportToolOutput.builder()
                        .summary(message)
                        .content("")
                        .build(),
                message
        );
    }

    private String abbreviate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return StringUtils.defaultString(text);
        }
        return text.substring(0, maxLen) + "...";
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("ReportTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }
}
