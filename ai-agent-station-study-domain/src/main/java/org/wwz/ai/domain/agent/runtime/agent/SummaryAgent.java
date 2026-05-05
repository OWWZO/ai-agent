package org.wwz.ai.domain.agent.runtime.agent;


import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactBinding;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactFormatter;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class SummaryAgent extends BaseAgent {
    private String requestId;
    private Integer messageSizeLimit;
    private Double summaryTemperature;
    private static final String LOG_FLAG = "summaryTaskResult";
    private static final Pattern ARTIFACT_SPLIT_PATTERN = Pattern.compile(ToolArtifactFormatter.ARTIFACT_KEY_SEPARATOR_REGEX);

    public SummaryAgent(AgentContext context) {
        ReactorRuntimeDependencies runtimeDependencies = requireRuntimeDependencies(context);
        ReactorConfig reactorConfig = runtimeDependencies.requireReactorConfig();
        setSystemPrompt(reactorConfig.getSummarySystemPrompt());

        setContext(context);
        setRequestId(context.getRequestId());
        // 总结阶段允许单独指定模型；未配置时保持原有兼容逻辑。
        setLlm(new LLM(resolveSummaryModelName(reactorConfig), "", runtimeDependencies));
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
        List<ToolArtifactBinding> bindings = context.getVisibleArtifactBindings();
        if (CollectionUtils.isEmpty(bindings)) {
            log.info("requestId: {} no visible artifact bindings found in context", requestId);
            return "";
        }
        log.info("requestId: {} {} artifact bindings:{}", requestId, LOG_FLAG, bindings);
        String result = ToolArtifactFormatter.formatSummaryContext(bindings);

        log.info("requestId: {} generated file info: {}", requestId, result);
        return result;
    }

    // 提取系统提示格式化逻辑
    private String formatSystemPrompt(String taskHistory, String query) {
        String systemPrompt = getSystemPrompt();
        if (systemPrompt == null) {
            log.error("requestId: {} {} systemPrompt is null", requestId, LOG_FLAG);
            throw new IllegalStateException("System prompt is not configured");
        }

        // 替换占位符
        return systemPrompt
                .replace("{{taskHistory}}", taskHistory)
                .replace("{{fileNameDesc}}", createFileInfo())
                .replace("{{query}}", query)
                + "\n\n如果需要返回最终文件，请在 " + ToolArtifactFormatter.ARTIFACT_DELIMITER + " 后仅输出 artifactKey 列表。"
                + "artifactKey 格式必须为 toolCallId" + ToolArtifactFormatter.ARTIFACT_KEY_SEPARATOR + "fileName，多个使用、分隔，禁止只输出 fileName。"
                + "如果没有需要返回的文件，则不要输出 " + ToolArtifactFormatter.ARTIFACT_DELIMITER + " 段落。";
    }

    // 构建总结阶段的 system prompt。
    private Message createSystemMessage(String content) {
        // 总结约束必须以 system role 注入，否则模型对格式和协议的遵循度会显著下降。
        return Message.systemMessage(content, null);
    }

    /**
     * 构建总结阶段的最小 user 指令。
     * 某些 OpenAI 兼容网关不接受“仅 system、无 user”的请求，这里补一条稳定指令做兼容。
     */
    private Message createSummaryInstructionMessage() {
        return Message.userMessage("请基于系统提供的完整上下文，严格按照输出协议生成最终总结。", null);
    }

    /**
     * 解析总结阶段实际使用的模型。
     * 优先使用 summary.model_name，未配置时沿用历史逻辑，避免影响现网链路。
     */
    private String resolveSummaryModelName(ReactorConfig reactorConfig) {
        if (StringUtils.isNotBlank(reactorConfig.getSummaryModelName())) {
            return reactorConfig.getSummaryModelName().trim();
        }
        return context.getAgentType() == 3
                ? reactorConfig.getPlannerModelName()
                : reactorConfig.getReactModelName();
    }

    /**
     * 解析LLM响应并处理文件关联
     */
    private TaskSummaryResult parseLlmResponse(String llmResponse) {
        if (StringUtils.isEmpty(llmResponse)) {
            log.error("requestId: {} pattern matcher failed for response is null", requestId);
            return TaskSummaryResult.builder().taskSummary("").build();
        }

        String[] parts1 = llmResponse.split(Pattern.quote(ToolArtifactFormatter.ARTIFACT_DELIMITER), 2);
        if (parts1.length < 2) {
            return TaskSummaryResult.builder().taskSummary(parts1[0].trim()).build();
        }

        String summary = parts1[0].trim();
        String artifactKeys = parts1[1].trim();

        List<ToolArtifactBinding> bindings = context.getVisibleArtifactBindings();
        if (CollectionUtils.isEmpty(bindings)) {
            log.warn("requestId: {} no visible bindings found when parsing summary response", requestId);
            return TaskSummaryResult.builder().taskSummary(summary).build();
        }

        Map<String, ToolArtifactBinding> keyToBinding = buildArtifactKeyIndex(bindings);
        Map<String, File> selectedFiles = new LinkedHashMap<>();
        for (String item : splitArtifactItems(artifactKeys)) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            for (Map.Entry<String, ToolArtifactBinding> entry : keyToBinding.entrySet()) {
                if (item.contains(entry.getKey())) {
                    log.info("requestId: {} add artifact by key:{} file:{}", requestId, entry.getKey(), entry.getValue().getFile());
                    selectedFiles.putIfAbsent(entry.getKey(), entry.getValue().getFile());
                    break;
                }
            }
        }
        return TaskSummaryResult.builder()
                .taskSummary(summary)
                .files(new ArrayList<>(selectedFiles.values()))
                .build();
    }

    private Map<String, ToolArtifactBinding> buildArtifactKeyIndex(List<ToolArtifactBinding> bindings) {
        Map<String, ToolArtifactBinding> index = new LinkedHashMap<>();
        for (ToolArtifactBinding binding : bindings) {
            String key = ToolArtifactFormatter.buildArtifactKey(binding);
            if (StringUtils.isNotBlank(key)) {
                index.put(key, binding);
            }
        }
        return index;
    }

    private List<String> splitArtifactItems(String artifactKeys) {
        if (StringUtils.isBlank(artifactKeys)) {
            return List.of();
        }
        String[] parts = ARTIFACT_SPLIT_PATTERN.split(artifactKeys);
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
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

        // 1. 参数校验：消息列表为空 或 用户查询为空时，返回空总结结果（避免空指针/无意义的LLM调用）
        if (CollectionUtils.isEmpty(messages) || StringUtils.isEmpty(query)) {
            log.warn("requestId: {}  summaryTaskResult messages:{}  or query:{} is empty", requestId, messages, query);
            // 构建空总结结果（taskSummary为空字符串），保证返回值非null
            return TaskSummaryResult.builder().taskSummary("").build();
        }

        try {
            // 总结阶段不走 BaseAgent.run，需要手动标记当前位置，保证 LLM 账本可识别为 summary 调用。
            context.markExecutionPosition(getClass().getSimpleName().replace("Agent", "").toLowerCase(), null);
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

            // 消息拼入提示词占位
            String formattedPrompt = formatSystemPrompt(sb.toString(), query);
            //构建总结阶段的 system prompt。
            Message systemMessage = createSystemMessage(formattedPrompt);
            /**
             * 构建总结阶段的最小 user 指令。
             * 某些 OpenAI 兼容网关不接受“仅 system、无 user”的请求，这里补一条稳定指令做兼容。
             */
            Message summaryInstruction = createSummaryInstructionMessage();

            // 4. 异步调用LLM生成总结：总结阶段使用独立 system prompt 与独立温度配置。
            boolean enableSummaryStreamPush = Boolean.TRUE.equals(context.getIsStream());
            String previousStreamMessageType = context.getStreamMessageType();
            if (enableSummaryStreamPush) {
                context.setStreamMessageType("agent_stream");
            }

            //TODO:优化
            String llmResponse;
            try {
                CompletableFuture<String> summaryFuture = getLlm().ask(
                        context,
                        Collections.singletonList(summaryInstruction),
                        Collections.singletonList(systemMessage),
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

    private ReactorRuntimeDependencies requireRuntimeDependencies(AgentContext context) {
        if (context == null || context.getRuntimeDependencies() == null) {
            throw new IllegalStateException("SummaryAgent 缺少 ReactorRuntimeDependencies");
        }
        return context.getRuntimeDependencies();
    }
}
