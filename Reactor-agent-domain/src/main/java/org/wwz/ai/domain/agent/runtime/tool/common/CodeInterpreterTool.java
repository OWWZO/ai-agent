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
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CodeInterpreterToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRefMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 代码解释器工具。
 * <p>
 * 将 Python 源码提交到受控执行服务，消费流式结果并把新生成文件转换为稳定 artifact 引用。
 */
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
     * 调用 CodeAgent：细粒度 SSE（任务/过程/步骤思考/代码/执行输出/结论）对齐 data_analysis 累加推送。
     */
    public CompletableFuture<ToolResultPayload> callCodeAgentStream(CodeInterpreterRequest codeRequest,
                                                                    ToolArtifactSource artifactSource) {
        CompletableFuture<ToolResultPayload> future = new CompletableFuture<>();
        try {
            ReactorConfig reactorConfig = requireReactorConfig();
            String url = reactorConfig.getCodeInterpreterUrl() + "/v1/tool/code_interpreter";
            log.info("{} code_interpreter request {}", agentContext.getRequestId(), JSONObject.toJSONString(codeRequest));
            String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
            String messageId = StringUtil.getUUID();
            String toolCallId = artifactSource == null ? null : artifactSource.getToolCallId();
            StringBuilder fullContentBuilder = new StringBuilder();
            List<CodeInterpreterResponse.FileInfo> finalFileInfo = new ArrayList<>();
            AtomicReference<CodeInterpreterResponse> latestResponseRef =
                    new AtomicReference<>(CodeInterpreterResponse.builder()
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
                    if (codeResponse == null) {
                        return;
                    }
                    latestResponseRef.set(codeResponse);

                    // 过程区只累加 data（Python 侧已写入任务/思考/代码/输出 Markdown）；
                    // 纯 code 事件只用于产物 fileInfo，避免与 data 中的代码块重复。
                    String chunkText = codeResponse.getData() == null
                            ? ""
                            : String.valueOf(codeResponse.getData());
                    if (StringUtils.isNotBlank(chunkText)) {
                        fullContentBuilder.append(chunkText);
                        if (!chunkText.endsWith("\n")) {
                            fullContentBuilder.append("\n");
                        }
                    }

                    if (Objects.nonNull(codeResponse.getFileInfo()) && !codeResponse.getFileInfo().isEmpty()) {
                        finalFileInfo.clear();
                        finalFileInfo.addAll(codeResponse.getFileInfo());
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

                    codeResponse.setToolCallId(toolCallId);
                    if (Boolean.TRUE.equals(codeResponse.getIsFinal())) {
                        String conclusion = StringUtils.firstNonBlank(
                                codeResponse.getCodeOutput(),
                                codeResponse.getContent(),
                                chunkText);
                        if (StringUtils.isNotBlank(conclusion)
                                && fullContentBuilder.indexOf(conclusion) < 0) {
                            fullContentBuilder.append("\n").append(conclusion).append("\n");
                        }
                        String full = fullContentBuilder.toString();
                        codeResponse.setData(full);
                        if (StringUtils.isBlank(codeResponse.getCodeOutput())) {
                            codeResponse.setCodeOutput(full);
                        }
                        if (!finalFileInfo.isEmpty()) {
                            codeResponse.setFileInfo(finalFileInfo);
                        }
                        agentContext.getPrinter().send(messageId, "code",
                                codeResponse, digitalEmployee, true);
                    } else if (StringUtils.isNotBlank(chunkText)) {
                        // 中间过程统一用 data 推 Markdown 增量；无 data 的 code/file 事件不刷 UI
                        CodeInterpreterResponse progress = CodeInterpreterResponse.builder()
                                .requestsId(codeResponse.getRequestsId())
                                .data(chunkText)
                                .code(codeResponse.getCode())
                                .fileInfo(codeResponse.getFileInfo())
                                .isFinal(false)
                                .toolCallId(toolCallId)
                                .build();
                        agentContext.getPrinter().send(messageId, "code",
                                progress, digitalEmployee, false);
                    }
                }

                @Override
                public void onClosed() {
                    CodeInterpreterResponse codeResponse = latestResponseRef.get();
                    if (codeResponse.getFileInfo() == null && !finalFileInfo.isEmpty()) {
                        codeResponse.setFileInfo(finalFileInfo);
                    }
                    String full = fullContentBuilder.toString();
                    if (StringUtils.isBlank(full)) {
                        full = StringUtils.defaultString(codeResponse.getCodeOutput());
                    }
                    String display = appendArtifactUrls(full, codeResponse.getFileInfo());
                    if (StringUtils.isNotBlank(display) && !Boolean.TRUE.equals(codeResponse.getIsFinal())) {
                        codeResponse.setData(display);
                        codeResponse.setCodeOutput(StringUtils.firstNonBlank(codeResponse.getCodeOutput(), display));
                        codeResponse.setIsFinal(true);
                        codeResponse.setToolCallId(toolCallId);
                        agentContext.getPrinter().send(messageId, "code",
                                codeResponse, digitalEmployee, true);
                    }
                    if (!future.isDone()) {
                        future.complete(buildSuccessPayload(codeResponse, display));
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
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", "code_interpreter");
        data.put("ok", Boolean.TRUE);
        if (codeResponse != null) {
            if (StringUtils.isNotBlank(codeResponse.getCodeOutput())) {
                data.put("codeOutput", codeResponse.getCodeOutput());
            }
            if (StringUtils.isNotBlank(codeResponse.getContent())) {
                data.put("content", codeResponse.getContent());
            }
            if (StringUtils.isNotBlank(codeResponse.getExplain())) {
                data.put("explain", codeResponse.getExplain());
            }
            List<CodeInterpreterResponse.FileInfo> files = codeResponse.getFileInfo();
            if (files != null && !files.isEmpty()) {
                List<Map<String, Object>> produced = new ArrayList<>();
                for (CodeInterpreterResponse.FileInfo info : files) {
                    if (info == null) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("file_name", info.getFileName());
                    String url = StringUtils.firstNonBlank(info.getDomainUrl(), info.getOssUrl());
                    if (StringUtils.isNotBlank(url)) {
                        row.put("url", url);
                    }
                    produced.add(row);
                }
                data.put("produced_files", produced);
                data.put("hint", "Use produced_files.url as image.url for document_generate when needed.");
            }
        }
        if (!data.containsKey("codeOutput") && StringUtils.isNotBlank(displayText)) {
            data.put("codeOutput", displayText);
        }
        return ToolResultPayload.fromData(
                data,
                CodeInterpreterToolOutput.builder()
                        .codeOutput(codeResponse == null ? null : codeResponse.getCodeOutput())
                        .content(codeResponse == null ? null : codeResponse.getContent())
                        .code(codeResponse == null ? null : codeResponse.getCode())
                        .explain(codeResponse == null ? null : codeResponse.getExplain())
                        .fileRefs(ToolFileRefMapper.fromCodeInterpreterFileInfo(codeResponse == null ? null : codeResponse.getFileInfo()))
                        .build()
        );
    }

    /**
     * 流式代码解释器在任务结束后才取得上传 URL，需写入 observation 供后续 document_generate 使用。
     */
    private String appendArtifactUrls(String output, List<CodeInterpreterResponse.FileInfo> files) {
        if (Objects.isNull(files) || files.isEmpty()) {
            return output;
        }
        StringBuilder result = new StringBuilder(StringUtils.defaultString(output));
        boolean appended = false;
        for (CodeInterpreterResponse.FileInfo file : files) {
            if (Objects.isNull(file)) {
                continue;
            }
            String url = StringUtils.firstNonBlank(file.getDomainUrl(), file.getOssUrl());
            if (StringUtils.isBlank(url)) {
                continue;
            }
            if (!appended) {
                result.append("\n后续工具可用产物（图片传给 document_generate 时使用 image.url）：");
                appended = true;
            }
            result.append("\n- fileName:").append(StringUtils.defaultString(file.getFileName()))
                    .append(" url:").append(url);
        }
        return result.toString();
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
