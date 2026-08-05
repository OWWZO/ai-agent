package org.wwz.ai.domain.agent.runtime.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.artifact.TaskSummaryArtifactProtocol;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactBinding;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactFormatter;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 兼容保留的总结 Agent。
 * <p>
 * 负责把任务历史和 artifact 协议整理成最终摘要；当前 React/PlanSolve 主路径已直接完成终答，
 * 新代码应优先复用 {@link org.wwz.ai.domain.agent.runtime.artifact.TaskSummaryArtifactProtocol}。
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class SummaryAgent extends BaseAgent {
    private String requestId;
    private Integer messageSizeLimit;
    private Double summaryTemperature;
    private static final String LOG_FLAG = "summaryTaskResult";

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
                + "\n\n" + TaskSummaryArtifactProtocol.protocolInstruction();
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
     * 解析LLM响应并处理文件关联（与 React 直出共用 {@link TaskSummaryArtifactProtocol}）。
     */
    private TaskSummaryResult parseLlmResponse(String llmResponse) {
        if (StringUtils.isEmpty(llmResponse)) {
            log.error("requestId: {} pattern matcher failed for response is null", requestId);
            return TaskSummaryResult.builder().taskSummary("").build();
        }
        List<ToolArtifactBinding> bindings = context.getVisibleArtifactBindings();
        if (CollectionUtils.isEmpty(bindings) && llmResponse.contains(ToolArtifactFormatter.ARTIFACT_DELIMITER)) {
            log.warn("requestId: {} no visible bindings found when parsing summary response", requestId);
        }
        return TaskSummaryArtifactProtocol.parse(llmResponse, bindings);
    }

    /**
     * 根据执行消息和用户请求生成结构化总结。
     */
    public TaskSummaryResult summaryTaskResult(List<Message> messages, String query) {
        if (CollectionUtils.isEmpty(messages) || StringUtils.isEmpty(query)) {
            return emptySummary(messages, query);
        }

        try {
            context.markExecutionPosition(getClass().getSimpleName().replace("Agent", "").toLowerCase(), null);
            log.info("requestId: {} summaryTaskResult: messages:{}", requestId, messages.size());
            String formattedPrompt = formatSystemPrompt(formatMessages(messages), query);
            String llmResponse = requestSummary(formattedPrompt);
            log.info("requestId: {} summaryTaskResult: {}", requestId, llmResponse);
            return parseLlmResponse(llmResponse);
        } catch (Exception e) {
            log.error("requestId: {} in summaryTaskResult failed,", requestId, e);
            return TaskSummaryResult.builder().taskSummary("任务执行失败，请联系管理员！").build();
        }
    }

    private TaskSummaryResult emptySummary(List<Message> messages, String query) {
        log.warn("requestId: {} summaryTaskResult messages:{} or query:{} is empty", requestId, messages, query);
        return TaskSummaryResult.builder().taskSummary("").build();
    }

    /**
     * 将执行消息转换为总结提示中的统一文本，并按配置限制单条消息长度。
     */
    private String formatMessages(List<Message> messages) {
        StringBuilder summaryContext = new StringBuilder();
        for (Message message : messages) {
            String content = message.getContent();
            if (content != null && content.length() > getMessageSizeLimit()) {
                log.info("requestId: {} message truncate,{}", requestId, message);
                content = content.substring(0, getMessageSizeLimit());
            }
            summaryContext.append(String.format("role:%s content:%s\n", message.getRole(), content));
        }
        return summaryContext.toString();
    }

    /**
     * 调用总结模型；流式总结临时切换为 agent_stream，结束后恢复原始消息类型。
     */
    private String requestSummary(String formattedPrompt) {
        Message systemMessage = createSystemMessage(formattedPrompt);
        Message summaryInstruction = createSummaryInstructionMessage();
        boolean enableSummaryStreamPush = Boolean.TRUE.equals(context.getIsStream());
        String previousStreamMessageType = context.getStreamMessageType();
        if (enableSummaryStreamPush) {
            context.setStreamMessageType("agent_stream");
        }
        try {
            CompletableFuture<String> summaryFuture = getLlm().ask(
                    context,
                    Collections.singletonList(summaryInstruction),
                    Collections.singletonList(systemMessage),
                    true,
                    getSummaryTemperature());
            return awaitFuture(summaryFuture);
        } finally {
            if (enableSummaryStreamPush) {
                context.setStreamMessageType(previousStreamMessageType);
            }
        }
    }

}
