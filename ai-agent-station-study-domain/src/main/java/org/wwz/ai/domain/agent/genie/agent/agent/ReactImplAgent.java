package org.wwz.ai.domain.agent.genie.agent.agent;


import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.wwz.ai.domain.agent.genie.agent.dto.Message;
import org.wwz.ai.domain.agent.genie.agent.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.genie.agent.dto.tool.ToolChoice;
import org.wwz.ai.domain.agent.genie.agent.enums.AgentState;
import org.wwz.ai.domain.agent.genie.agent.enums.RoleType;
import org.wwz.ai.domain.agent.genie.agent.llm.LLM;
import org.wwz.ai.domain.agent.genie.agent.prompt.ToolCallPrompt;
import org.wwz.ai.domain.agent.genie.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.genie.agent.util.FileUtil;
import org.wwz.ai.domain.agent.genie.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.genie.config.GenieConfig;
import org.wwz.ai.domain.agent.genie.model.response.AgentResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具调用代理 - 处理工具/函数调用的基础代理类
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ReactImplAgent extends ReActAgent {


    // ===================== 核心状态字段 =====================
    /**
     * 大模型决策出的工具调用指令列表
     * 来源：think阶段调用LLM的askTool方法返回，包含待执行的工具名称、参数、调用ID等信息
     * 用途：act阶段根据该列表执行具体工具，是"思考"到"执行"的核心桥梁
     */
    private List<ToolCall> toolCalls;

    /**
     * 工具结果最大截断长度
     * 用途：避免工具返回超长结果（如大文本、大数据集）导致Token超限或处理异常，仅保留前N个字符
     * 取值：由外部配置/业务逻辑设置，null表示不截断
     */
    private Integer maxObserve;

    /**
     * 系统提示词快照（初始值）
     * 设计目的：系统提示词中包含{{files}}等动态占位符，快照保留初始化时的原始值，避免多次替换导致内容错乱
     * 用途：每次think阶段从快照恢复原始提示词，再替换最新的文件内容占位符
     */
    private String systemPromptSnapshot;

    /**
     * 下一步提示词快照（初始值）
     * 设计目的：同systemPromptSnapshot，保留下一步提示词的原始值，保证动态占位符替换的准确性
     */
    private String nextStepPromptSnapshot;

    // ===================== 父类继承字段（关键说明） =====================
    // - name: 智能体名称（固定为"react"）
    // - description: 智能体描述（工具调用能力说明）
    // - systemPrompt: 系统提示词（指导大模型决策的核心指令，包含工具列表、任务规则等）
    // - nextStepPrompt: 下一步提示词（每次思考阶段向大模型发送的决策提示词）
    // - printer: 响应输出器（用于向客户端推送流式/非流式响应，如tool_thought、tool_result）
    // - maxSteps: 最大执行步数（防止智能体无限循环思考/执行）
    // - llm: 大模型实例（用于调用LLM生成工具调用指令）
    // - context: 智能体上下文（包含请求ID、用户查询、工具集合、文件信息、流式标识等核心上下文）
    // - availableTools: 可用工具集合（当前智能体可调用的所有工具）
    // - digitalEmployeePrompt: 数字员工专属提示词（业务定制化指令）
    // - memory: 智能体记忆（存储对话历史、工具调用记录、执行结果等，保证上下文连续性）

    /**
     * 构造方法：初始化ReAct智能体核心配置
     * 核心逻辑：加载配置→构建提示词→初始化核心组件→设置初始状态
     *
     * @param context 智能体上下文（携带请求ID、用户查询、工具集合、文件信息等全量上下文）
     */
    public ReactImplAgent(AgentContext context) {
        // 步骤1：设置智能体基础标识
        setName("react"); // 智能体名称，用于标识不同类型的智能体
        setDescription("an agent that can execute tool calls."); // 智能体能力描述

        // 步骤2：加载Spring上下文和核心配置
        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        GenieConfig genieConfig = applicationContext.getBean(GenieConfig.class); // 全局配置Bean（包含ReAct相关配置）

        // 步骤3：构建工具描述提示词（整合所有可用工具的名称+描述，供大模型决策参考）
        StringBuilder toolPrompt = new StringBuilder();
        for (BaseTool tool : context.getToolCollection().getToolMap().values()) {
            // 格式化工具信息："工具名：xxx 工具描述：xxx\n"，让大模型清晰识别可用工具
            toolPrompt.append(String.format("工具名：%s 工具描述：%s\n", tool.getName(), tool.getDescription()));
        }

        // 步骤4：定义提示词配置键（默认使用"default"，可通过配置扩展多套提示词）
        String promptKey = "default";
        String nextPromptKey = "default";

        // 步骤5：初始化系统提示词（核心指令，指导大模型的决策逻辑）
        // 替换占位符：{{tools}}=工具列表、{{query}}=用户查询、{{date}}=日期信息、{{basePrompt}}=基础提示词
        setSystemPrompt(genieConfig.getReactSystemPromptMap().getOrDefault(promptKey, ToolCallPrompt.SYSTEM_PROMPT)
                .replace("{{tools}}", toolPrompt.toString())
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{basePrompt}}", context.getBasePrompt()));

        // 步骤6：初始化下一步提示词（每次思考阶段发送给大模型的决策提示词）
        // 占位符替换规则同系统提示词，保证提示词包含完整的上下文信息
        setNextStepPrompt(genieConfig.getReactNextStepPromptMap().getOrDefault(nextPromptKey, ToolCallPrompt.NEXT_STEP_PROMPT)
                .replace("{{tools}}", toolPrompt.toString())
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{basePrompt}}", context.getBasePrompt()));

        // 步骤7：保存提示词快照（防止后续动态替换{{files}}导致原始提示词丢失）
        setSystemPromptSnapshot(getSystemPrompt());
        setNextStepPromptSnapshot(getNextStepPrompt());

        // 步骤8：初始化输出器和核心配置
        setPrinter(context.printer); // 响应输出器（推送tool_thought/tool_result给客户端）
        setMaxSteps(genieConfig.getReactMaxSteps()); // 最大执行步数（防止无限循环）
        setLlm(new LLM(genieConfig.getReactModelName(), "")); // 初始化大模型实例（指定ReAct专用模型）
        setContext(context); // 绑定智能体上下文

        // 步骤9：初始化可用工具集合（从上下文加载当前请求可调用的所有工具）
        availableTools = context.getToolCollection();
        // 步骤10：设置数字员工专属提示词（业务定制化指令，如角色设定、回复风格等）
        setDigitalEmployeePrompt(genieConfig.getDigitalEmployeePrompt());
    }

    /**
     * 重写思考方法（ReAct核心：Reason阶段）
     * 核心逻辑：
     * 1. 动态替换提示词中的文件信息占位符；
     * 2. 补充用户消息（保证对话历史的合法性）；
     * 3. 调用大模型生成工具调用指令；
     * 4. 处理大模型响应，更新智能体记忆和工具调用列表；
     * 5. 异常处理：捕获异常并记录，标记智能体为完成状态。
     *
     * @return boolean 思考是否成功：true=成功生成工具调用指令，false=异常失败
     */
    @Override
    public boolean think() {
        // 步骤1：动态替换提示词中的{{files}}占位符（文件信息可能实时更新，需每次思考时替换）
        String filesStr = FileUtil.formatFileInfo(context.getProductFiles(), true); // 格式化产品文件信息（如内容、路径、类型）
        setSystemPrompt(getSystemPromptSnapshot().replace("{{files}}", filesStr)); // 从快照恢复原始提示词，替换文件信息
        setNextStepPrompt(getNextStepPromptSnapshot().replace("{{files}}", filesStr));

        // 步骤2：补充用户消息（保证对话历史的最后一条是用户消息，符合大模型对话规范）
        if (!getMemory().getLastMessage().getRole().equals(RoleType.USER)) {
            // 构建用户消息：内容为下一步提示词，无图片（null）
            Message userMsg = Message.userMessage(getNextStepPrompt(), null);
            getMemory().addMessage(userMsg); // 添加到智能体记忆（对话历史）
        }

        try {
            // 步骤3：设置流式响应类型（标记当前流式消息为"tool_thought"，供前端识别）
            context.setStreamMessageType("tool_thought");

            // 步骤4：调用大模型的工具调用专用接口（askTool），生成工具调用指令
            CompletableFuture<LLM.ToolCallResponse> future = getLlm().askTool(
                    context,                  // 智能体上下文（请求ID、流式标识等）
                    getMemory().getMessages(),// 对话历史（记忆中的所有消息）
                    Message.systemMessage(getSystemPrompt(), null), // 系统提示词消息
                    availableTools,           // 可用工具集合（供大模型选择）
                    ToolChoice.AUTO,          // 工具选择策略：自动（由大模型决策是否调用工具）
                    null,                     // 自定义温度系数（使用LLM实例默认值）
                    context.getIsStream(),    // 是否流式响应（true=流式，false=非流式）
                    300                       // 超时时间（300秒）
            );

            // 步骤5：同步获取大模型响应（阻塞等待，直到返回工具调用结果）
            LLM.ToolCallResponse response = future.get();

            // 步骤6：更新智能体状态：保存大模型决策的工具调用列表
            setToolCalls(response.getToolCalls());

            // 步骤7：处理非流式响应（推送工具思考结果给客户端）
            if (!context.getIsStream() && response.getContent() != null && !response.getContent().isEmpty()) {
                printer.send("tool_thought", response.getContent()); // 输出工具思考内容（如"我需要调用deep_search工具查询xxx"）
            }

            // 步骤8：构建助手消息，添加到智能体记忆（记录大模型的决策结果）
            Message assistantMsg;
            if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()
                    && !"struct_parse".equals(llm.getFunctionCallType())) {
                // 场景1：原生函数调用模式（function_call）+ 有工具调用指令 → 构建工具调用消息
                assistantMsg = Message.fromToolCalls(response.getContent(), response.getToolCalls());
            } else {
                // 场景2：结构化解析模式（struct_parse）或无工具调用指令 → 构建普通助手消息
                assistantMsg = Message.assistantMessage(response.getContent(), null);
            }
            getMemory().addMessage(assistantMsg); // 添加到记忆，保证对话上下文连续

        } catch (Exception e) {
            // 异常处理：记录错误日志，添加异常消息到记忆，标记智能体为完成状态
            log.error("{} react think error", context.getRequestId(), e);
            getMemory().addMessage(Message.assistantMessage(
                    "Error encountered while processing: " + e.getMessage(), null));
            setState(AgentState.FINISHED); // 标记智能体完成（终止后续流程）
            return false; // 思考失败
        }

        return true; // 思考成功
    }

    /**
     * 重写执行方法（ReAct核心：Action阶段）
     * 核心逻辑：
     * 1. 校验工具调用列表：无工具则标记完成，返回最后一条消息内容；
     * 2. 执行工具：调用executeTools执行所有工具，获取执行结果；
     * 3. 处理工具结果：流式推送、截断超长结果、更新智能体记忆；
     * 4. 兼容两种工具调用模式：struct_parse（更新现有消息）、function_call（新增工具消息）；
     * 5. 聚合工具结果：返回所有工具结果的拼接字符串。
     *
     * @return String 所有工具执行结果的聚合字符串（换行分隔）
     */
    @Override
    public String act() {
        // 步骤1：边界条件处理：无工具调用指令 → 标记智能体完成，返回最后一条消息内容
        if (toolCalls.isEmpty()) {
            setState(AgentState.FINISHED);
            return getMemory().getLastMessage().getContent();
        }

        // 步骤2：执行工具调用（核心：调用executeTools方法执行所有工具，返回工具ID→结果的映射）
        Map<String, String> toolResults = executeTools(toolCalls);
        List<String> results = new ArrayList<>(); // 存储所有工具执行结果

        // 步骤3：遍历工具调用指令，处理每个工具的执行结果
        for (ToolCall command : toolCalls) {
            String result = toolResults.get(command.getId()); // 根据工具调用ID获取执行结果

            // 步骤3.1：特殊工具结果不推送（如代码解释器、报表工具等，避免前端展示冗余信息）
            if (!Arrays.asList("code_interpreter", "report_tool", "file_tool", "deep_search", "data_analysis").contains(command.getFunction().getName())) {
                // 推送工具结果到客户端：包含工具名、参数、执行结果
                printer.send("tool_result", AgentResponse.ToolResult.builder()
                        .toolName(command.getFunction().getName())
                        .toolParam(JSON.parseObject(command.getFunction().getArguments(), Map.class))
                        .toolResult(result)
                        .build(), null);
            }

            // 步骤3.2：截断超长工具结果（防止Token超限或内容过长）
            if (maxObserve != null) {
                result = result.substring(0, Math.min(result.length(), maxObserve)); // 仅保留前maxObserve个字符
            }

            // 步骤3.3：更新智能体记忆（兼容两种工具调用模式）
            if ("struct_parse".equals(llm.getFunctionCallType())) {
                // 模式1：结构化解析 → 更新最后一条助手消息，追加工具执行结果
                String content = getMemory().getLastMessage().getContent();
                getMemory().getLastMessage().setContent(content + "\n 工具执行结果为:\n" + result);
            } else {
                // 模式2：原生函数调用 → 新增工具消息（role=tool），关联工具调用ID
                Message toolMsg = Message.toolMessage(
                        result,          // 工具执行结果
                        command.getId(), // 工具调用ID（关联工具调用指令）
                        null             // 无图片
                );
                getMemory().addMessage(toolMsg); // 添加到记忆，供下一轮思考参考
            }

            // 步骤3.4：收集工具结果，用于最终聚合返回
            results.add(result);
        }

        // 步骤4：聚合所有工具结果（换行分隔），返回给上层流程
        return String.join("\n\n", results);
    }

    /**
     * 重写运行方法（智能体入口方法）
     * 核心逻辑：直接继承父类ReActAgent的run方法，遵循"初始化→循环（think→act）→终止"的标准ReAct流程
     * 父类run方法逻辑：
     * 1. 初始化智能体状态；
     * 2. 循环执行think（思考）和act（执行），直到达到最大步数或智能体标记为完成；
     * 3. 返回最终结果（如工具执行结果、大模型直接回复）。
     *
     * @param request 用户请求字符串（实际上下文已通过构造方法传入，此参数为父类兼容）
     * @return String 智能体执行的最终结果
     */
    @Override
    public String run(String request) {
        return super.run(request);
    }
}