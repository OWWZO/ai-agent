package org.wwz.ai.domain.agent.reactor.agent.tool.common;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.context.ApplicationContext;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.dto.FileRequest;
import org.wwz.ai.domain.agent.reactor.agent.dto.FileResponse;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
        return reactorConfig.getFileToolDesc().isEmpty() ? desc : reactorConfig.getFileToolDesc();
    }

    @Override
    public Map<String, Object> toParams() {

        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
        if (!reactorConfig.getFileToolDesc().isEmpty()) {
            return reactorConfig.getFileToolPamras();
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
        try {
            Map<String, Object> params = (Map<String, Object>) input;
            String command = (String) params.getOrDefault("command", "");
            FileRequest fileRequest = JSON.parseObject(JSON.toJSONString(input), FileRequest.class);
            fileRequest.setRequestId(agentContext.getRequestId());
            if ("upload".equals(command)) {
                return uploadFile(fileRequest, true, false);
            } else if ("get".equals(command)) {
                return getFile(fileRequest, true);
            }
        } catch (Exception e) {
            log.error("{} file tool error", agentContext.getRequestId(), e);
        }
        return null;
    }

    // 上传文件的 API 请求方法
    public String uploadFile(FileRequest fileRequest, Boolean isNoticeFe, Boolean isInternalFile) {
        long startTime = System.currentTimeMillis();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60000, TimeUnit.SECONDS) // 设置连接超时时间为 60 秒
                .readTimeout(30000, TimeUnit.SECONDS)    // 设置读取超时时间为 300 秒
                .writeTimeout(30000, TimeUnit.SECONDS)   // 设置写入超时时间为 300 秒
                .callTimeout(30000, TimeUnit.SECONDS)    // 设置调用超时时间为 300 秒
                .build();

        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        ReactorConfig reactorConfig = applicationContext.getBean(ReactorConfig.class);
        MediaType mediaType = MediaType.get("application/json; charset=utf-8");
        String url = reactorConfig.getCodeInterpreterUrl() + "/v1/file_tool/upload_file";

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
            return null;
        }

        // 如果文件名没有任何后缀，但已经非空，则自动补一个 .md 后缀，满足下游服务的约束
        if (!fileRequest.getFileName().contains(".")) {
            fileRequest.setFileName(fileRequest.getFileName() + ".md");
        }
        RequestBody body = RequestBody.create(JSON.toJSONString(fileRequest), mediaType);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        try {
            log.info("{} file tool upload request {}", agentContext.getRequestId(), JSON.toJSONString(fileRequest));
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful() || response.body() == null) {
                log.error("{} upload file faied", agentContext.getRequestId());
                return null;
            }
            String result = response.body().string();
            FileResponse fileResponse = JSON.parseObject(result, FileResponse.class);
            log.info("{} file tool upload response {}", agentContext.getRequestId(), result);
            // 构建前端格式
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("command", "写入文件");
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
            agentContext.getProductFiles().add(file);
            if (isNoticeFe) {
                // 内部文件不通知前端
                agentContext.getPrinter().send("file", resultMap, digitalEmployee);
            }
            if (!isInternalFile) {
                // 非内部文件，参与交付物
                agentContext.getTaskProductFiles().add(file);
            }
            // 返回工具执行结果
            return fileRequest.getFileName() + " 写入到文件链接: " + fileResponse.getOssUrl();

        } catch (Exception e) {
            log.error("{} upload file error", agentContext.getRequestId(), e);
        }
        return null;
    }

    // 获取文件的 API 请求方法
    public String getFile(FileRequest fileRequest, Boolean noticeFe) {
        long startTime = System.currentTimeMillis();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60000, TimeUnit.SECONDS) // 设置连接超时时间为 60 秒
                .readTimeout(300000, TimeUnit.SECONDS)    // 设置读取超时时间为 60 秒
                .writeTimeout(30000, TimeUnit.SECONDS)   // 设置写入超时时间为 60 秒
                .callTimeout(30000, TimeUnit.SECONDS)    // 设置调用超时时间为 60 秒
                .build();

        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        ReactorConfig reactorConfig = applicationContext.getBean(ReactorConfig.class);
        MediaType mediaType = MediaType.get("application/json; charset=utf-8");
        String url = reactorConfig.getCodeInterpreterUrl() + "/v1/file_tool/get_file";
        // 构建请求体
        FileRequest getFileRequest = FileRequest.builder()
                .requestId(agentContext.getRequestId())
                .fileName(fileRequest.getFileName())
                .build();
        // 适配多轮对话
        getFileRequest.setRequestId(agentContext.getSessionId());
        RequestBody body = RequestBody.create(JSON.toJSONString(getFileRequest), mediaType);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        try {
            log.info("{} file tool get request {}", agentContext.getRequestId(), JSON.toJSONString(getFileRequest));
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful() || response.body() == null) {
                String errMessage = "获取文件失败 " + fileRequest.getFileName();
                return errMessage;
            }
            String result = response.body().string();
            FileResponse fileResponse = JSON.parseObject(result, FileResponse.class);
            log.info("{} file tool get response {}", agentContext.getRequestId(), result);
            // 构建前端格式
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("command", "读取文件");
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

                return "文件内容 " + fileContent;
            }
        } catch (Exception e) {

            log.error("{} get file error", agentContext.getRequestId(), e);
        }
        return null;
    }

    private String getUrlContent(String url) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60000, TimeUnit.SECONDS) // 设置连接超时时间为 60 秒
                .readTimeout(60000, TimeUnit.SECONDS)    // 设置读取超时时间为 60 秒
                .writeTimeout(60000, TimeUnit.SECONDS)   // 设置写入超时时间为 60 秒
                .callTimeout(60000, TimeUnit.SECONDS)    // 设置调用超时时间为 60 秒
                .build();
        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                String errMsg = String.format("获取文件失败, 状态码:%d", response.code());
                log.error("{} 获取文件失败 {}", agentContext.getRequestId(), response.code());
                return null;
            }
        } catch (IOException e) {
            log.error("{} 获取文件异常", agentContext.getRequestId(), e);
            return null;
        }
    }
}