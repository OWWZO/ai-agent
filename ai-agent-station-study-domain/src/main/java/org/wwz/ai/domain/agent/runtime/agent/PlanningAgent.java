package org.wwz.ai.domain.agent.runtime.agent;



/**
 * 规划代理 - 创建和管理任务计划的代理
 */

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolChoice;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.prompt.PlanningPrompt;
import org.wwz.ai.domain.agent.runtime.tool.common.PlanningTool;
import org.wwz.ai.domain.agent.runtime.util.FileUtil;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.time.LocalDateTime;

/**
 * 计划型智能体（PlanningAgent）
 * 继承自 ReActAgent（ReAct 范式智能体，核心是"思考-行动"循环），专注于创建和管理执行计划来解决复杂任务
 * 核心能力：
 * 1. 基于用户查询、工具列表、文件信息生成执行计划
 * 2. 支持计划的动态更新/关闭更新（按需执行固定计划）
 * 3. 调用规划工具（PlanningTool）管理计划的步骤执行、状态跟踪
 * 4. 集成LLM大模型完成思考过程，调用工具完成行动过程
 *
 * @author （可补充作者信息）
 * @date （可补充日期）
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanningAgent extends ReActAgent {

    private static final String STEP_STATUS_COMPLETED = "completed";
    private static final String PLANNING_TOOL_NAME = "planning";

    /**
     * 大模型返回的工具调用列表（记录需要执行的工具及参数）
     */
    private List<ToolCall> toolCalls;

    /**
     * 工具执行结果的最大截取长度（避免结果过长导致内存/传输问题）
     */
    private Integer maxObserve;

    /**
     * 核心计划工具实例：负责计划的创建、步骤管理、状态更新
     */
    private PlanningTool planningTool = new PlanningTool();

    /**
     * 是否关闭计划动态更新：
     * - true：使用固定计划，仅按步骤执行，不重新生成/更新计划
     * - false：每次思考阶段重新生成/更新计划
     */
    private Boolean isColseUpdate;

    /**
     * 系统提示词快照：保存初始化后的原始系统提示词（避免动态替换{{files}}后丢失原始模板）
     */
    private String systemPromptSnapshot;

    /**
     * 下一步提示词快照：保存初始化后的原始下一步提示词（作用同systemPromptSnapshot）
     */
    private String nextStepPromptSnapshot;

    /**
     * 计划唯一标识：用于关联当前智能体处理的计划ID（可用于追踪、缓存等）
     */
    private String planId;

    /**
     * 记录最近一次已经下发给执行器的 currentStep。
     * 普通 replan 自动推进后，只允许同一 currentStep 被 dispatch 一次，避免外层循环重复执行同一任务。
     */
    private String lastDispatchedTask;

    /**
     * 当前 planner round 标识。
     * 约束为 toolInvocationId，供同一轮 thought / plan / task 统一复用。
     */
    private String currentPlannerRoundId;

    /**
     * 构造方法：初始化计划智能体的核心配置
     *
     * @param context 智能体上下文：包含用户查询、工具集合、日期信息、SOP提示词、请求ID等核心数据
     */
    public PlanningAgent(AgentContext context) {
        // 1. 设置智能体基础属性
        setName("planning"); // 智能体名称：用于日志/标识
        setDescription("An agent that creates and manages plans to solve tasks"); // 智能体描述

        // 2. 获取显式注入的运行时配置（ReactorConfig是业务自定义的配置类，包含大模型、提示词等配置）
        ReactorRuntimeDependencies runtimeDependencies = requireRuntimeDependencies(context);
        ReactorConfig reactorConfig = runtimeDependencies.requireReactorConfig();
        setContext(context); // 提前绑定上下文，供基类公共提示词初始化逻辑复用

        // 3. 构建工具提示词：拼接所有可用工具的名称+描述，用于填充提示词模板
        String toolPrompt = buildToolPrompt(context.getToolCollection());
        initializePromptsWithHistoryOnlyInSystem(
                reactorConfig.getPlannerSystemPromptMap(),
                reactorConfig.getPlannerNextStepPromptMap(),
                PlanningPrompt.SYSTEM_PROMPT,
                PlanningPrompt.NEXT_STEP_PROMPT,
                toolPrompt,
                "{{sopPrompt}}",
                context.getSopPrompt());

        // 4. 保存提示词快照：避免后续动态替换{{files}}后丢失原始模板
        setSystemPromptSnapshot(getSystemPrompt());
        setNextStepPromptSnapshot(getNextStepPrompt());

        // 5. 设置智能体运行依赖
        setPrinter(context.printer); // 设置输出器：用于向用户/前端推送执行过程（如plan、task、plan_thought）
        setMaxSteps(reactorConfig.getPlannerMaxSteps()); // 设置最大执行步骤：防止无限循环
        setLlm(new LLM(reactorConfig.getPlannerModelName(), "", runtimeDependencies)); // 初始化大模型实例（指定模型名称）

        // 6. 关联上下文&配置计划更新开关
        setIsColseUpdate("1".equals(reactorConfig.getPlanningCloseUpdate())); // 从配置读取是否关闭计划更新（1=关闭）
        planningTool.setCloseUpdateMode(getIsColseUpdate());

        // 7. 初始化可用工具：将规划工具加入智能体的工具集，并绑定上下文
        availableTools.addTool(planningTool);
        planningTool.setAgentContext(context);
    }

    /**
     * 重写思考（think）方法：智能体的核心思考逻辑
     * 核心流程：
     * 1. 加载文件信息并更新提示词
     * 2. 处理"关闭计划更新"的特殊场景
     * 3. 构造大模型请求，获取工具调用指令
     * 4. 处理大模型响应，记录日志&更新记忆
     *
     * @return 思考是否成功（固定返回true，异常仅日志记录不阻断流程）
     */
    @Override
    public boolean think() {
        // 1. 格式化产品文件信息：将上下文的产品文件转为字符串，填充到提示词中（false表示不展示文件完整路径）
        String filesStr = FileUtil.formatFileInfo(context.getProductFiles(), false);
        // 更新系统提示词：替换{{files}}占位符（使用快照避免叠加替换）
        setSystemPrompt(getSystemPromptSnapshot().replace("{{files}}", filesStr));
        // 更新下一步提示词：同理替换{{files}}占位符
        setNextStepPrompt(getNextStepPromptSnapshot().replace("{{files}}", filesStr));
        log.info("{} planer fileStr {}", context.getRequestId(), filesStr); // 日志记录文件信息（用于问题排查）

        // 2. 特殊场景：关闭计划动态更新时，直接执行计划下一步（不调用大模型思考）
        if (isColseUpdate) {
            if (Objects.nonNull(planningTool.getPlan())) { // 计划已初始化
                recordCompatPlanningAdvance(); // 执行计划的下一步，并补齐可回放账本事实
                return true;
            }
        }

        try {
            // 3. 构造大模型请求的用户消息：确保最后一条消息是用户角色（大模型交互规范）
            Message lastMessage = getMemory().getLastMessage();
            // 兼容测试夹具或冷启动场景下记忆尚未预热的情况，避免首次 think 直接空指针。
            if (lastMessage == null || !RoleType.USER.equals(lastMessage.getRole())) {
                Message userMsg = Message.userMessage(getNextStepPrompt(), null); // 构建用户消息（内容为下一步提示词）
                getMemory().addMessage(userMsg); // 添加到智能体记忆（记忆用于多轮对话上下文）
            }

            // 4. 设置流式消息类型：用于前端区分消息类型（plan_thought=计划思考过程）
            context.setStreamMessageType("plan_thought");

            // 5. 异步调用大模型获取工具调用响应：
            // - 参数：上下文、历史消息、系统提示词、可用工具、工具选择策略（AUTO=自动选择）、超时时间300秒
            CompletableFuture<LLM.ToolCallResponse> future = getLlm().askTool(context,
                    getMemory().getMessages(),
                    Message.systemMessage(getSystemPrompt(), null),
                    availableTools,
                    ToolChoice.AUTO, null, context.getIsStream(), false, 3000
            );

            // 6. 同步获取异步结果（阻塞等待大模型响应）
            LLM.ToolCallResponse response = future.get();
            setToolCalls(response.getToolCalls()); // 保存大模型返回的工具调用列表
            bindCurrentPlannerRoundId(response.getToolCalls());

            if (context.getIsStream()
                    && response.getContent() != null
                    && !response.getContent().isEmpty()) {
                printer.sendWithResultMap(
                        resolvePlannerThoughtMessageId(response),
                        "plan_thought",
                        response.getContent(),
                        buildPlannerRoundResultMap(),
                        true
                );
            }

            // 7. 非流式场景：推送思考过程到前端/输出器
            if (!context.getIsStream() && response.getContent() != null && !response.getContent().isEmpty()) {
                printer.sendWithResultMap("plan_thought", response.getContent(), buildPlannerRoundResultMap());
            }

            // 8. 日志记录：思考内容、选择的工具数量（用于监控/排查）
            log.info("{} {}'s thoughts: {}", context.getRequestId(), getName(), response.getContent());
            log.info("{} {} selected {} tools to use", context.getRequestId(), getName(),
                    response.getToolCalls() != null ? response.getToolCalls().size() : 0);

            // 9. 构建助手消息并添加到记忆：
            // - 分支1：有工具调用且不是结构化解析模式 → 构建包含工具调用的助手消息
            // - 分支2：无工具调用/结构化解析模式 → 构建普通助手消息
            Message assistantMsg = response.getToolCalls() != null && !response.getToolCalls().isEmpty() && !"struct_parse".equals(llm.getFunctionCallType()) ?
                    Message.fromToolCalls(response.getContent(), response.getToolCalls()) :
                    Message.assistantMessage(response.getContent(), null);

            getMemory().addMessage(assistantMsg); // 助手消息加入记忆，用于后续多轮对话

        } catch (Exception e) {
            // 异常处理：仅记录日志，不返回false（避免智能体直接终止）
            log.error("{} think error ", context.getRequestId(), e);
        }

        return true; // 思考阶段无论是否异常，均返回true（保证流程继续）
    }

    /**
     * 重写行动（act）方法：智能体的核心执行逻辑
     * 核心流程：
     * 1. 处理"关闭计划更新"的特殊场景
     * 2. 遍历工具调用列表，执行每个工具并收集结果
     * 3. 将工具执行结果更新到智能体记忆
     * 4. 根据计划状态返回下一步任务或执行结果
     *
     * @return 执行结果：下一步任务字符串 / 工具执行结果拼接字符串
     */
    @Override
    public String act() {
        // 1. 特殊场景：关闭计划动态更新时，直接返回下一步任务
        if (isColseUpdate) {
            if (Objects.nonNull(planningTool.getPlan())) {
                return getNextTask();
            }
        }
//
//        if (toolCalls.isEmpty()) {
//            setState(AgentState.FINISHED);
//            return getMemory().getLastMessage().toString();
//        }

        // 2. 初始化工具执行结果列表
        List<String> results = new ArrayList<>();
        long startTime = System.currentTimeMillis(); // 记录行动开始时间（性能监控）

        // 3. 遍历大模型指定的工具调用列表，逐个执行
        for (ToolCall toolCall : toolCalls) {
            ToolExecutionOutcome outcome = executeToolOutcome(toolCall);
            String result = writeToolObservationToMemory(toolCall, outcome);
            results.add(result); // 收集工具执行结果
            if (outcome != null && !outcome.isSuccess()) {
                return result;
            }
        }

        // 6. 计划已初始化的场景：处理计划下一步并返回任务
        if (Objects.nonNull(planningTool.getPlan())) {
            return getNextTask(); // 返回下一步任务
        }

        // 7. 无计划时，返回所有工具执行结果的拼接字符串
        return String.join("\n\n", results);
    }

    @Override
    protected Integer resolveMaxObserveLength() {
        return maxObserve;
    }

    /**
     * 私有方法：获取计划的下一步任务
     * 核心逻辑：
     * 1. 检查计划所有步骤是否完成 → 标记智能体完成并返回"finish"
     * 2. 未完成时，获取当前步骤并推送到前端 → 返回当前步骤字符串
     * 3. 无当前步骤时返回空字符串
     *
     * @return 下一步任务标识："finish"（完成）/ 当前步骤字符串 / 空字符串
     */
    private String getNextTask() {
        if (planningTool.getPlan() == null) {
            throw new IllegalStateException("planning tool returned without a plan");
        }
        // 1. 检查计划所有步骤是否都已完成
        boolean allComplete = planningTool.getPlan().getStepStatus().stream()
                .allMatch(STEP_STATUS_COMPLETED::equals);

        // 2. 所有步骤完成：标记智能体状态为FINISHED，推送计划结果，返回"finish"
        if (allComplete) {
            setState(AgentState.FINISHED);
            lastDispatchedTask = null;
            printer.sendWithResultMap("plan", planningTool.getPlan(), buildPlannerRoundResultMap()); // 推送完整计划到前端
            return "finish";
        }

        // 3. 存在未完成步骤：处理当前步骤
        if (!planningTool.getPlan().getCurrentStep().isEmpty()) {
            String currentStep = planningTool.getPlan().getCurrentStep();
            if (Objects.equals(lastDispatchedTask, currentStep)) {
                throw new IllegalStateException("current task already dispatched; planning must mutate plan before redispatch");
            }
            setState(AgentState.FINISHED); // 标记当前计划步骤完成（进入下一轮）
            // 切割当前步骤（<sep>为步骤分隔符）
            String[] currentSteps = currentStep.split("<sep>");
            printer.sendWithResultMap("plan", planningTool.getPlan(), buildPlannerRoundResultMap()); // 推送最新计划状态
            // 逐个推送当前步骤到前端（task类型消息）
            Arrays.stream(currentSteps).forEach(step -> printer.send("task", step));
            lastDispatchedTask = currentStep;
            return currentStep; // 返回当前步骤字符串
        }

        // 4. 无当前步骤时返回空字符串
        throw new IllegalStateException("plan has unfinished work but no executable current step");
    }

    /**
     * 重写运行（run）方法：智能体的入口方法
     * 核心逻辑：计划未初始化时，添加计划前置提示词，再调用父类run方法（触发思考-行动循环）
     *
     * @param request 用户原始请求字符串
     * @return 父类run方法的返回结果（最终执行结果）
     */
    @Override
    public String run(String request) {
        // 计划未初始化时，拼接计划前置提示词（引导大模型生成合理计划）
        if (Objects.isNull(planningTool.getPlan())) {
            ReactorConfig reactorConfig = requireRuntimeDependencies(context).requireReactorConfig();
            request = reactorConfig.getPlanPrePrompt() + request;
        }
        // 调用父类ReActAgent的run方法：触发think()→act()的循环执行
        return super.run(request);
    }

    /**
     * 规划链路要求 final thought / plan / task.messageType=plan 使用同一 round。
     * 当前唯一稳定的 round key 是 planning tool 的 toolInvocationId。
     */
    private void bindCurrentPlannerRoundId(List<ToolCall> toolCalls) {
        if (context == null || context.getAgentRunState() == null || toolCalls == null || toolCalls.isEmpty()) {
            currentPlannerRoundId = null;
            return;
        }
        for (ToolCall toolCall : toolCalls) {
            if (toolCall == null
                    || toolCall.getFunction() == null
                    || !PLANNING_TOOL_NAME.equals(toolCall.getFunction().getName())) {
                continue;
            }
            Map<String, Long> mapping = ensureToolInvocationIds(List.of(toolCall));
            if (!mapping.isEmpty()) {
                context.getAgentRunState().bindToolInvocationIds(mapping);
            }
            Long toolInvocationId = context.getAgentRunState().resolveToolInvocationId(toolCall.getId());
            currentPlannerRoundId = toolInvocationId == null ? null : String.valueOf(toolInvocationId);
            if (currentPlannerRoundId != null) {
                return;
            }
        }
        currentPlannerRoundId = null;
    }

    private Map<String, Object> buildPlannerRoundResultMap() {
        if (currentPlannerRoundId == null || currentPlannerRoundId.isBlank()) {
            return Map.of();
        }
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("plannerRoundId", currentPlannerRoundId);
        return resultMap;
    }

    /**
     * 规划链路关闭增量透传时，仍需要复用一条稳定 messageId 补发最终 thought。
     */
    private String resolvePlannerThoughtMessageId(LLM.ToolCallResponse response) {
        if (response != null
                && response.getStreamMessageId() != null
                && !response.getStreamMessageId().isBlank()) {
            return response.getStreamMessageId();
        }
        return StringUtil.getUUID();
    }

    private ReactorRuntimeDependencies requireRuntimeDependencies(AgentContext context) {
        if (context == null || context.getRuntimeDependencies() == null) {
            throw new IllegalStateException("PlanningAgent 缺少 ReactorRuntimeDependencies");
        }
        return context.getRuntimeDependencies();
    }

    /**
     * close_update=1 不再重新走 Planner 思考，但历史账本仍需要看到真实的计划推进事实。
     * 这里补一条内部 planning 调用记录，复用既有 tool invocation + structured output 账本体系，
     * 避免历史回放继续只看到首轮 create 快照。
     */
    private void recordCompatPlanningAdvance() {
        PlanningToolOutput output = planningTool.advanceCompatPlanAndCapture();
        if (output == null
                || context == null
                || !context.hasActiveLedgerRun()
                || context.getExecutionRecorder() == null
                || context.getAgentRunState() == null) {
            return;
        }

        Long llmInvocationId = context.getExecutionRecorder().createLlmInvocation(LlmInvocationStartRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .invocationSeq(context.getAgentRunState().nextInvocationSeq())
                .agentName(getName())
                .stepNo(getCurrentStep())
                .callKind(ExecutionLedgerConstants.CALL_KIND_ASK_TOOL)
                .streaming(false)
                .modelName(getLlm() == null ? null : getLlm().getModel())
                .startedAt(LocalDateTime.now())
                .build());
        if (llmInvocationId == null) {
            return;
        }

        context.getAgentRunState().bindCurrentLlmInvocationId(llmInvocationId);
        String toolCallId = buildCompatPlanningToolCallId(getCurrentStep());
        Map<String, Long> mapping = context.getExecutionRecorder().createToolInvocations(ToolInvocationBatchStartRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .llmInvocationId(llmInvocationId)
                .agentName(getName())
                .stepNo(getCurrentStep())
                .items(List.of(ToolInvocationBatchStartRecord.Item.builder()
                        .toolCallId(toolCallId)
                        .dispatchIndex(1)
                        .toolName(PLANNING_TOOL_NAME)
                        .toolProvider(ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL)
                        .inputJson(buildCompatPlanningInputJson(output))
                        .startedAt(LocalDateTime.now())
                        .build()))
                .build());
        context.getAgentRunState().bindToolInvocationIds(mapping);

        Long toolInvocationId = context.getAgentRunState().resolveToolInvocationId(toolCallId);
        if (toolInvocationId != null) {
            context.getExecutionRecorder().finishToolInvocation(ToolInvocationFinishRecord.builder()
                    .toolInvocationId(toolInvocationId)
                    .runId(context.getAgentRunState().getRunId())
                    .requestId(context.getRequestId())
                    .sessionId(context.getSessionId())
                    .toolCallId(toolCallId)
                    .toolName(PLANNING_TOOL_NAME)
                    .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                    .llmObservation("兼容顺推已推进计划")
                    .structuredOutput(output)
                    .finishedAt(LocalDateTime.now())
                    .build());
        }

        context.getExecutionRecorder().finishLlmInvocation(LlmInvocationFinishRecord.builder()
                .llmInvocationId(llmInvocationId)
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .responseText(null)
                .toolCallCount(1)
                .finishReason("tool_calls")
                .finishedAt(LocalDateTime.now())
                .build());
        currentPlannerRoundId = null;
    }

    private String buildCompatPlanningToolCallId(int stepNo) {
        return String.format("compat-planning-%s-%d", context.getRequestId(), stepNo);
    }

    private String buildCompatPlanningInputJson(PlanningToolOutput output) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("command", output.getCommand());
        payload.put("step_index", output.getBeforePlan() == null ? null : output.getBeforePlan().getCurrentStepIndex());
        payload.put("step_status", STEP_STATUS_COMPLETED);
        return com.alibaba.fastjson.JSON.toJSONString(payload);
    }
}
