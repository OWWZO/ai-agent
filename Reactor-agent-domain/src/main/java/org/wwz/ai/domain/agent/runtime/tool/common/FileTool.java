package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRefMapper;

import java.io.IOException;
import java.util.*;

@Slf4j
@Data

public class FileTool implements BaseTool {
    private AgentContext agentContext;

    @Override
    public String getName() {
        return "file_tool";
    }

    @Override
    public String getDescription() {
        String desc = "这是一个文件工具，可以上传或下载文件";
        ReactorConfig reactorConfig = requireReactorConfig();
        return reactorConfig.getFileToolDesc().isEmpty() ? desc : reactorConfig.getFileToolDesc();
    }

    @Override
    public Map<String, Object> toParams() {

        ReactorConfig reactorConfig = requireReactorConfig();
        if (!reactorConfig.getFileToolParams().isEmpty()) {
            return reactorConfig.getFileToolParams();
        }

        Map<String, Object> command = new HashMap<>();
        command.put("type", "string");
        command.put("description", "文件操作类型：upload、get");

        Map<String, Object> fileName = new HashMap<>();
        fileName.put("type", "string");
        fileName.put("description", "文件名。纯文本内容请优先使用 Markdown 保存，文件名必须带后缀，例如：`xxx.md`（不要省略后缀，也不要使用 .txt）");

        Map<String, Object> fileDesc = new HashMap<>();
        fileDesc.put("type", "string");
        fileDesc.put("description", "文件描述，20字左右，upload时必填");

        Map<String, Object> fileContent = new HashMap<>();
        fileContent.put("type", "string");
        fileContent.put("description", "文件内容，upload时必填");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("command", command);
        properties.put("filename", fileName);
        properties.put("description", fileDesc);
        properties.put("content", fileContent);
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("command", "filename"));

