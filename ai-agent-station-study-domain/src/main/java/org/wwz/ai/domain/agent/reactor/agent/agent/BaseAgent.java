package org.wwz.ai.domain.agent.reactor.agent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.artifact.ToolArtifactFormatter;
import org.wwz.ai.domain.agent.reactor.agent.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.dto.Memory;
import org.wwz.ai.domain.agent.reactor.agent.dto.Message;
import org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentState;
import org.wwz.ai.domain.agent.reactor.agent.enums.RoleType;
import org.wwz.ai.domain.agent.reactor.agent.llm.LLM;
import org.wwz.ai.domain.agent.reactor.agent.printer.Printer;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.reactor.agent.util.ThreadUtil;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationFinishRecord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * 所有 Agent 的抽象基类。
 * 固定执行主循环，并统一承接记忆管理、工具执行和执行账本接入。
 */
@Slf4j
@Data
@Accessors(chain = true)
public abstract class BaseAgent {

    /** Agent 名称 */
    private String name;
    /** Agent 描述 */
    private String description;
    /** 系统提示词 */
    private String systemPrompt;
    /** 下一步提示词 */
    private String nextStepPrompt;
    /** 当前 Agent 可用工具集合 */
    public ToolCollection availableTools = new ToolCollection();
    /** Agent 记忆 */
    private Memory memory = new Memory();
    /** LLM 门面 */
    protected LLM llm;
    /** Agent 上下文 */
    protected AgentContext context;

    /** 当前状态 */
    private AgentState state = AgentState.IDLE;
    /** 最大步数 */
    private int maxSteps = 10;
    /** 当前步号 */
    private int currentStep = 0;
    /** 重复阈值，暂未启用 */
    private int duplicateThreshold = 2;

    /** 输出器 */
    Printer printer;

    /** 数字员工提示词 */
    private String digitalEmployeePrompt;

    /**
     * 子类定义单步执行逻辑。
     */
    public abstract String step();

    /**
     * Agent 主循环。
     */
    public String run(String query) {
        setState(AgentState.IDLE);

        if (query != null && !query.isEmpty()) {
            updateMemory(RoleType.USER, query, null);
        }

        List<String> results = new ArrayList<>();
        try {
            while (currentStep < maxSteps && state != AgentState.FINISHED) {
                currentStep++;
                if (context != null) {
                    // 每步进入前都刷新一次当前位置，供 LLM / tool 账本读取。
                    context.markExecutionPosition(getName(), currentStep);
                }
                log.info("{} {} Executing step {}/{}", context.getRequestId(), getName(), currentStep, maxSteps);
                results.add(step());
            }

            if (currentStep >= maxSteps) {
                currentStep = 0;
                state = AgentState.IDLE;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
        } catch (Exception e) {
            state = AgentState.ERROR;
            throw e;
        }

        return results.isEmpty() ? "No steps executed" : results.get(results.size() - 1);
    }

    /**
     * 追加记忆消息。
     */
    public void updateMemory(RoleType role, String content, String base64Image, Object... args) {
        Message message;
        switch (role) {
            case USER:
                message = Message.userMessage(content, base64Image);
                break;
            case SYSTEM:
                message = Message.systemMessage(content, base64Image);
                break;
            case ASSISTANT:
                message = Message.assistantMessage(content, base64Image);
                break;
            case TOOL:
                message = Message.toolMessage(content, (String) args[0], base64Image);
                break;
            default:
                throw new IllegalArgumentException("Unsupported role type: " + role);
        }
        memory.addMessage(message);
    }

    /**
     * 预装历史消息，避免共享同一份可变列表。
     */
    protected void preloadMemory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        memory.addMessages(new ArrayList<>(messages));
    }

    /**
     * 注入会话历史摘要。
     */
    protected String injectHistoryDialogue(String promptTemplate, String historyDialogue) {
        String normalizedTemplate = promptTemplate == null ? "" : promptTemplate;
        String normalizedHistory = historyDialogue == null ? "" : historyDialogue;
        if (normalizedTemplate.contains("{{history_dialogue}}")) {
            return normalizedTemplate.replace("{{history_dialogue}}", normalizedHistory);
        }
        if (normalizedHistory.isBlank()) {
            return normalizedTemplate;
        }
        return normalizedTemplate + "\n\n## 用户历史对话信息\n<history_dialogue>\n"
                + normalizedHistory
                + "\n</history_dialogue>";
    }

