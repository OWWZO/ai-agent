package org.wwz.ai.domain.agent.runtime.agent;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.memory.ltm.LtmMemoryGuard;
import org.wwz.ai.domain.agent.memory.ltm.LtmPromptGuidance;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactFormatter;
import org.wwz.ai.domain.agent.runtime.dto.Memory;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolCallbackProvider;
import org.wwz.ai.domain.agent.runtime.llm.SessionPromptFreeze;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModePromptInjector;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.prompt.IntentGatedPrompt;
import org.wwz.ai.domain.agent.runtime.prompt.ToolCallPrompt;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;
import org.wwz.ai.domain.agent.runtime.util.FileUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

    /** 不单独向前端发送工具结果的工具；它们通过产物或结构化事件展示结果。 */
    private static final Set<String> TOOLS_WITHOUT_RESULT_EVENT = Set.of(
            "code_interpreter", "report_tool", "document_generate", "slides_generate",
            "excel_generator", "checklist_generate", "template_filler", "document_template",
            "theme_designer", "chart_generator", "file_tool", "deep_search",
            "multimodalagent_tool", "data_analysis", "canvas_publish", "get_html_canvas_guide",
            "get_genui_guide", "list_ui_components", "emit_ui_tree", "emit_ui_patch",
            // workspace 产物走 file 事件，避免再叠一层 tool_result 卡片
            "workspace_write", "workspace_edit");

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

    private final transient ToolExecutionPipeline toolPipeline = new ToolExecutionPipeline(this);

    /**
     * 子类定义单步执行逻辑。
     */
    public abstract String step();

    protected ReactorRuntimeDependencies requireRuntimeDependencies(AgentContext agentContext) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies();
    }

    protected <T> T awaitFuture(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        }
    }

    protected void ensureQueryMessage() {
        if (getMemory().getLastMessage() == null) {
            String seed = context.getQuery() == null ? "" : context.getQuery();
            getMemory().addMessage(Message.userMessage(seed, null));
        }
    }

    protected void appendAssistantMessage(LLM.ToolCallResponse response) {
        Message assistantMessage = response.getToolCalls() != null
                && !response.getToolCalls().isEmpty()
                && !"struct_parse".equals(llm.getFunctionCallType())
                ? Message.fromToolCalls(response.getContent(), response.getReasoningContent(), response.getToolCalls())
                : Message.assistantMessage(response.getContent(), response.getReasoningContent(), null);
        getMemory().addMessage(assistantMessage);
    }

    protected Map<String, Object> parseToolParam(ToolCall command) {
        return toolPipeline.parseToolParam(command);
    }

    protected void sendToolResult(ToolCall command, String toolResult) {
        if (printer == null || command == null || command.getFunction() == null) {
            return;
        }
        String toolName = command.getFunction().getName();
        if (TOOLS_WITHOUT_RESULT_EVENT.contains(toolName)) {
            return;
        }
        printer.send("tool_result", AgentResponse.ToolResult.builder()
                .toolName(toolName)
                .toolParam(parseToolParam(command))
                .toolResult(toolResult)
                .toolCallId(command.getId())
                .build(), null);
    }

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
            // 本轮上传/可用文件挂在 user 侧（不进 system，保护 prompt cache）
            if (context != null && context.getProductFiles() != null && !context.getProductFiles().isEmpty()) {
                String filesBlock = FileUtil.formatAvailableFilesUserBlock(context.getProductFiles());
                if (StringUtils.isNotBlank(filesBlock)) {
                    userContent = filesBlock + "\n\n" + userContent;
                }
            }
            // 深度记忆 prefetch 挂在 user 侧围栏；skip_memory 不注入
            if (context != null
                    && !Boolean.TRUE.equals(context.getSkipMemory())
                    && StringUtils.isNotBlank(context.getLtmMemoryContext())) {
                userContent = context.getLtmMemoryContext().trim() + "\n\n" + userContent;
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
                    PlanModePromptInjector.injectStepReminders(this);
                    //每步 LLM 前再判一次上下文水位（含 tool 后中途）
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
        // Hermes-style tool guidance (WHEN/SKIP) + curated snapshot; skip_memory 不注入
        if (context != null && !Boolean.TRUE.equals(context.getSkipMemory())) {
            var tools = context.getToolCollection();
            boolean memoryToolPresent = tools != null && tools.getTool(MemoryTool.TOOL_NAME) != null;
            boolean sessionSearchPresent = tools != null && tools.getTool(SessionSearchTool.TOOL_NAME) != null;
            String ltmGuidance = LtmPromptGuidance.forLoadedTools(memoryToolPresent, sessionSearchPresent);
            if (StringUtils.isNotBlank(ltmGuidance) && !systemTemplate.contains(LtmPromptGuidance.MEMORY_GUIDANCE)
                    && !systemTemplate.contains(ltmGuidance)) {
                systemTemplate = systemTemplate.trim() + "\n\n" + ltmGuidance.trim() + "\n";
            }
            if (context.getRuntimeDependencies() != null) {
                var ltmManager = context.getRuntimeDependencies().getOptionalLtmManager();
                if (ltmManager != null) {
                    String ltmBlock = ltmManager.buildSystemPrompt();
                    if (StringUtils.isNotBlank(ltmBlock) && !systemTemplate.contains(ltmBlock)) {
                        systemTemplate = systemTemplate.trim() + "\n\n" + ltmBlock.trim() + "\n";
                    }
                }
            }
        }
        systemTemplate = canonicalizeSystemText(systemTemplate);
        // Freeze 仅作同 session 防御缓存；主稳定性来自确定性规范化
        String toolSig = LlmToolCallbackProvider.buildToolSignature(
                context == null ? null : context.getToolCollection());
        String agentSlot = StringUtils.defaultIfBlank(getName(), "agent")
                + "|intent=" + intentPolicy.getCacheKey();
        String sessionId = context == null ? null : context.getSessionId();
        return SessionPromptFreeze.freezeSystem(
                sessionId, agentSlot, toolSig, systemTemplate);
    }

    /**
     * 兼容旧的单参数调用方，统一复用当前稳定 system prompt 规范化逻辑。
     */
    protected String buildStableSystemPrompt(String template) {
        return buildStableSystemPrompt(template, null, null, null);
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
     * 会话级 env（date）预置到 memory 前缀；history 走 workingMemoryMessages preload
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
     * 初始化稳定 system prompt
     * query/date/history 进入 memory messages，不写入 system
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

    protected void initializeSystemPrompt(Map<String, String> systemPromptMap, String defaultSystemPrompt) {
        String toolPrompt = buildToolPrompt(context == null ? null : context.getToolCollection());
        initializePromptsWithHistoryOnlyInSystem(
                systemPromptMap,
                null,
                defaultSystemPrompt,
                null,
                toolPrompt,
                null,
                null);
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

    public String executeTool(ToolCall command) {
        return executeToolOutcome(command).getLlmObservation();
    }

    protected ToolExecutionOutcome executeToolOutcome(ToolCall command) {
        return toolPipeline.executeOne(command);
    }

    public Map<String, String> executeTools(List<ToolCall> commands) {
        Map<String, ToolExecutionOutcome> outcomes = executeToolOutcomes(commands);
        Map<String, String> result = new LinkedHashMap<>(outcomes.size());
        for (Map.Entry<String, ToolExecutionOutcome> entry : outcomes.entrySet()) {
            result.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().getLlmObservation());
        }
        return result;
    }

    protected Map<String, ToolExecutionOutcome> executeToolOutcomes(List<ToolCall> commands) {
        return toolPipeline.executeBatch(commands);
    }

    protected Map<String, Long> ensureToolInvocationIds(List<ToolCall> commands) {
        return toolPipeline.ensureToolInvocationIds(commands);
    }

    protected Map<String, Long> preRegisterToolInvocations(List<ToolCall> commands) {
        return toolPipeline.preRegisterToolInvocations(commands);
    }

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
     * 每次即将调用主模型前，对当前 Memory 做阈值压缩。
     * 成功后同步 memory + context.workingMemoryMessages，保证 export delta 正确。
     */
    protected void compactWorkingMemoryIfNeeded(String phase) {
        if (context == null || memory == null) {
            return;
        }
        // LTM flush/review fork：禁止 mid-run compact，否则会递归 flush 并冲掉对话快照
        if (LtmMemoryGuard.isSideEffectsDisabled(context)) {
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
