package org.wwz.ai.domain.agent.runtime.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactFormatter;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Memory;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.prompt.ToolCallPrompt;
import org.wwz.ai.domain.agent.runtime.prompt.IntentGatedPrompt;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 所有 Agent 的抽象基类。
 * 固定执行主循环，并统一承接记忆管理、工具执行和执行账本接入。
 * <p>
 * 子类只需要实现 {@link #step()}，本类负责保证每一步都经过取消检查、运行位置刷新、
 * 工作记忆压缩和最大步数保护；工具执行则统一经过 observation、账本和产物收口。
 */
@Slf4j
@Data
@Accessors(chain = true)
public abstract class BaseAgent {

    private static final ObjectMapper JSON = new ObjectMapper();

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

        // 多轮 cache 关键：有 working memory 时只 preload+append query，禁止重插 session_env 打乱前缀
        // 首轮：seed env → query；结束时把 env+本轮轨迹一并 persist
                boolean hasWorking = context != null
                && context.getWorkingMemoryMessages() != null
                && !context.getWorkingMemoryMessages().isEmpty();
        if (hasWorking) {
            // 跨轮：用历史整段替换 memory，再 append 本轮 query（严格前缀续写）
            memory.replaceMessages(new ArrayList<>(context.getWorkingMemoryMessages()));
        } else {
            // 首轮：session_env + query
            if (memory != null) {
                memory.clear();
            }
            seedSessionContextMessages();
        }
        if (query != null && !query.isEmpty()) {
            String userContent = query;
            // 深度记忆 prefetch 挂在 user 侧围栏；skip_memory 不注入
            if (context != null
                    && !Boolean.TRUE.equals(context.getSkipMemory())
                    && StringUtils.isNotBlank(context.getLtmMemoryContext())) {
                userContent = context.getLtmMemoryContext().trim() + "\n\n" + query;
            }
            updateMemory(RoleType.USER, userContent, null);
        }

        List<String> results = new ArrayList<>();
        try {
            // 每次循环只推进一个逻辑步骤；step() 可以触发多次 LLM/tool 调用，但不能绕过这里的边界检查。
            while (currentStep < maxSteps && state != AgentState.FINISHED) {
                if (context != null && context.isRunCancelled()) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: User stopped");
                    log.info("{} {} run cancelled reason={}",
                            context.getRequestId(), getName(), context.getRunCancelReason());
                    break;
                }
                currentStep++;
                if (context != null) {
                    // 每步进入前都刷新一次当前位置，供 LLM / tool 账本读取。
                    context.markExecutionPosition(getName(), currentStep);
                    // Plan Mode：sparse/full 提醒 + mid-run Enter 时补 system 指引
                    org.wwz.ai.domain.agent.runtime.planmode.PlanModePromptInjector.injectStepReminders(this);
                    // cc-haha 对齐：每步 LLM 前再判一次上下文水位（含 tool 后中途）
                    compactWorkingMemoryIfNeeded("step");
                }
                log.info("{} {} Executing step {}/{}", context.getRequestId(), getName(), currentStep, maxSteps);
                results.add(step());
            }

            if (currentStep >= maxSteps && state != AgentState.FINISHED) {
                // 达到步数上限时以可识别的终止结果结束本轮，避免调用方误以为仍可继续执行。
                currentStep = 0;
                state = AgentState.IDLE;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
        } catch (Exception e) {
            // 状态先切换为 ERROR，再把异常交给上层处理；账本最终状态由应用编排层统一写回。
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
     * 注入会话历史摘要到 system 模板。
     * @deprecated history 应进入 messages，不再拼入 system（破坏 prompt cache 前缀）。
     */
    @Deprecated
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
     * 组装 cache-friendly system：只保留静态/会话级占位，去掉 query/date/files/history。
     * <p>
     * 纯函数规范化：同输入必得同字节，不依赖 SessionPromptFreeze 内存。
     * tools 只走 API tools[]（忽略 toolPrompt 正文），避免 system 与 schema 双轨。
     */
    protected String buildStableSystemPrompt(String template, String toolPrompt, String extraPlaceholder, String extraValue) {
        String systemTemplate = normalizeSystemSource(template);
        String basePrompt = context == null || context.getBasePrompt() == null
                ? ""
                : context.getBasePrompt().trim();
        // SOP 为会话/请求级静态指引，进入 system 固定段（cache 友好）；query/date/files/history 仍只走 messages
        String sopPrompt = context == null || context.getSopPrompt() == null
                ? ""
                : context.getSopPrompt().trim();
        boolean hasSopPlaceholder = systemTemplate.contains("{{sopPrompt}}")
                || systemTemplate.contains("{{executorSopPrompt}}");
        // tools 只走 API tools[]，不再写入 system 正文
        systemTemplate = systemTemplate
                .replace("{{tools}}", "")
                .replace("{{basePrompt}}", basePrompt)
                .replace("{{sopPrompt}}", sopPrompt)
                .replace("{{executorSopPrompt}}", sopPrompt)
                .replace("{{query}}", "")
                .replace("{{date}}", "")
                .replace("{{files}}", "")
                .replace("{{history_dialogue}}", "");
        if (extraPlaceholder != null && !extraPlaceholder.isEmpty()) {
            systemTemplate = systemTemplate.replace(extraPlaceholder, extraValue == null ? "" : extraValue);
        }
        // 模板无 {{sopPrompt}} 时仍把召回 SOP 并入 system（PlanSolve 主路径需要）
        if (!sopPrompt.isEmpty() && !hasSopPlaceholder && !systemTemplate.contains(sopPrompt)) {
            systemTemplate = systemTemplate.trim() + "\n\n# SOP\n" + sopPrompt + "\n";
        }
        systemTemplate = stripEmptyEnvBlocks(systemTemplate);
        IntentGatedPrompt.Selection intentPolicy = IntentGatedPrompt.select(
                context == null ? null : context.getQuery(),
                context == null ? null : context.getToolCollection());
        systemTemplate = intentPolicy.appendTo(systemTemplate);
        // LTM 策展冻结块：会话开始快照；skip_memory 路径不注入（子代理等）
        if (context != null
                && !Boolean.TRUE.equals(context.getSkipMemory())
                && context.getRuntimeDependencies() != null) {
            var ltmManager = context.getRuntimeDependencies().getOptionalLtmManager();
            if (ltmManager != null) {
                String ltmBlock = ltmManager.buildSystemPrompt();
                if (StringUtils.isNotBlank(ltmBlock) && !systemTemplate.contains(ltmBlock)) {
                    systemTemplate = systemTemplate.trim() + "\n\n" + ltmBlock.trim() + "\n";
                }
            }
        }
        systemTemplate = canonicalizeSystemText(systemTemplate);
        // Freeze 仅作同 session 防御缓存；主稳定性来自确定性规范化
        String toolSig = org.wwz.ai.domain.agent.runtime.llm.LlmToolCallbackProvider.buildToolSignature(
                context == null ? null : context.getToolCollection());
        String agentSlot = StringUtils.defaultIfBlank(getName(), "agent")
                + "|intent=" + intentPolicy.getCacheKey();
        String sessionId = context == null ? null : context.getSessionId();
        return org.wwz.ai.domain.agent.runtime.llm.SessionPromptFreeze.freezeSystem(
                sessionId, agentSlot, toolSig, systemTemplate);
    }

    /**
     * 入参归一：统一换行，去掉 BOM，避免多次构造时源串细微差异。
     */
    protected String normalizeSystemSource(String system) {
        if (system == null || system.isEmpty()) {
            return "";
        }
        String s = system;
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * 清掉被置空的 date/files/history 空标签块与空标题，避免 system 残留空壳。
     */
    protected String stripEmptyEnvBlocks(String system) {
        if (system == null || system.isEmpty()) {
            return "";
        }
        String s = system;
        s = s.replaceAll("(?s)<date>\\s*</date>", "");
        s = s.replaceAll("(?s)<files>\\s*</files>", "");
        s = s.replaceAll("(?s)<file_desc>\\s*</file_desc>", "");
        s = s.replaceAll("(?s)<history_dialogue>\\s*</history_dialogue>", "");
        s = s.replaceAll("(?s)<session_env>\\s*</session_env>", "");
        s = s.replaceAll("(?m)^## 当前日期\\s*$\\n?", "");
        s = s.replaceAll("(?m)^## 可用文件及描述\\s*$\\n?", "");
        s = s.replaceAll("(?m)^## 当前可用的文件名及描述\\s*$\\n?", "");
        s = s.replaceAll("(?m)^## 用户历史对话信息\\s*$\\n?", "");
        return s;
    }

    /**
     * 输出字节规范化：行尾空白、连续空行、统一结尾换行 —— 同逻辑内容必同字节。
     */
    protected String canonicalizeSystemText(String system) {
        if (system == null || system.isEmpty()) {
            return "";
        }
        String[] parts = system.split("\n", -1);
        StringBuilder sb = new StringBuilder(system.length());
        int blankRun = 0;
        for (String line : parts) {
            String trimmedRight = rtrimSpaces(line);
            if (trimmedRight.isEmpty()) {
                blankRun++;
                if (blankRun > 1) {
                    continue;
                }
                sb.append('\n');
            } else {
                blankRun = 0;
                sb.append(trimmedRight).append('\n');
            }
        }
        String out = trimNewlines(sb.toString());
        return out.isEmpty() ? "" : out + "\n";
    }

    private static String rtrimSpaces(String line) {
        int endIdx = line.length();
        while (endIdx > 0) {
            char c = line.charAt(endIdx - 1);
            if (c != ' ' && c != '\t') {
                break;
            }
            endIdx--;
        }
        return line.substring(0, endIdx);
    }

    private static String trimNewlines(String s) {
        int startIdx = 0;
        int endIdx = s.length();
        while (startIdx < endIdx && s.charAt(startIdx) == '\n') {
            startIdx++;
        }
        while (endIdx > startIdx && s.charAt(endIdx - 1) == '\n') {
            endIdx--;
        }
        return s.substring(startIdx, endIdx);
    }



    /**
     * 会话级 env（date）预置到 memory 前缀；history 走 workingMemoryMessages preload，不再拼 historyDialogue 文本。
     */
    protected void seedSessionContextMessages() {
        if (context == null || memory == null) {
            return;
        }
        if (!memory.getMessages().isEmpty()) {
            Message first = memory.getMessages().get(0);
            if (first != null && first.getContent() != null
                    && first.getContent().contains("<session_env>")) {
                return;
            }
        }
        String dateInfo = context.getDateInfo() == null ? "" : context.getDateInfo();
        if (!dateInfo.isBlank()) {
            memory.addMessage(Message.userMessage(
                    "<session_env>\n当前日期：" + dateInfo + "\n</session_env>",
                    null));
        }
    }

    /**
     * 预装跨轮工作记忆（ledger hydrate 的 Message 链）。幂等：已存在非 session_env 消息则跳过。
     */
    protected void preloadWorkingMemoryIfPresent() {
        if (context == null || memory == null) {
            return;
        }
        List<Message> working = context.getWorkingMemoryMessages();
        if (working == null || working.isEmpty()) {
            return;
        }
        // 已有非空 memory 且首条已是历史时跳过（防重复 preload）
        if (!memory.getMessages().isEmpty()) {
            return;
        }
        memory.addMessages(new ArrayList<>(working));
    }





    /**
     * 从工具集合构建工具提示词。
     */
    protected String buildToolPrompt(ToolCollection tools) {
        if (tools == null || tools.getToolMap() == null || tools.getToolMap().isEmpty()) {
            return "";
        }
        // 与 toolCallbacks 一致：按 name 排序，避免 system 内 {{tools}} 文本顺序漂移打爆 cache
        StringBuilder toolPrompt = new StringBuilder();
        tools.getToolMap().values().stream()
                .filter(tool -> tool != null && tool.getName() != null)
                .sorted(java.util.Comparator.comparing(BaseTool::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(tool -> toolPrompt.append(String.format("工具名：%s 工具描述：%s\n",
                        tool.getName(), tool.getDescription())));
        return toolPrompt.toString();
    }

        /**
     * 初始化稳定 system prompt（cache-friendly）。
     * query/date/history 进入 memory messages，不写入 system；nextStep 已禁用。
     */
    protected void initializePromptsWithHistoryOnlyInSystem(Map<String, String> systemPromptMap,
                                                            Map<String, String> nextStepPromptMap,
                                                            String defaultSystemPrompt,
                                                            String defaultNextStepPrompt,
                                                            String toolPrompt,
                                                            String extraPlaceholder,
                                                            String extraValue) {
        Map<String, String> map = systemPromptMap == null ? Map.of() : systemPromptMap;
        String template = ToolCallPrompt.ensureUserFacingReplyContract(
                map.getOrDefault("default", defaultSystemPrompt));
        setSystemPrompt(buildStableSystemPrompt(template, toolPrompt, extraPlaceholder, extraValue));
        setNextStepPrompt(null);
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
     * 子类按需覆写 observation 最大长度。
     * BaseAgent 本身不关心具体配置来源，只负责统一收口规则。
     */
    protected Integer resolveMaxObserveLength() {
        return null;
    }

    /**
     * 统一生成最终 observation。
     * 先做长度裁剪（LeAgent 风格截断提示），再追加当前 toolCall 关联的产物摘要，
     * 确保账本与主智能体看到的内容完全一致；artifact 摘要不参与 maxObserve 裁剪。
     */
    protected String buildFinalLlmObservation(String rawObservation, String toolCallId) {
        String observation = StringUtils.defaultString(rawObservation);
        Integer maxObserve = resolveMaxObserveLength();
        if (maxObserve != null && maxObserve > 0) {
            observation = ToolObservationSerializer.truncateForLlm(observation, maxObserve);
        }
        return attachToolArtifactSummary(observation, toolCallId);
    }

    /**
     * 把工具最终 observation 写回记忆。
     * 无论单工具还是批量工具，都统一走这一条链路，避免不同 Agent 各自拼装结果。
     */
    protected String writeToolObservationToMemory(ToolCall command, ToolExecutionOutcome outcome) {
        String observation = outcome == null ? "" : StringUtils.defaultString(outcome.getLlmObservation());
        if (command == null) {
            return observation;
        }
        if ("struct_parse".equals(llm.getFunctionCallType())) {
            String content = getMemory().getLastMessage().getContent();
            getMemory().getLastMessage().setContent(content + "\n 工具执行结果为:\n" + observation);
            return observation;
        }
        getMemory().addMessage(Message.toolMessage(observation, command.getId(), outcome.getBase64Image()));
        return observation;
    }

    /**
     * 对外保留原有工具执行契约。
     */
    public String executeTool(ToolCall command) {
        return executeToolOutcome(command).getLlmObservation();
    }

    /**
     * 单工具路径的完整执行结果。
     * 包含预登记、执行、observation 收口、账本落库与产物登记。
     */
    protected ToolExecutionOutcome executeToolOutcome(ToolCall command) {
        // 单工具路径也复用完整收口流程：先登记调用，再执行，最后写终态、产物和前端完成事件。
        List<ToolCall> commands = command == null ? List.of() : List.of(command);
        Map<String, Long> toolInvocationIds = ensureToolInvocationIds(commands);
        if (context != null && context.getAgentRunState() != null && !toolInvocationIds.isEmpty()) {
            context.getAgentRunState().bindToolInvocationIds(toolInvocationIds);
        }
        Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
        emitToolCallRunningEvents(commands, dispatchIndexMapping);
        ToolExecutionOutcome outcome = finalizeToolExecutionOutcome(command, executeToolInternal(command));
        finishToolInvocation(command, outcome);
        recordToolArtifacts(command);
        emitToolCallFinishedEvent(command, dispatchIndexMapping.get(command == null ? null : command.getId()), outcome);
        return outcome;
    }

    /**
     * 内部工具执行，保留账本需要的状态与结构化输出。
     */
    private ToolExecutionOutcome executeToolInternal(ToolCall command) {
        if (command == null || command.getFunction() == null
                || StringUtils.isBlank(command.getFunction().getName())) {
            return ToolExecutionOutcome.failure(
                    "Error: Invalid function call format",
                    "Error: Invalid function call format",
                    null,
                    "Invalid function call format"
            );
        }

        String toolName = command.getFunction().getName();
        if (context != null && context.isRunCancelled()) {
            // 用户取消后不再启动工具；返回失败 outcome 让调用链仍能完成账本和 UI 收口。
            return ToolExecutionOutcome.failure(
                    "工具未执行：用户已停止本轮对话",
                    "工具未执行：用户已停止本轮对话",
                    null,
                    "USER_STOP"
            );
        }
        try {
            Object args = parseToolArguments(toolName, command.getFunction().getArguments(), JSON);

            // Plan Mode 工具门禁（对标 cc-haha：plan 期禁写业务文件）
            String planDeny = org.wwz.ai.domain.agent.runtime.planmode.PlanModeToolPolicy.denyReason(
                    context, toolName, args);
            if (planDeny != null) {
                return ToolExecutionOutcome.failure(planDeny, planDeny, null, "PLAN_MODE_DENY");
            }

            ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                    .sessionId(context.getSessionId())
                    .requestId(context.getRequestId())
                    .toolCallId(command.getId())
                    .toolName(toolName)
                    .build();

            Object resultObject;
            context.bindCurrentToolArtifactSource(artifactSource);
            try {
                resultObject = availableTools.execute(toolName, args);
            } finally {
                context.clearCurrentToolArtifactSource();
            }

            log.info("{} execute tool: {} {} result {}", context.getRequestId(), toolName, args, resultObject);

            if (resultObject == null) {
                return ToolExecutionOutcome.failure(
                        "Tool " + toolName + " Error.",
                        "Tool " + toolName + " Error.",
                        null,
                        "Tool returned null"
                );
            }

            ToolResultPayload payload = normalizeToolResultPayload(resultObject);
            String toolResult = StringUtils.defaultString(payload.getToolResult());
            String llmObservation = StringUtils.defaultIfBlank(payload.getLlmObservation(), toolResult);
            if (Boolean.TRUE.equals(payload.getFailed())) {
                return ToolExecutionOutcome.failure(
                        toolResult,
                        llmObservation,
                        payload.getStructuredOutput(),
                        StringUtils.defaultIfBlank(payload.getErrorMsg(), toolResult)
                );
            }
            return ToolExecutionOutcome.success(toolResult, llmObservation, payload.getStructuredOutput(),
                     payload.getBase64Image(), payload.getImageMimeType());
        } catch (Exception e) {
            log.error("{} execute tool {} failed ", context.getRequestId(), toolName, e);
            return ToolExecutionOutcome.failure(
                    "Tool " + toolName + " Error.",
                    "Tool " + toolName + " Error.",
                    null,
                    e.getMessage()
            );
        }
    }

    /**
     * 并发执行多个工具调用。
     */
    public Map<String, String> executeTools(List<ToolCall> commands) {
        Map<String, ToolExecutionOutcome> outcomes = executeToolOutcomes(commands);
        Map<String, String> result = new LinkedHashMap<>(outcomes.size());
        for (Map.Entry<String, ToolExecutionOutcome> entry : outcomes.entrySet()) {
            result.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().getLlmObservation());
        }
        return result;
    }

    /**
     * 并发执行多个工具调用，并返回完整 outcome。
     * 子类可以基于同一份 outcome 同时处理前端展示、记忆写回和账本一致性。
     * <p>
     * Agent 派发走 task 池，普通工具走 tool 池，打断「同池嵌套阻塞」自死锁；
     * allOf 带超时，超时未完成的工具标记 failed，避免 UI 永久 running。
     */
    protected Map<String, ToolExecutionOutcome> executeToolOutcomes(List<ToolCall> commands) {
        Map<String, ToolExecutionOutcome> result = new ConcurrentHashMap<>();
        if (commands == null || commands.isEmpty()) {
            return result;
        }

        Map<String, Integer> dispatchIndexMapping = buildDispatchIndexMapping(commands);
        Map<String, Long> toolInvocationIds = ensureToolInvocationIds(commands);

        if (context != null && context.getAgentRunState() != null) {
            context.getAgentRunState().bindToolInvocationIds(toolInvocationIds);
        }
        emitToolCallRunningEvents(commands, dispatchIndexMapping);

        List<CompletableFuture<Void>> futures = new ArrayList<>(commands.size());
        List<CompletableFuture<?>> executionFutures = new ArrayList<>(commands.size());
        for (ToolCall toolCall : commands) {
            // 子 Agent 派发和普通工具使用不同执行器，避免同一个受限线程池中出现嵌套等待导致自死锁。
            Executor executor = resolveExecutorForTool(toolCall);
            String scene = isAgentDispatchTool(toolCall) ? "subAgentBatch" : "toolBatch";
            CompletableFuture<ToolExecutionOutcome> executionFuture = AgentExecutorSupport
                    .supplyAsync(executor, scene, context,
                            () -> finalizeToolExecutionOutcome(toolCall, executeToolInternal(toolCall)));
            executionFutures.add(executionFuture);
            CompletableFuture<Void> future = executionFuture
                    .handle((outcome, error) -> {
                        if (error != null && unwrapExecutionError(error) instanceof CancellationException) {
                            return null;
                        }
                        ToolExecutionOutcome finalOutcome = outcome;
                        if (error != null) {
                            Throwable root = unwrapExecutionError(error);
                            String msg = root.getMessage() == null
                                    ? root.getClass().getSimpleName()
                                    : root.getMessage();
                            finalOutcome = toolFailureOutcome("Tool execution error: " + msg, msg);
                        }
                        completeToolOutcome(result, toolCall, finalOutcome, dispatchIndexMapping, true);
                        return null;
                    });
            futures.add(future);
        }

        awaitToolBatch(futures, executionFutures);
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId()) || result.containsKey(command.getId())) {
                continue;
            }
            completeToolOutcome(
                    result,
                    command,
                    toolFailureOutcome("工具执行超时，已终止等待", "TOOL_BATCH_TIMEOUT"),
                    dispatchIndexMapping,
                    false);
        }

        // 按模型返回的原始顺序重新组装结果；ConcurrentHashMap 只负责并发写入，不承担展示顺序。
        Map<String, ToolExecutionOutcome> ordered = new LinkedHashMap<>(commands.size());
        for (ToolCall command : commands) {
            if (command != null && StringUtils.isNotBlank(command.getId())) {
                ordered.put(command.getId(), result.get(command.getId()));
            }
        }
        return ordered;
    }

    private void awaitToolBatch(List<CompletableFuture<Void>> futures,
                                List<CompletableFuture<?>> executionFutures) {
        if (futures == null || futures.isEmpty()) {
            return;
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        long timeoutSeconds = resolveToolBatchTimeoutSeconds();
        try {
            if (timeoutSeconds > 0L) {
                all.get(timeoutSeconds, TimeUnit.SECONDS);
            } else {
                all.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} tool batch interrupted, cancelling unfinished tools",
                    context == null ? "-" : context.getRequestId());
            cancelToolExecutions(executionFutures);
        } catch (TimeoutException e) {
            log.error("{} tool batch timed out after {}s",
                    context == null ? "-" : context.getRequestId(), timeoutSeconds);
            cancelToolExecutions(executionFutures);
        } catch (ExecutionException e) {
            Throwable root = unwrapExecutionError(e.getCause());
            if (root instanceof TimeoutException) {
                log.error("{} tool batch timed out after {}s",
                        context == null ? "-" : context.getRequestId(), timeoutSeconds);
                cancelToolExecutions(executionFutures);
            } else {
                log.error("{} tool batch join failed",
                        context == null ? "-" : context.getRequestId(), root);
            }
        }
    }

    /**
     * 批次超时后取消底层执行任务；FutureTask 会中断正在运行的工具线程。
     */
    private void cancelToolExecutions(List<CompletableFuture<?>> executionFutures) {
        if (executionFutures == null) {
            return;
        }
        for (CompletableFuture<?> executionFuture : executionFutures) {
            if (executionFuture != null && !executionFuture.isDone()) {
                executionFuture.cancel(true);
            }
        }
    }

    /**
     * 登记工具 outcome，并按需落账本/产物/终态事件。
     * 超时补完路径不调用 recordToolArtifacts，与历史行为一致。
     */
    private void completeToolOutcome(Map<String, ToolExecutionOutcome> result,
                                     ToolCall toolCall,
                                     ToolExecutionOutcome outcome,
                                     Map<String, Integer> dispatchIndexMapping,
                                     boolean recordArtifacts) {
        if (toolCall == null || StringUtils.isBlank(toolCall.getId()) || outcome == null) {
            return;
        }
        // 幂等保护：防止超时补写与成功回调同时执行导致重复落账
        if (result.containsKey(toolCall.getId())) {
            return;
        }
        result.put(toolCall.getId(), outcome);
        finishToolInvocation(toolCall, outcome);
        if (recordArtifacts) {
            recordToolArtifacts(toolCall);
        }
        emitToolCallFinishedEvent(toolCall, dispatchIndexMapping.get(toolCall.getId()), outcome);
    }

    private static ToolExecutionOutcome toolFailureOutcome(String message, String errorMsg) {
        return ToolExecutionOutcome.failure(message, message, null, errorMsg);
    }

    private static Throwable unwrapExecutionError(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? error : current;
    }

    private boolean isAgentDispatchTool(ToolCall toolCall) {
        return toolCall != null
                && toolCall.getFunction() != null
                && org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool.NAME
                .equals(toolCall.getFunction().getName());
    }

    /**
     * Agent 派发占用 task 池；普通工具占用 tool 池，避免嵌套 Agent 与子工具同池自死锁。
     */
    private Executor resolveExecutorForTool(ToolCall toolCall) {
        if (isAgentDispatchTool(toolCall)) {
            return resolveTaskExecutor();
        }
        return resolveToolExecutor();
    }

    private long resolveToolBatchTimeoutSeconds() {
        if (context == null || context.getRuntimeDependencies() == null) {
            return 600L;
        }
        return context.getRuntimeDependencies().resolveToolBatchTimeoutSeconds();
    }

    /**
     * 为同一批 tool call 固定 dispatchIndex，保证实时占位、终态更新与账本顺序一致。
     */
    private Map<String, Integer> buildDispatchIndexMapping(List<ToolCall> commands) {
        Map<String, Integer> dispatchIndexMapping = new LinkedHashMap<>();
        if (commands == null || commands.isEmpty()) {
            return dispatchIndexMapping;
        }
        int dispatchIndex = 1;
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId())) {
                continue;
            }
            dispatchIndexMapping.put(command.getId(), dispatchIndex++);
        }
        return dispatchIndexMapping;
    }

    /**
     * 工具真正开始执行前先推送一条 tool_call 占位事件，
     * 让前端能够立刻展示“正在调用哪个工具”，避免长耗时工具阶段看起来像卡住。
     */
    private void emitToolCallRunningEvents(List<ToolCall> commands, Map<String, Integer> dispatchIndexMapping) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (ToolCall command : commands) {
            emitToolCallEvent(command, dispatchIndexMapping.get(command == null ? null : command.getId()), "running", false, null);
        }
    }

    /**
     * 工具完成后回写同一 messageId 的终态，前端可直接原位覆盖 running 卡片。
     */
    private void emitToolCallFinishedEvent(ToolCall command,
                                           Integer dispatchIndex,
                                           ToolExecutionOutcome outcome) {
        String status = outcome != null && outcome.isSuccess() ? "success" : "failed";
        emitToolCallEvent(command, dispatchIndex, status, true, outcome);
    }

    private void emitToolCallEvent(ToolCall command,
                                   Integer dispatchIndex,
                                   String status,
                                   boolean isFinal,
                                   ToolExecutionOutcome outcome) {
        if (printer == null || command == null || command.getFunction() == null) {
            return;
        }
        String toolCallId = command.getId();
        String toolName = command.getFunction().getName();
        if (StringUtils.isBlank(toolCallId) || StringUtils.isBlank(toolName)) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", "tool_call");
        payload.put("status", status);
        payload.put("toolName", toolName);
        payload.put("toolCallId", toolCallId);
        payload.put("toolProvider", resolveToolProvider(toolName));
        if (dispatchIndex != null) {
            payload.put("dispatchIndex", dispatchIndex);
        }

        Long toolInvocationId = context == null || context.getAgentRunState() == null
                ? null
                : context.getAgentRunState().resolveToolInvocationId(toolCallId);
        if (toolInvocationId != null) {
            payload.put("toolInvocationId", String.valueOf(toolInvocationId));
        }

        Object input = parseToolCallInput(command.getFunction().getArguments());
        if (input != null) {
            payload.put("input", input);
        }

        payload.put("summary", buildToolCallSummary(toolName, status));
        payload.put("isFinal", isFinal);

        if (outcome != null && StringUtils.isNotBlank(outcome.getErrorMsg())) {
            payload.put("errorMsg", outcome.getErrorMsg());
        }

        printer.send(toolCallId, "tool_call", payload, isFinal);
    }

    private Object parseToolCallInput(String arguments) {
        String normalizedPayload = normalizeToolPayload(arguments);
        try {
            return JSON.readValue(normalizedPayload, Object.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * Parse tool arguments; salvage truncated canvas_publish / emit_ui_tree JSON when normal parse fails.
     */
    private Object parseToolArguments(String toolName, String arguments, ObjectMapper mapper) throws Exception {
        String normalizedPayload = normalizeToolPayload(arguments);
        try {
            return mapper.readValue(normalizedPayload, Object.class);
        } catch (Exception parseError) {
            Map<String, Object> salvaged = trySalvageToolArguments(toolName, normalizedPayload, parseError);
            if (salvaged != null) {
                return salvaged;
            }
            throw parseError;
        }
    }

    private Map<String, Object> trySalvageToolArguments(String toolName,
                                                        String normalizedPayload,
                                                        Exception parseError) {
        if (org.wwz.ai.domain.agent.runtime.tool.canvas.CanvasPublishArgSalvage.isCanvasPublish(toolName)) {
            Map<String, Object> salvaged = org.wwz.ai.domain.agent.runtime.tool.canvas.CanvasPublishArgSalvage
                    .parseOrSalvage(normalizedPayload);
            if (salvaged != null && !salvaged.isEmpty()) {
                log.warn("{} canvas_publish args salvaged after parse failure: {}",
                        context == null ? "-" : context.getRequestId(),
                        parseError.getMessage());
                return salvaged;
            }
        }
        if (org.wwz.ai.domain.agent.runtime.tool.canvas.EmitUiTreeArgSalvage.isEmitUiTree(toolName)) {
            Map<String, Object> salvaged = org.wwz.ai.domain.agent.runtime.tool.canvas.EmitUiTreeArgSalvage
                    .parseOrSalvage(normalizedPayload);
            if (salvaged != null && !salvaged.isEmpty()) {
                log.warn("{} emit_ui_tree args salvaged after parse failure: {}",
                        context == null ? "-" : context.getRequestId(),
                        parseError.getMessage());
                return salvaged;
            }
        }
        return null;
    }

    private String buildToolCallSummary(String toolName, String status) {
        if ("success".equals(status)) {
            return toolName + " 调用完成";
        }
        if ("failed".equals(status)) {
            return toolName + " 调用失败";
        }
        return "正在调用 " + toolName;
    }

    /**
     * 主线程预登记工具调用，稳定保存 dispatchIndex 与 toolInvocationId。
     */
    protected Map<String, Long> ensureToolInvocationIds(List<ToolCall> commands) {
        if (context == null || context.getAgentRunState() == null || commands == null || commands.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> existing = new LinkedHashMap<>();
        List<ToolCall> missingCommands = new ArrayList<>();
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId())) {
                continue;
            }
            Long existingInvocationId = context.getAgentRunState().resolveToolInvocationId(command.getId());
            if (existingInvocationId != null) {
                existing.put(command.getId(), existingInvocationId);
            } else {
                missingCommands.add(command);
            }
        }
        if (missingCommands.isEmpty()) {
            return existing;
        }
        Map<String, Long> created = preRegisterToolInvocations(missingCommands);
        if (existing.isEmpty()) {
            return created;
        }
        if (created.isEmpty()) {
            return existing;
        }
        existing.putAll(created);
        return existing;
    }

    protected Map<String, Long> preRegisterToolInvocations(List<ToolCall> commands) {
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
                    .parentToolCallId(context.getParentToolUseId())
                    .subAgentId(context.getSubAgentId())
                    .subAgentType(context.getSubAgentType())
                    .subAgentDescription(context.getSubAgentDescription())
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
        // 先写入调用开始事实，再启动工具执行，确保快速完成或并发执行也不会丢失 tool invocation。
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
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .sessionId(context.getSessionId())
                .toolCallId(command.getId())
                .toolName(command.getFunction().getName())
                .status(outcome != null && outcome.isSuccess()
                        ? ExecutionLedgerConstants.STATUS_SUCCESS
                        : ExecutionLedgerConstants.STATUS_FAILED)
                .llmObservation(outcome == null ? null : outcome.getLlmObservation())
                .structuredOutput(outcome == null ? null : outcome.getStructuredOutput())
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
            return JSON.readTree(payload).toString();
        } catch (Exception ignore) {
            return "{}";
        }
    }

    private ToolResultPayload normalizeToolResultPayload(Object rawResult) {
        if (rawResult instanceof ToolResultPayload payload) {
            // 已预填 llmObservation 的 rich tool 保持原样；仅 llmData 时走 LeAgent serialize_for_llm。
            boolean failed = Boolean.TRUE.equals(payload.getFailed());
            String toolResult = StringUtils.defaultString(payload.getToolResult());
            String llmObservation = payload.getLlmObservation();
            if (StringUtils.isBlank(llmObservation)) {
                if (payload.getLlmData() != null || failed) {
                    llmObservation = ToolObservationSerializer.serializePayload(payload);
                } else {
                    llmObservation = toolResult;
                }
            }
            if (StringUtils.isBlank(toolResult)) {
                toolResult = llmObservation;
            }
            return ToolResultPayload.builder()
                    .toolResult(toolResult)
                    .llmObservation(llmObservation)
                    .llmData(payload.getLlmData())
                    .structuredOutput(payload.getStructuredOutput())
                    .base64Image(payload.getBase64Image())
                    .imageMimeType(payload.getImageMimeType())
                    .failed(failed)
                    .errorMsg(payload.getErrorMsg())
                    .build();
        }
        if (rawResult instanceof String textResult) {
            // 对齐 LeAgent：成功 + str data → 原样
            return ToolResultPayload.builder()
                    .toolResult(textResult)
                    .llmObservation(ToolObservationSerializer.serializeSuccess(textResult))
                    .llmData(textResult)
                    .failed(Boolean.FALSE)
                    .build();
        }
        // 对齐 LeAgent：成功 + 非 str → json.dumps(data)
        String serialized = ToolObservationSerializer.serializeSuccess(rawResult);
        return ToolResultPayload.builder()
                .toolResult(serialized)
                .llmObservation(serialized)
                .llmData(rawResult)
                .failed(Boolean.FALSE)
                .build();
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

    /**
     * 工具批量执行优先走运行时托管执行器；缺少上下文时回退当前线程，兼容单测夹具。
     */
    private Executor resolveToolExecutor() {
        return resolveExecutor(deps -> deps.requireToolExecutor());
    }

    private Executor resolveTaskExecutor() {
        return resolveExecutor(deps -> deps.requireTaskExecutor());
    }

    private Executor resolveExecutor(java.util.function.Function<
            org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies, Executor> picker) {
        if (context == null || context.getRuntimeDependencies() == null) {
            return Runnable::run;
        }
        return picker.apply(context.getRuntimeDependencies());
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
            return JSON.writeValueAsString(metadata);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 在工具实际执行完成后统一收口最终 observation。
     * 所有写库与写记忆都必须使用这一份 canonical 结果。
     */
    private ToolExecutionOutcome finalizeToolExecutionOutcome(ToolCall command, ToolExecutionOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        String toolCallId = command == null ? null : command.getId();
        return outcome.setLlmObservation(buildFinalLlmObservation(outcome.getLlmObservation(), toolCallId));
    }

    /**
     * 单次工具执行的内部结果。
     */
    @Data
    @Accessors(chain = true)
    protected static class ToolExecutionOutcome {
        private boolean success;
        private String toolResult;
        private String llmObservation;
        private ToolStructuredOutput structuredOutput;
        private String errorMsg;
        private String base64Image;
        private String imageMimeType;

        private static ToolExecutionOutcome success(String toolResult,
                                                    String llmObservation,
                                                    ToolStructuredOutput structuredOutput,
                                                    String base64Image,
                                                    String imageMimeType) {
            return new ToolExecutionOutcome()
                    .setSuccess(true)
                    .setToolResult(toolResult)
                    .setLlmObservation(llmObservation)
                    .setStructuredOutput(structuredOutput)
                    .setBase64Image(base64Image)
                    .setImageMimeType(imageMimeType);
        }

        private static ToolExecutionOutcome failure(String toolResult,
                                                    String llmObservation,
                                                    ToolStructuredOutput structuredOutput,
                                                    String errorMsg) {
            return new ToolExecutionOutcome()
                    .setSuccess(false)
                    .setToolResult(toolResult)
                    .setLlmObservation(llmObservation)
                    .setStructuredOutput(structuredOutput)
                    .setErrorMsg(errorMsg);
        }
    }

    /**
     * 导出本轮应写入 working_memory 的消息（去掉 session_env 与跨轮 preload 前缀）。
     */
    public List<Message> exportWorkingMemoryDelta() {
        List<Message> all = memory == null ? List.of() : memory.getMessages();
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        // mid-run compact 后 workingMemoryMessages 已同步为新前缀：只 persist 后缀 delta
        int preloadSize = 0;
        if (context != null && context.getWorkingMemoryMessages() != null) {
            preloadSize = context.getWorkingMemoryMessages().size();
        }
        if (preloadSize <= 0) {
            return new ArrayList<>(all);
        }
        if (preloadSize >= all.size()) {
            return List.of();
        }
        // 持久化只写本轮新增后缀，避免把跨轮前缀重复写入 working_memory 投影表。
        return new ArrayList<>(all.subList(preloadSize, all.size()));
    }

    /**
     * 对齐 cc-haha：每次即将调用主模型前，对当前 Memory 做阈值压缩。
     * 成功后同步 memory + context.workingMemoryMessages，保证 export delta 正确。
     */
    protected void compactWorkingMemoryIfNeeded(String phase) {
        if (context == null || memory == null) {
            return;
        }
        if (context.getRuntimeDependencies() == null) {
            return;
        }
        var compaction = context.getRuntimeDependencies().getOptionalSessionContextCompactionService();
        if (compaction == null) {
            return;
        }
        List<Message> current = memory.getMessages();
        if (current == null || current.isEmpty()) {
            return;
        }
        try {
            List<Message> compacted = compaction.applyIfNeededMidRun(
                    context.getSessionId(),
                    context.getRequestId(),
                    current);
            if (compacted == null || compacted == current || compacted.equals(current)) {
                return;
            }
            // 压缩后的列表同时替换 Agent Memory 和上下文快照，否则后续导出 delta 会从旧前缀计算。
            memory.replaceMessages(new ArrayList<>(compacted));
            context.setWorkingMemoryMessages(new ArrayList<>(compacted));
            log.info("{} {} mid-run compact phase={} beforeMsgs={} afterMsgs={}",
                    context.getRequestId(), getName(), phase, current.size(), compacted.size());
        } catch (Exception e) {
            log.warn("{} {} mid-run compact failed phase={}: {}",
                    context.getRequestId(), getName(), phase, e.getMessage());
        }
    }


}
