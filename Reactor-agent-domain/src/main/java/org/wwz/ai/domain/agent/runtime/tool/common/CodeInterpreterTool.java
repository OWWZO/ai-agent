package org.wwz.ai.domain.agent.runtime.tool.common;


import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamListener;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;

import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterRequest;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CodeInterpreterToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRefMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Slf4j
@Data
public class CodeInterpreterTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return "code_interpreter";
    }

    @Override
    public String getDescription() {
        String desc = "这是一个代码工具，可以通过编写代码完成数据处理、数据分析、图表生成等任务";
        ReactorConfig reactorConfig = requireReactorConfig();
        return reactorConfig.getCodeAgentDesc().isEmpty() ? desc : reactorConfig.getCodeAgentDesc();
    }

    @Override
    public Map<String, Object> toParams() {

        ReactorConfig reactorConfig = requireReactorConfig();
        if (!reactorConfig.getCodeAgentParams().isEmpty()) {
            return reactorConfig.getCodeAgentParams();
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
            List<String> fileNames = agentContext.getProductFiles().stream().map(File::getFileName).collect(Collectors.toList());
            CodeInterpreterRequest request = CodeInterpreterRequest.builder()
                    .requestId(agentContext.getSessionId()) // 适配多轮对话
                    .query(agentContext.getQuery())
                    .task(task)
                    .fileNames(fileNames)
                    .stream(true)
                    .build();
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());

            // 调用流式 API
            Future<ToolResultPayload> future = callCodeAgentStream(request, artifactSource);
            return future.get();
        } catch (Exception e) {
            log.error("{} code agent error", agentContext.getRequestId(), e);
            return buildFailurePayload("code_interpreter 执行失败：" + e.getMessage());
        }
    }

    /**
     * 调用 CodeAgent
     */
    public CompletableFuture<ToolResultPayload> callCodeAgentStream(CodeInterpreterRequest codeRequest,
                                                                    ToolArtifactSource artifactSource) {
        CompletableFuture<ToolResultPayload> future = new CompletableFuture<>();
        try {
            ReactorConfig reactorConfig = requireReactorConfig();
            String url = reactorConfig.getCodeInterpreterUrl() + "/v1/tool/code_interpreter";
            log.info("{} code_interpreter request {}", agentContext.getRequestId(), JSONObject.toJSONString(codeRequest));
            java.util.concurrent.atomic.AtomicReference<CodeInterpreterResponse> latestResponseRef =
                    new java.util.concurrent.atomic.AtomicReference<>(CodeInterpreterResponse.builder()
                            .codeOutput("code_interpreter执行失败")
                            .build());

            requireRemoteStreamPort().openStream(RemoteStreamRequest.builder()
                    .method("POST")
                    .url(url)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(JSONObject.toJSONString(codeRequest))
                    .connectTimeoutSeconds(6000L)
                    .readTimeoutSeconds(300000L)
                    .writeTimeoutSeconds(30000L)
                    .callTimeoutSeconds(300000L)
                    .build(), new RemoteStreamListener() {
                @Override
                public void onOpen() {
                    log.info("{} code_interpreter stream opened", agentContext.getRequestId());
                }

                @Override
                public void onLine(String line) {
                    if (!line.startsWith("data:")) {
                        return;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data) || data.startsWith("heartbeat")) {
                        return;
                    }
                    log.info("{} code_interpreter recv data: {}", agentContext.getRequestId(), data);
                    CodeInterpreterResponse codeResponse = JSONObject.parseObject(data, CodeInterpreterResponse.class);
                    latestResponseRef.set(codeResponse);
                    if (Objects.nonNull(codeResponse.getFileInfo()) && !codeResponse.getFileInfo().isEmpty()) {
                        for (CodeInterpreterResponse.FileInfo fileInfo : codeResponse.getFileInfo()) {
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
                    String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
                    log.info("requestId:{} task:{} toolName:{} digitalEmployee:{}", agentContext.getRequestId(),
                            agentContext.getToolCollection().getCurrentTask(), getName(), digitalEmployee);
                    agentContext.getPrinter().send("code", codeResponse, digitalEmployee);
                }

                @Override
                public void onClosed() {
                    CodeInterpreterResponse codeResponse = latestResponseRef.get();
                    StringBuilder output = new StringBuilder(StringUtils.defaultString(codeResponse.getCodeOutput()));
                    if (Objects.nonNull(codeResponse.getFileInfo()) && !codeResponse.getFileInfo().isEmpty()) {
                        output.append("\n\n其中保存了文件: ");
                        for (CodeInterpreterResponse.FileInfo fileInfo : codeResponse.getFileInfo()) {
                            output.append(fileInfo.getFileName()).append("\n");
                        }
                    }
                    if (!future.isDone()) {
                        future.complete(buildSuccessPayload(codeResponse, output.toString()));
                    }
                }

                @Override
                public void onFailure(Throwable throwable, Integer statusCode, String responseBody) {
                    log.error("{} code_interpreter on failure, statusCode={}, body={}",
                            agentContext.getRequestId(), statusCode, responseBody, throwable);
                    if (!future.isDone()) {
                        if (statusCode != null) {
                            future.complete(buildFailurePayload("code_interpreter 执行失败：上游服务返回异常状态 " + statusCode + "。"));
                        } else {
                            future.complete(buildFailurePayload("code_interpreter 执行失败：" + throwable.getMessage()));
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("{} code_interpreter request error", agentContext.getRequestId(), e);
            future.complete(buildFailurePayload("code_interpreter 执行失败：" + e.getMessage()));
        }

        return future;
    }

    private ToolResultPayload buildSuccessPayload(CodeInterpreterResponse codeResponse, String displayText) {
        return ToolResultPayload.structured(
                displayText,
                displayText,
                CodeInterpreterToolOutput.builder()
                        .codeOutput(codeResponse == null ? null : codeResponse.getCodeOutput())
                        .content(codeResponse == null ? null : codeResponse.getContent())
                        .code(codeResponse == null ? null : codeResponse.getCode())
                        .explain(codeResponse == null ? null : codeResponse.getExplain())
                        .fileRefs(ToolFileRefMapper.fromCodeInterpreterFileInfo(codeResponse == null ? null : codeResponse.getFileInfo()))
                        .build()
        );
    }

    private ToolResultPayload buildFailurePayload(String message) {
        return ToolResultPayload.failure(
                message,
                message,
                CodeInterpreterToolOutput.builder()
                        .codeOutput(message)
                        .build(),
                message
        );
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("CodeInterpreterTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    private RemoteStreamPort requireRemoteStreamPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("CodeInterpreterTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireRemoteStreamPort();
    }
}