    /**
     * 从工具集合构建工具提示词。
     */
    protected String buildToolPrompt(ToolCollection tools) {
        if (tools == null || tools.getToolMap() == null || tools.getToolMap().isEmpty()) {
            return "";
        }
        StringBuilder toolPrompt = new StringBuilder();
        for (BaseTool tool : tools.getToolMap().values()) {
            toolPrompt.append(String.format("工具名：%s 工具描述：%s\n", tool.getName(), tool.getDescription()));
        }
        return toolPrompt.toString();
    }

    /**
     * 初始化系统提示词与下一步提示词。
     */
    protected void initializePrompts(Map<String, String> systemPromptMap,
                                     Map<String, String> nextStepPromptMap,
                                     String defaultSystemPrompt,
                                     String defaultNextStepPrompt,
                                     String toolPrompt,
                                     String extraPlaceholder,
                                     String extraValue) {
        String promptKey = "default";
        String nextPromptKey = "default";

        String systemTemplate = systemPromptMap.getOrDefault(promptKey, defaultSystemPrompt)
                .replace("{{tools}}", toolPrompt)
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{basePrompt}}", context.getBasePrompt());
        if (extraPlaceholder != null) {
            systemTemplate = systemTemplate.replace(extraPlaceholder, extraValue);
        }
        setSystemPrompt(injectHistoryDialogue(systemTemplate, context.getHistoryDialogue()));

