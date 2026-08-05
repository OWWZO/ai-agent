package org.wwz.ai.domain.agent.runtime.agent;

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
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequestRetry;
import org.wwz.ai.domain.agent.runtime.prompt.PlanningPrompt;
import org.wwz.ai.domain.agent.runtime.tool.common.PlanningTool;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 计划型智能体，负责生成计划、推进步骤并记录规划账本事实。
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
        ReactorConfig reactorConfig = AgentBootstrap.configure(this, context, new AgentBootstrap.Profile(
                "planning",
                "An agent that creates and manages plans to solve tasks",
                ReactorConfig::getPlannerSystemPromptMap,
                PlanningPrompt.SYSTEM_PROMPT,
                ReactorConfig::getPlannerModelName,
                ReactorConfig::getPlannerMaxSteps,
                false,
                false
        ));
        setIsColseUpdate("1".equals(reactorConfig.getPlanningCloseUpdate()));
        planningTool.setCloseUpdateMode(getIsColseUpdate());
        availableTools.addTool(planningTool);
        planningTool.setAgentContext(context);
    }

    /**
     * 重写思考（think）方法：智能体的核心思考逻辑
     * 核心流程：
     * 1. 处理"关闭计划更新"的特殊场景
     * 2. 构造大模型请求，获取工具调用指令
     * 3. 处理大模型响应，记录日志并更新记忆
     *
     * @return 思考是否成功（固定返回true，异常仅日志记录不阻断流程）
     */
    @Override
    public boolean think() {
        // 关闭计划更新时不再调用 LLM，而是沿用兼容路径推进本地计划。
        if (isColseUpdate) {
            if (Objects.nonNull(planningTool.getPlan())) {
                recordCompatPlanningAdvance();
                return true;
            }
        }

        try {
            ensureQueryMessage();
            context.setStreamMessageType("plan_thought");

            LLM.ToolCallResponse response = LlmRequestRetry.call(
                    "plan-think:" + context.getRequestId(),
                    () -> awaitFuture(getLlm().askTool(context,
                            getMemory().getMessages(),
                            Message.systemMessage(getSystemPrompt(), null),
                            availableTools,
                            ToolChoice.AUTO, null, context.getIsStream(), false, 3000
                    ))
            );
            setToolCalls(response.getToolCalls());
            bindCurrentPlannerRoundId(response.getToolCalls());

            // 原生 CoT：有则推（与 tool_call 无关）
            if (response.getReasoningContent() != null && !response.getReasoningContent().isBlank()) {
                printer.send(org.wwz.ai.domain.agent.runtime.llm.ReasoningContentExtractor.EVENT_TYPE,
                        response.getReasoningContent());
            }

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

            if (!context.getIsStream() && response.getContent() != null && !response.getContent().isEmpty()) {
                printer.sendWithResultMap("plan_thought", response.getContent(), buildPlannerRoundResultMap());
            }

            log.info("{} {}'s thoughts: {}", context.getRequestId(), getName(), response.getContent());
            log.info("{} {} selected {} tools to use", context.getRequestId(), getName(),
                    response.getToolCalls() != null ? response.getToolCalls().size() : 0);

            appendAssistantMessage(response);

        } catch (Exception e) {
            log.error("{} think error ", context.getRequestId(), e);
        }

        return true;
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
        // 关闭计划更新时直接返回本地计划的下一步。
        if (isColseUpdate) {
            if (Objects.nonNull(planningTool.getPlan())) {
                return getNextTask();
            }
        }
        List<String> results = new ArrayList<>();

        for (ToolCall toolCall : toolCalls) {
            ToolExecutionOutcome outcome = executeToolOutcome(toolCall);
            String result = writeToolObservationToMemory(toolCall, outcome);
            results.add(result);
            if (outcome != null && !outcome.isSuccess()) {
                return result;
            }
        }

        if (Objects.nonNull(planningTool.getPlan())) {
            return getNextTask();
        }

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
        // 计划推进是一个状态机：先判断全量完成，再处理当前步骤，最后把计划
        // 快照和 task 事件分别发送给前端，返回 finish/当前步骤/空值。
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
        // run 只负责在首次进入时补齐规划提示词，实际 think -> act 循环仍交给父类，
        // 避免子类重复实现统一停止条件。
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

    /**
     * close_update=1 不再重新走 Planner 思考，但历史账本仍需要看到真实的计划推进事实。
     * 这里补一条内部 planning 调用记录，复用既有 tool invocation + structured output 账本体系，
     * 避免历史回放继续只看到首轮 create 快照。
     */
    private void recordCompatPlanningAdvance() {
        // 关闭动态更新的兼容路径没有真实 planning tool call，因此补写最小账本事实，
        // 保证历史回放仍能看到计划推进边界。
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