        return parameters;
    }

    @Override
    public Object execute(Object input) {
        String command = "";
        try {
            Map<String, Object> params = (Map<String, Object>) input;
            command = (String) params.getOrDefault("command", "");
            FileRequest fileRequest = JSON.parseObject(JSON.toJSONString(input), FileRequest.class);
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
            fileRequest.setRequestId(agentContext.getRequestId());
            if ("upload".equals(command)) {
                return uploadFilePayload(fileRequest, true, false, artifactSource);
            } else if ("get".equals(command)) {
                return getFilePayload(fileRequest, true);
            }
        } catch (Exception e) {
            log.error("{} file tool error, input:{}", agentContext.getRequestId(), JSON.toJSONString(input), e);
            return buildFailurePayload(command, null, "file_tool 执行失败：" + e.getMessage());
        }
        return buildFailurePayload(command, null, "file_tool 执行失败：不支持的 command。");
    }

    // 上传文件的 API 请求方法
    public String uploadFile(FileRequest fileRequest, Boolean isNoticeFe, Boolean isInternalFile) {
        ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
        ToolResultPayload payload = uploadFilePayload(fileRequest, isNoticeFe, isInternalFile, artifactSource);
        return payload == null ? null : payload.getToolResult();
    }

    public String uploadFile(FileRequest fileRequest,
                             Boolean isNoticeFe,
                             Boolean isInternalFile,
                             ToolArtifactSource artifactSource) {
        ToolResultPayload payload = uploadFilePayload(fileRequest, isNoticeFe, isInternalFile, artifactSource);
        return payload == null ? null : payload.getToolResult();
    }

    private ToolResultPayload uploadFilePayload(FileRequest fileRequest,
                                                Boolean isNoticeFe,
                                                Boolean isInternalFile,
                                                ToolArtifactSource artifactSource) {
        ReactorConfig reactorConfig = requireReactorConfig();
        FileArtifactPort fileArtifactPort = requireFileArtifactPort();

        // 构建请求体 多轮对话替换requestId为sessionId
        fileRequest.setRequestId(agentContext.getSessionId());
        // 清理文件名中的特殊字符
        fileRequest.setFileName(StringUtil.removeSpecialChars(fileRequest.getFileName()));

        // 如果清洗后文件名为空，但有内容/描述，则自动生成一个合理的 Markdown 文件名，避免无效调用反复失败
        if ((fileRequest.getFileName() == null || fileRequest.getFileName().isEmpty())
                && ((fileRequest.getContent() != null && !fileRequest.getContent().isEmpty())
                || (fileRequest.getDescription() != null && !fileRequest.getDescription().isEmpty()))) {
            String baseName = Optional.ofNullable(fileRequest.getDescription())
                    .filter(desc -> !desc.isEmpty())
                    .orElse("autogen_file");
            // 只保留英文数字和下划线，避免再次被 removeSpecialChars 清空
            baseName = baseName.replaceAll("[^a-zA-Z0-9_]", "");
            if (baseName.isEmpty()) {
                baseName = "autogen_file";
            }
            fileRequest.setFileName(baseName + ".md");
        }

        // 再做一次 trim
        if (fileRequest.getFileName() != null) {
            fileRequest.setFileName(fileRequest.getFileName().trim());
        }

        if (fileRequest.getFileName() == null || fileRequest.getFileName().isEmpty()) {
            String errorMessage = "上传文件失败 文件名为空";

            log.error("{} {}", agentContext.getRequestId(), errorMessage);
            return buildFailurePayload("upload", fileRequest.getFileName(), errorMessage);
        }

        // 如果文件名没有任何后缀，但已经非空，则自动补一个 .md 后缀，满足下游服务的约束
        if (!fileRequest.getFileName().contains(".")) {
            fileRequest.setFileName(fileRequest.getFileName() + ".md");
        }
        try {
            log.info("{} file tool upload request {}", agentContext.getRequestId(), JSON.toJSONString(fileRequest));
            FileResponse fileResponse = fileArtifactPort.upload(reactorConfig.getCodeInterpreterUrl(), fileRequest);
            if (fileResponse == null) {
                return buildFailurePayload("upload", fileRequest.getFileName(), "上传文件失败 " + fileRequest.getFileName());
            }
            log.info("{} file tool upload response {}", agentContext.getRequestId(), JSON.toJSONString(fileResponse));
            // 构建前端格式
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("command", "写入文件");
            if (artifactSource != null) {
                resultMap.put("toolCallId", artifactSource.getToolCallId());
                resultMap.put("toolName", artifactSource.getToolName());
            }
            List<CodeInterpreterResponse.FileInfo> fileInfo = new ArrayList<>();
            fileInfo.add(CodeInterpreterResponse.FileInfo.builder()
                    .fileName(fileRequest.getFileName())
                    .ossUrl(fileResponse.getOssUrl())
                    .domainUrl(fileResponse.getDomainUrl())
                    .fileSize(fileResponse.getFileSize())
                    .build());
            resultMap.put("fileInfo", fileInfo);
            // 获取数字人
            String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
            log.info("requestId:{} task:{} toolName:{} digitalEmployee:{}", agentContext.getRequestId(),
                    agentContext.getToolCollection().getCurrentTask(), getName(), digitalEmployee);
            // 添加文件到上下文
            File file = File.builder()
                    .ossUrl(fileResponse.getOssUrl())
                    .domainUrl(fileResponse.getDomainUrl())
                    .fileName(fileRequest.getFileName())
                    .fileSize(fileResponse.getFileSize())
                    .description(fileRequest.getDescription())
                    .isInternalFile(isInternalFile)
                    .build();
            agentContext.registerGeneratedArtifact(artifactSource, file);
            if (isNoticeFe) {
                // 内部文件不通知前端
                agentContext.getPrinter().send("file", resultMap, digitalEmployee);
            }
            // 返回工具执行结果
            String toolResult = fileRequest.getFileName() + " 写入到文件链接: " + fileResponse.getOssUrl();
            return ToolResultPayload.structured(
                    toolResult,
                    toolResult,
                    FileToolOutput.builder()
                            .command("upload")
                            .primaryFileName(fileRequest.getFileName())
                            .previewUrl(fileResponse.getDomainUrl())
                            .downloadUrl(fileResponse.getOssUrl())
                            .fileRefs(ToolFileRefMapper.fromCodeInterpreterFileInfo(fileInfo))
                            .build()
            );

        } catch (Exception e) {
            log.error("{} upload file error", agentContext.getRequestId(), e);
            return buildFailurePayload("upload", fileRequest.getFileName(), "上传文件失败 " + fileRequest.getFileName());
        }
    }

    // 获取文件的 API 请求方法
    public String getFile(FileRequest fileRequest, Boolean noticeFe) {
        ToolResultPayload payload = getFilePayload(fileRequest, noticeFe);
        return payload == null ? null : payload.getToolResult();
    }

    private ToolResultPayload getFilePayload(FileRequest fileRequest, Boolean noticeFe) {
        ReactorConfig reactorConfig = requireReactorConfig();
        FileArtifactPort fileArtifactPort = requireFileArtifactPort();
        // 构建请求体
        FileRequest getFileRequest = FileRequest.builder()
                .requestId(agentContext.getRequestId())
                .fileName(fileRequest.getFileName())
                .build();
        // 适配多轮对话
        getFileRequest.setRequestId(agentContext.getSessionId());
        try {
            log.info("{} file tool get request {}", agentContext.getRequestId(), JSON.toJSONString(getFileRequest));
            FileResponse fileResponse = fileArtifactPort.get(reactorConfig.getCodeInterpreterUrl(), getFileRequest);
            if (fileResponse == null) {
                String errMessage = "获取文件失败 " + fileRequest.getFileName();
                return buildFailurePayload("get", fileRequest.getFileName(), errMessage);
            }
            log.info("{} file tool get response {}", agentContext.getRequestId(), JSON.toJSONString(fileResponse));
            // 构建前端格式
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("command", "读取文件");
            ToolArtifactSource artifactSource = agentContext.getCurrentToolArtifactSource();
            if (artifactSource != null) {
                resultMap.put("toolCallId", artifactSource.getToolCallId());
                resultMap.put("toolName", artifactSource.getToolName());
            }
            List<CodeInterpreterResponse.FileInfo> fileInfo = new ArrayList<>();
            fileInfo.add(CodeInterpreterResponse.FileInfo.builder()
                    .fileName(fileRequest.getFileName())
                    .ossUrl(fileResponse.getOssUrl())
                    .domainUrl(fileResponse.getDomainUrl())
                    .fileSize(fileResponse.getFileSize())
                    .build());
            resultMap.put("fileInfo", fileInfo);
            // 获取数字人
            String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
            log.info("requestId:{} task:{} toolName:{} digitalEmployee:{}", agentContext.getRequestId(),
                    agentContext.getToolCollection().getCurrentTask(), getName(), digitalEmployee);
            // 通知前端
            if (noticeFe) {
                agentContext.getPrinter().send("file", resultMap, digitalEmployee);
            }
            // 返回工具执行结果
            String fileContent = getUrlContent(fileResponse.getOssUrl());
            if (Objects.nonNull(fileContent)) {
                if (fileContent.length() > reactorConfig.getFileToolContentTruncateLen()) {
                    fileContent = fileContent.substring(0, reactorConfig.getFileToolContentTruncateLen());
                }
                String toolResult = "文件内容 " + fileContent;
                return ToolResultPayload.structured(
                        toolResult,
                        toolResult,
                        FileToolOutput.builder()
                                .command("get")
                                .primaryFileName(fileRequest.getFileName())
                                .previewUrl(fileResponse.getDomainUrl())
                                .downloadUrl(fileResponse.getOssUrl())
                                .fileRefs(ToolFileRefMapper.fromCodeInterpreterFileInfo(fileInfo))
                                .build()
                );
            }
        } catch (Exception e) {

            log.error("{} get file error", agentContext.getRequestId(), e);
            return buildFailurePayload("get", fileRequest.getFileName(), "获取文件失败 " + fileRequest.getFileName());
        }
        return buildFailurePayload("get", fileRequest.getFileName(), "获取文件失败 " + fileRequest.getFileName());
    }

    private String getUrlContent(String url) {
        try {
            return requireFileArtifactPort().readText(url, 60L);
        } catch (IOException e) {
            log.error("{} 获取文件异常", agentContext.getRequestId(), e);
            return null;
        }
    }

    /**
     * file_tool 失败时也返回最小 typed output，保证独立输出表能落到可解释终态。
     */
    private ToolResultPayload buildFailurePayload(String command, String fileName, String message) {
        return ToolResultPayload.failure(
                message,
                message,
                FileToolOutput.builder()
                        .command(command)
                        .primaryFileName(fileName)
                        .build(),
                message
        );
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("FileTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    private FileArtifactPort requireFileArtifactPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("FileTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireFileArtifactPort();
    }
}