        String nextTemplate = nextStepPromptMap.getOrDefault(nextPromptKey, defaultNextStepPrompt)
                .replace("{{tools}}", toolPrompt)
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{basePrompt}}", context.getBasePrompt());
        if (extraPlaceholder != null) {
            nextTemplate = nextTemplate.replace(extraPlaceholder, extraValue);
        }
        setNextStepPrompt(injectHistoryDialogue(nextTemplate, context.getHistoryDialogue()));
    }

    /**
     * 为单次工具结果追加当前 toolCall 的文件摘要。
     */
    protected String attachToolArtifactSummary(String result, String toolCallId) {
        if (context == null || StringUtils.isBlank(toolCallId)) {
            return result;
        }
        return ToolArtifactFormatter.appendToolArtifactSummary(
                result,
                context.getArtifactBindingsByToolCallId(toolCallId)
        );
    }

    /**
     * 对外保留原有工具执行契约。
     */
    public String executeTool(ToolCall command) {
        return executeToolInternal(command).getDisplayResult();
    }

    /**
     * 内部工具执行，保留账本需要的状态与结构化输出。
     */
    private ToolExecutionOutcome executeToolInternal(ToolCall command) {
        if (command == null || command.getFunction() == null
                || StringUtils.isBlank(command.getFunction().getName())) {
            return ToolExecutionOutcome.failure("Error: Invalid function call format", null, null, "Invalid function call format");
        }

        String toolName = command.getFunction().getName();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object args = mapper.readValue(normalizeToolPayload(command.getFunction().getArguments()), Object.class);

            ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                    .sessionId(context.getSessionId())
                    .requestId(context.getRequestId())
                    .toolCallId(command.getId())
                    .toolName(toolName)
                    .build();

            String result;
            context.bindCurrentToolArtifactSource(artifactSource);
            try {
                result = availableTools.execute(toolName, args);
            } finally {
                context.clearCurrentToolArtifactSource();
            }

            log.info("{} execute tool: {} {} result {}", context.getRequestId(), toolName, args, result);

            if (result == null) {
                return ToolExecutionOutcome.failure("Tool " + toolName + " Error.", null, null, "Tool returned null");
            }

            String outputJson = tryNormalizeJson(result, mapper);
            String outputText = isStructuredToolOutput(toolName, outputJson) ? null : result;
            return ToolExecutionOutcome.success(result, outputText, outputJson);
        } catch (Exception e) {
            log.error("{} execute tool {} failed ", context.getRequestId(), toolName, e);
            return ToolExecutionOutcome.failure("Tool " + toolName + " Error.", null, null, e.getMessage());
        }
    }

    /**
     * 并发执行多个工具调用。
     */
    public Map<String, String> executeTools(List<ToolCall> commands) {
        Map<String, String> result = new ConcurrentHashMap<>();
        if (commands == null || commands.isEmpty()) {
            return result;
        }

        Map<String, Long> toolInvocationIds = preRegisterToolInvocations(commands);
        if (context != null && context.getAgentRunState() != null) {
            context.getAgentRunState().bindToolInvocationIds(toolInvocationIds);
        }

        CountDownLatch taskCount = ThreadUtil.getCountDownLatch(commands.size());
        for (ToolCall toolCall : commands) {
            ThreadUtil.execute(() -> {
                try {
                    ToolExecutionOutcome outcome = executeToolInternal(toolCall);
                    result.put(toolCall.getId(), outcome.getDisplayResult());
                    finishToolInvocation(toolCall, outcome);
                    recordToolArtifacts(toolCall);
                } finally {
                    taskCount.countDown();
                }
            });
        }

        ThreadUtil.await(taskCount);
        return result;
    }

    /**
     * 主线程预登记工具调用，稳定保存 dispatchIndex 与 toolInvocationId。
     */
    private Map<String, Long> preRegisterToolInvocations(List<ToolCall> commands) {
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null) {
            return Map.of();
        }
        Long llmInvocationId = context.getAgentRunState().getCurrentLlmInvocationId();
        if (llmInvocationId == null) {
            return Map.of();
        }
        List<ToolInvocationBatchStartRecord.Item> items = new ArrayList<>(commands.size());
        int dispatchIndex = 1;
        for (ToolCall command : commands) {
            if (command == null || command.getFunction() == null || StringUtils.isBlank(command.getFunction().getName())) {
                continue;
            }
            items.add(ToolInvocationBatchStartRecord.Item.builder()
                    .toolCallId(command.getId())
                    .dispatchIndex(dispatchIndex++)
                    .toolName(command.getFunction().getName())
                    .toolProvider(resolveToolProvider(command.getFunction().getName()))
                    .inputJson(normalizeToolPayload(command.getFunction().getArguments()))
                    .startedAt(LocalDateTime.now())
                    .build());
        }
        if (items.isEmpty()) {
            return Map.of();
        }
        return context.getExecutionRecorder().createToolInvocations(ToolInvocationBatchStartRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .llmInvocationId(llmInvocationId)
                .agentName(getName())
                .stepNo(getCurrentStep())
                .items(items)
                .build());
    }

    /**
     * 回写工具终态。
     */
    private void finishToolInvocation(ToolCall command, ToolExecutionOutcome outcome) {
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null || command == null) {
            return;
        }
        Long toolInvocationId = context.getAgentRunState().resolveToolInvocationId(command.getId());
        if (toolInvocationId == null) {
            return;
        }
        context.getExecutionRecorder().finishToolInvocation(ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolInvocationId)
                .requestId(context.getRequestId())
                .toolCallId(command.getId())
                .status(outcome != null && outcome.isSuccess()
                        ? ExecutionLedgerConstants.STATUS_SUCCESS
                        : ExecutionLedgerConstants.STATUS_FAILED)
                .outputText(outcome == null ? null : outcome.getOutputText())
                .outputJson(outcome == null ? null : outcome.getOutputJson())
                .errorMsg(outcome == null ? null : outcome.getErrorMsg())
                .finishedAt(LocalDateTime.now())
                .build());
    }

    /**
     * 收口当前 toolCall 生成的输出文件。
     */
    private void recordToolArtifacts(ToolCall command) {
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null || command == null) {
            return;
        }
        Long toolInvocationId = context.getAgentRunState().resolveToolInvocationId(command.getId());
        if (toolInvocationId == null) {
            return;
        }
        List<ArtifactRecordCommand> artifactCommands = new ArrayList<>();
        for (var binding : context.getArtifactBindingsByToolCallId(command.getId())) {
            if (binding == null || binding.getSource() == null || binding.getFile() == null) {
                continue;
            }
            File file = binding.getFile();
            artifactCommands.add(ArtifactRecordCommand.builder()
                    .runId(context.getAgentRunState().getRunId())
                    .requestId(context.getRequestId())
                    .toolInvocationId(toolInvocationId)
                    .toolCallId(command.getId())
                    .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                    .visibility(binding.isInternalFile()
                            ? ExecutionLedgerConstants.VISIBILITY_INTERNAL
                            : ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                    .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                    .sourceName(binding.getSource().getToolName())
                    .fileName(file.getFileName())
                    .storageKey(resolveStorageKey(file))
                    .downloadUrl(file.getOssUrl())
                    .previewUrl(file.getDomainUrl())
                    .fileSize(file.getFileSize() == null ? null : file.getFileSize().longValue())
                    .metadataJson(buildArtifactMetadata(file))
                    .build());
        }
        if (!artifactCommands.isEmpty()) {
            context.getExecutionRecorder().recordArtifacts(artifactCommands);
        }
    }

    private String normalizeToolPayload(String payload) {
        if (StringUtils.isBlank(payload)) {
            return "{}";
        }
        try {
            return new ObjectMapper().readTree(payload).toString();
        } catch (Exception ignore) {
            return "{}";
        }
    }

    private String tryNormalizeJson(String payload, ObjectMapper mapper) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        try {
            return mapper.readTree(payload).toString();
        } catch (Exception ignore) {
            return null;
        }
    }

    private boolean isStructuredToolOutput(String toolName, String outputJson) {
        return "deep_search".equals(toolName) && StringUtils.isNotBlank(outputJson);
    }

    private String resolveToolProvider(String toolName) {
        if (availableTools == null || StringUtils.isBlank(toolName)) {
            return ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL;
        }
        if (availableTools.getMcpToolMap() != null && availableTools.getMcpToolMap().containsKey(toolName)) {
            return ExecutionLedgerConstants.TOOL_PROVIDER_MCP;
        }
        return ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL;
    }

    private String resolveStorageKey(File file) {
        if (file == null) {
            return "";
        }
        if (StringUtils.isNotBlank(file.getOriginOssUrl())) {
            return file.getOriginOssUrl();
        }
        if (StringUtils.isNotBlank(file.getOssUrl())) {
            return file.getOssUrl();
        }
        if (StringUtils.isNotBlank(file.getOriginDomainUrl())) {
            return file.getOriginDomainUrl();
        }
        if (StringUtils.isNotBlank(file.getDomainUrl())) {
            return file.getDomainUrl();
        }
        return StringUtils.defaultString(file.getFileName());
    }

    private String buildArtifactMetadata(File file) {
        if (file == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(file.getDescription())) {
            metadata.put("description", file.getDescription());
        }
        if (StringUtils.isNotBlank(file.getOriginFileName())) {
            metadata.put("originFileName", file.getOriginFileName());
        }
        if (StringUtils.isNotBlank(file.getOriginDomainUrl())) {
            metadata.put("originDomainUrl", file.getOriginDomainUrl());
        }
        if (metadata.isEmpty()) {
            return null;
        }
        try {
            return new ObjectMapper().writeValueAsString(metadata);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 单次工具执行的内部结果。
     */
    @Data
    @Accessors(chain = true)
    private static class ToolExecutionOutcome {
        private boolean success;
        private String displayResult;
        private String outputText;
        private String outputJson;
        private String errorMsg;

        private static ToolExecutionOutcome success(String displayResult, String outputText, String outputJson) {
            return new ToolExecutionOutcome()
                    .setSuccess(true)
                    .setDisplayResult(displayResult)
                    .setOutputText(outputText)
                    .setOutputJson(outputJson);
        }

        private static ToolExecutionOutcome failure(String displayResult, String outputText, String outputJson, String errorMsg) {
            return new ToolExecutionOutcome()
                    .setSuccess(false)
                    .setDisplayResult(displayResult)
                    .setOutputText(outputText)
                    .setOutputJson(outputJson)
                    .setErrorMsg(errorMsg);
        }
    }

}
