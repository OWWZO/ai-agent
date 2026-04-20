package org.wwz.ai.domain.agent.reactor.agent.agent;


import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.dto.Message;
import org.wwz.ai.domain.agent.reactor.agent.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.reactor.agent.llm.LLM;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class SummaryAgent extends BaseAgent {
    private String requestId;
    private Integer messageSizeLimit;
    private Double summaryTemperature;
    public static final String logFlag = "summaryTaskResult";

    public SummaryAgent(AgentContext context) {
        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        ReactorConfig reactorConfig = applicationContext.getBean(ReactorConfig.class);
        setSystemPrompt(reactorConfig.getSummarySystemPrompt());

        setContext(context);
        setRequestId(context.getRequestId());
        setLlm(new LLM(context.getAgentType() == 3 ? reactorConfig.getPlannerModelName() : reactorConfig.getReactModelName(), ""));
        setMessageSizeLimit(reactorConfig.getMessageSizeLimit());
        setSummaryTemperature(reactorConfig.getSummaryTemperature());
    }

    /**
     * 执行单个步骤
     */
    public String step() {
        return "";
    }

    // 构造文件信息
    private String createFileInfo() {
        List<File> files = context.getProductFiles();
        if (CollectionUtils.isEmpty(files)) {
            log.info("requestId: {} no files found in context", requestId);
            return "";
        }
        log.info("requestId: {} {} product files:{}", requestId, logFlag, files);
        String result = files.stream()
                .filter(file -> !file.getIsInternalFile()) // 过滤内部文件
                .map(file -> file.getFileName() + " : " + file.getDescription())
                .collect(Collectors.joining("\n"));

        log.info("requestId: {} generated file info: {}", requestId, result);
        return result;
    }

    // 提取系统提示格式化逻辑
    private String formatSystemPrompt(String taskHistory, String query) {
        String systemPrompt = getSystemPrompt();
        if (systemPrompt == null) {
            log.error("requestId: {} {} systemPrompt is null", requestId, logFlag);
            throw new IllegalStateException("System prompt is not configured");
        }

        // 替换占位符
        return systemPrompt
                .replace("{{taskHistory}}", taskHistory)
                .replace("{{fileNameDesc}}", createFileInfo())
                .replace("{{query}}", query);
    }

    // 提取消息创建逻辑
    private Message createSystemMessage(String content) {
        return Message.userMessage(content, null); // 如果需要更复杂的消息构建，可扩展
    }

    /**
     * 解析LLM响应并处理文件关联
     */
    private TaskSummaryResult parseLlmResponse(String llmResponse) {
        if (StringUtils.isEmpty(llmResponse)) {
            log.error("requestId: {} pattern matcher failed for response is null", requestId);
        }

        String[] parts1 = llmResponse.split("\\$\\$\\$");
        if (parts1.length < 2) {
            return TaskSummaryResult.builder().taskSummary(parts1[0].trim()).build();
        }

        String summary = parts1[0].trim();
        String fileNames = parts1[1].trim();

        List<File> files = context.getProductFiles();
        if (!CollectionUtils.isEmpty(files)) {
            Collections.reverse(files);
        } else {
            log.error("requestId: {} llmResponse:{} productFile list is empty", requestId, llmResponse);
            // 文件列表为空，交付物中不显示文件
            return TaskSummaryResult.builder().taskSummary(summary).build();
        }
        List<File> product = new ArrayList<>();
        String[] items = fileNames.split("、");
        for (String item : items) {
            String trimmedItem = item.trim();
            if (StringUtils.isBlank(trimmedItem)) {
                continue;
            }
            for (File file : files) {
                if (item.contains(file.getFileName().trim())) {
                    log.info("requestId: {} add file:{}", requestId, file);
                    product.add(file);
                    break;
                }
            }
        }

        return TaskSummaryResult.builder().taskSummary(summary).files(product).build();
    }


    /**
     * 核心方法：基于Agent执行过程中的消息记录和用户原始查询，调用LLM生成结构化的任务总结结果
     * 整体流程：参数校验 → 消息格式化（含超长内容截断）→ 构建LLM提示词 → 异步调用LLM → 解析LLM响应 → 返回总结结果
     * 异常场景：参数为空/LLM调用失败时返回兜底结果，保证方法不抛出异常
     *
     * @param messages Agent执行过程中产生的所有消息列表（包含用户输入、LLM回复、工具执行结果等）
     * @param query    用户原始查询/任务指令（用于让总结贴合用户核心需求）
     * @return TaskSummaryResult 任务总结结果对象（包含总结文本、产物文件列表等，异常时返回兜底值）
     */
    public TaskSummaryResult summaryTaskResult(List<Message> messages, String query) {
        // 记录方法开始执行时间，便于后续统计耗时（可扩展打印耗时日志）
        long startTime = System.currentTimeMillis();

        // 1. 参数校验：消息列表为空 或 用户查询为空时，返回空总结结果（避免空指针/无意义的LLM调用）
        if (CollectionUtils.isEmpty(messages) || StringUtils.isEmpty(query)) {
            log.warn("requestId: {}  summaryTaskResult messages:{}  or query:{} is empty", requestId, messages, query);
            // 构建空总结结果（taskSummary为空字符串），保证返回值非null
            return TaskSummaryResult.builder().taskSummary("").build();
        }

        try {
            // 2. 格式化执行消息：将消息列表转为LLM可识别的结构化文本（role+content），并处理超长消息
            log.info("requestId: {} summaryTaskResult: messages:{}", requestId, messages.size());
            StringBuilder sb = new StringBuilder();
            // 遍历所有执行消息，拼接为统一格式
            for (Message message : messages) {
                String content = message.getContent();
                // 消息内容超长时截断：避免超出LLM上下文窗口限制，导致调用失败/总结不全
                if (content != null && content.length() > getMessageSizeLimit()) {
                    log.info("requestId: {} message truncate,{}", requestId, message);
                    // 截取前N个字符（getMessageSizeLimit()返回最大允许长度）
                    content = content.substring(0, getMessageSizeLimit());
                }
                // 按「role:角色类型 content:消息内容」格式拼接，换行分隔，便于LLM解析
                sb.append(String.format("role:%s content:%s\n", message.getRole(), content));
            }

            // 3. 构建LLM系统提示词：将格式化的消息和用户查询注入预设模板，生成最终发给LLM的提示词
            String formattedPrompt = formatSystemPrompt(sb.toString(), query);
            // 将格式化后的提示词转为LLM可识别的System角色消息
            Message userMessage = createSystemMessage(formattedPrompt);

            // 4. 异步调用LLM生成总结：使用CompletableFuture异步执行，提升性能（此处get()转为同步等待结果）
            // 参数说明：
            // - context：Agent上下文（含请求ID、LLM配置等）
            // - Collections.singletonList(userMessage)：发给LLM的消息列表（仅系统提示词）
            // - Collections.emptyList()：无图片等多模态内容
            // - false：不启用流式返回
            // - 0.01：LLM温度参数（越低结果越固定，适合总结类场景）
            boolean enableSummaryStreamPush = Boolean.TRUE.equals(context.getIsStream());
            String previousStreamMessageType = context.getStreamMessageType();
            if (enableSummaryStreamPush) {
                context.setStreamMessageType("agent_stream");
            }

            String llmResponse;
            try {
                CompletableFuture<String> summaryFuture = getLlm().ask(
                        context,
                        Collections.singletonList(userMessage),
                        Collections.emptyList(),
                        true,
                        getSummaryTemperature());
                llmResponse = summaryFuture.get();
            } finally {
                if (enableSummaryStreamPush) {
                    context.setStreamMessageType(previousStreamMessageType);
                }
            }
            log.info("requestId: {} summaryTaskResult: {}", requestId, llmResponse);

            // 6. 解析LLM响应：将LLM返回的文本转为结构化的TaskSummaryResult对象（如提取总结文本、文件列表）
            return parseLlmResponse(llmResponse);

        } catch (Exception e) {
            // 异常处理：捕获所有异常（LLM调用失败、解析失败等），返回兜底提示
            log.error("requestId: {} in summaryTaskResult failed,", requestId, e);
            // 构建兜底结果：提示用户任务执行失败，联系管理员，保证方法返回值非null
            return TaskSummaryResult.builder().taskSummary("任务执行失败，请联系管理员！").build();
        }
    }
}
