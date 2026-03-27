package org.wwz.ai.domain.agent.reactor.agent.agent;



/**
 * 规划代理 - 创建和管理任务计划的代理
 */

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.Message;
import org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolChoice;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentState;
import org.wwz.ai.domain.agent.reactor.agent.enums.RoleType;
import org.wwz.ai.domain.agent.reactor.agent.llm.LLM;
import org.wwz.ai.domain.agent.reactor.agent.prompt.PlanningPrompt;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.tool.common.PlanningTool;
import org.wwz.ai.domain.agent.reactor.agent.util.FileUtil;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
     * 构造方法：初始化计划智能体的核心配置
     *
     * @param context 智能体上下文：包含用户查询、工具集合、日期信息、SOP提示词、请求ID等核心数据
     */
    public PlanningAgent(AgentContext context) {
        // 1. 设置智能体基础属性
        setName("planning"); // 智能体名称：用于日志/标识
        setDescription("An agent that creates and manages plans to solve tasks"); // 智能体描述

        // 2. 获取Spring上下文及配置（ReactorConfig是业务自定义的配置类，包含大模型、提示词等配置）
        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        ReactorConfig reactorConfig = applicationContext.getBean(ReactorConfig.class);

        // 3. 构建工具提示词：拼接所有可用工具的名称+描述，用于填充提示词模板
        StringBuilder toolPrompt = new StringBuilder();
        for (BaseTool tool : context.getToolCollection().getToolMap().values()) {
            toolPrompt.append(String.format("工具名：%s 工具描述：%s\n", tool.getName(), tool.getDescription()));
        }

        // 4. 加载提示词模板（默认使用"default"键，无则使用PlanningPrompt中的默认值）
        String promptKey = "default";
        String nextPromptKey = "default";

        // 5. 初始化系统提示词：替换模板中的占位符（工具列表、用户查询、日期、SOP提示词）
        setSystemPrompt(reactorConfig.getPlannerSystemPromptMap().getOrDefault(promptKey, PlanningPrompt.SYSTEM_PROMPT)
                .replace("{{tools}}", toolPrompt.toString()) // 替换工具列表占位符
                .replace("{{query}}", context.getQuery())   // 替换用户查询占位符
                .replace("{{date}}", context.getDateInfo()) // 替换日期信息占位符
                .replace("{{sopPrompt}}", context.getSopPrompt())); // 替换SOP（标准作业流程）提示词占位符

        // 6. 初始化下一步提示词：逻辑同系统提示词
        setNextStepPrompt(reactorConfig.getPlannerNextStepPromptMap().getOrDefault(nextPromptKey, PlanningPrompt.NEXT_STEP_PROMPT)
                .replace("{{tools}}", toolPrompt.toString())
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{sopPrompt}}", context.getSopPrompt()));

        // 7. 保存提示词快照：避免后续动态替换{{files}}后丢失原始模板
        setSystemPromptSnapshot(getSystemPrompt());
        setNextStepPromptSnapshot(getNextStepPrompt());

        // 8. 设置智能体运行依赖
        setPrinter(context.printer); // 设置输出器：用于向用户/前端推送执行过程（如plan、task、plan_thought）
        setMaxSteps(reactorConfig.getPlannerMaxSteps()); // 设置最大执行步骤：防止无限循环
        setLlm(new LLM(reactorConfig.getPlannerModelName(), "")); // 初始化大模型实例（指定模型名称）

        // 9. 关联上下文&配置计划更新开关
        setContext(context); // 绑定智能体上下文
        setIsColseUpdate("1".equals(reactorConfig.getPlanningCloseUpdate())); // 从配置读取是否关闭计划更新（1=关闭）

        // 10. 初始化可用工具：将规划工具加入智能体的工具集，并绑定上下文
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
        long startTime = System.currentTimeMillis(); // 记录思考开始时间（可用于性能监控）
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
                planningTool.stepPlan(); // 执行计划的下一步
                return true;
            }
        }

        try {
            // 3. 构造大模型请求的用户消息：确保最后一条消息是用户角色（大模型交互规范）
            if (!getMemory().getLastMessage().getRole().equals(RoleType.USER)) {
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
                    ToolChoice.AUTO, null, context.getIsStream(), 3000
            );

            // 6. 同步获取异步结果（阻塞等待大模型响应）
            LLM.ToolCallResponse response = future.get();
            setToolCalls(response.getToolCalls()); // 保存大模型返回的工具调用列表

            // 7. 非流式场景：推送思考过程到前端/输出器
            if (!context.getIsStream() && response.getContent() != null && !response.getContent().isEmpty()) {
                printer.send("plan_thought", response.getContent());
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
            String result = executeTool(toolCall); // 执行工具（父类ReActAgent的核心方法）
            // 4. 截取结果长度（如果配置了maxObserve）：避免超长结果影响后续处理
            if (maxObserve != null) {
                result = result.substring(0, Math.min(result.length(), maxObserve));
            }
            results.add(result); // 收集工具执行结果

            // 5. 将工具执行结果添加到智能体记忆（区分两种函数调用模式）
            if ("struct_parse".equals(llm.getFunctionCallType())) {
                // 结构化解析模式：追加结果到最后一条消息的内容中
                String content = getMemory().getLastMessage().getContent();
                getMemory().getLastMessage().setContent(content + "\n 工具执行结果为:\n" + result);
            } else { // 标准函数调用模式：创建独立的工具消息添加到记忆
                Message toolMsg = Message.toolMessage(
                        result,          // 工具执行结果
                        toolCall.getId(),// 工具调用ID（关联请求/响应）
                        null             // 扩展参数（暂无）
                );
                getMemory().addMessage(toolMsg);
            }
        }

        // 6. 计划已初始化的场景：处理计划下一步并返回任务
        if (Objects.nonNull(planningTool.getPlan())) {
            if (isColseUpdate) {
                planningTool.stepPlan(); // 关闭更新时，执行计划下一步
            }
            return getNextTask(); // 返回下一步任务
        }

        // 7. 无计划时，返回所有工具执行结果的拼接字符串
        return String.join("\n\n", results);
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
        // 1. 检查计划所有步骤是否都已完成
        boolean allComplete = true;
        for (String status : planningTool.getPlan().getStepStatus()) {
            if (!"completed".equals(status)) { // 存在未完成步骤
                allComplete = false;
                break;
            }
        }

        // 2. 所有步骤完成：标记智能体状态为FINISHED，推送计划结果，返回"finish"
        if (allComplete) {
            setState(AgentState.FINISHED);
            printer.send("plan", planningTool.getPlan()); // 推送完整计划到前端
            return "finish";
        }

        // 3. 存在未完成步骤：处理当前步骤
        if (!planningTool.getPlan().getCurrentStep().isEmpty()) {
            setState(AgentState.FINISHED); // 标记当前计划步骤完成（进入下一轮）
            // 切割当前步骤（<sep>为步骤分隔符）
            String[] currentSteps = planningTool.getPlan().getCurrentStep().split("<sep>");
            printer.send("plan", planningTool.getPlan()); // 推送最新计划状态
            // 逐个推送当前步骤到前端（task类型消息）
            Arrays.stream(currentSteps).forEach(step -> printer.send("task", step));
            return planningTool.getPlan().getCurrentStep(); // 返回当前步骤字符串
        }

        // 4. 无当前步骤时返回空字符串
        return "";
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
            ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
            request = reactorConfig.getPlanPrePrompt() + request;
        }
        // 调用父类ReActAgent的run方法：触发think()→act()的循环执行
        return super.run(request);
    }
}