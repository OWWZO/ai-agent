package org.wwz.ai.domain.agent.reactor.agent.agent;



/**
 * 代理基类 - 管理代理状态和执行的基础类
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.artifact.ToolArtifactFormatter;
import org.wwz.ai.domain.agent.reactor.agent.artifact.ToolArtifactSource;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * 所有Agent的抽象基类（Base Agent）
 * 定义了Agent的通用属性、核心执行流程和基础能力（内存管理、工具执行、状态控制），
 * 抽象出{@link #step()}方法由子类实现，体现“模板方法模式”——固定主流程，子类定制具体步骤逻辑
 *
 * 核心设计思路：
 * 1. 封装通用属性：名称、描述、提示词、内存、LLM、上下文等
 * 2. 固定执行流程：run()方法定义主循环，循环调用子类实现的step()
 * 3. 提供基础能力：记忆更新、单/多工具执行（支持并发）、状态管理
 *
 * @author （可补充作者）
 * @version （可补充版本）
 */
@Slf4j // Lombok注解：自动生成日志对象
@Data // Lombok注解：自动生成getter/setter、toString、equals、hashCode等方法
@Accessors(chain = true) // Lombok注解：设置setter方法返回当前对象，支持链式调用（如agent.setName("test").setMaxSteps(5)）
public abstract class BaseAgent {

    // ===================== 核心属性 =====================
    /** Agent名称，用于日志标识、区分不同Agent实例 */
    private String name;
    /** Agent功能描述，用于说明该Agent的作用（如“ReAct模式执行Agent”、“总结Agent”） */
    private String description;
    /** 系统提示词，定义Agent的核心行为准则、角色定位（传给LLM的system角色消息） */
    private String systemPrompt;
    /** 下一步执行提示词，用于引导Agent生成下一个执行步骤的指令（如“请基于当前结果生成下一步操作”） */
    private String nextStepPrompt;
    /** Agent可调用的工具集合，管理所有可用工具的注册、执行 */
    public ToolCollection availableTools = new ToolCollection();
    /** Agent的记忆模块，存储执行过程中的所有消息（用户输入、LLM回复、工具调用结果等） */
    private Memory memory = new Memory();
    /** 大语言模型（LLM）实例，Agent的核心推理能力依赖该实例 */
    protected LLM llm;
    /** Agent上下文，存储全局共享信息（如请求ID、产物文件、Printer等） */
    protected AgentContext context;

    // ===================== 执行控制属性 =====================
    /** Agent执行状态，默认IDLE（空闲），包含FINISHED（完成）、ERROR（异常）等状态（枚举类AgentState） */
    private AgentState state = AgentState.IDLE;
    /** Agent允许的最大执行步数，防止无限循环，默认10步 */
    private int maxSteps = 10;
    /** 当前已执行的步数，用于计数和终止循环 */
    private int currentStep = 0;
    /** 重复操作阈值，用于判断是否出现重复步骤（未在当前代码中使用，预留扩展） */
    private int duplicateThreshold = 2;

    // ===================== 结果输出属性 =====================
    /** 结果输出器，用于将Agent执行结果发送给前端/调用方（如send("result", taskResult)） */
    Printer printer;

    // ===================== 数字员工专属属性 =====================
    /** 数字员工专属提示词，用于定制数字员工角色的Agent行为（扩展属性） */
    private String digitalEmployeePrompt;

    /**
     * 抽象方法：执行单个步骤的核心逻辑
     * 由子类实现，定义Agent每一步的具体行为（如ReAct Agent的“推理-行动-观察”步骤）
     * @return 单个步骤的执行结果（字符串形式，如LLM回复、工具执行结果）
     */
    public abstract String step();

    /**
     * Agent核心执行主循环：控制step()方法的循环调用，直到满足终止条件
     * 终止条件：1. 当前步数 >= 最大步数  2. Agent状态变为FINISHED（完成）
     * @param query 用户输入的查询/任务指令（可为空，空则仅执行初始化逻辑）
     * @return 最终执行结果：优先返回最后一步的step结果；无执行步骤则返回"No steps executed"
     */
    public String run(String query) {
        // 初始化Agent状态为空闲（IDLE），重置执行状态
        setState(AgentState.IDLE);

        // 若用户查询非空，将查询添加到Agent记忆（USER角色消息）
        if (query != null && !query.isEmpty()) {
            updateMemory(RoleType.USER, query, null);
        }

        // 存储每一步的执行结果，用于最终返回最后一步结果
        List<String> results = new ArrayList<>();
        try {
            // 主循环：未达到最大步数 且 状态未完成 → 继续执行步骤
            while (currentStep < maxSteps && state != AgentState.FINISHED) {
                // 步数自增，记录当前执行步数
                currentStep++;
                // 打印日志：标记请求ID、Agent名称、当前步数/最大步数（便于排查问题）
                log.info("{} {} Executing step {}/{}", context.getRequestId(), getName(), currentStep, maxSteps);
                // 调用子类实现的step()方法，执行单个步骤并获取结果
                //think给出工具调用参数 由act多线程执行然后返回结果
                String stepResult = step();
                // 将当前步骤结果加入列表
                results.add(stepResult);
            }

            // 终止条件1：达到最大步数 → 重置状态和步数，添加终止提示
            if (currentStep >= maxSteps) {
                currentStep = 0; // 重置步数，便于后续复用Agent实例
                state = AgentState.IDLE; // 重置状态为空闲
                results.add("Terminated: Reached max steps (" + maxSteps + ")"); // 添加终止原因
            }
        } catch (Exception e) {
            // 执行异常：标记状态为ERROR，抛出异常让上层处理
            state = AgentState.ERROR;
            throw e;
        }

        // 返回结果：有执行步骤则返回最后一步结果，无则返回默认提示
        return results.isEmpty() ? "No steps executed" : results.get(results.size() - 1);
    }

    /**
     * 更新Agent的记忆模块：根据角色类型创建对应消息，并添加到内存
     * 支持用户、系统、助手、工具四种核心角色，其他角色抛异常
     * @param role 消息角色类型（枚举类RoleType：USER/SYSTEM/ASSISTANT/TOOL）
     * @param content 消息内容（核心文本）
     * @param base64Image 可选：base64格式的图片（支持多模态消息，可为null）
     * @param args 可变参数：仅TOOL角色需要，第一个参数为工具名称（用于工具消息标识）
     */
    public void updateMemory(RoleType role, String content, String base64Image, Object... args) {
        Message message;
        // 根据角色类型创建对应消息实例
        switch (role) {
            case USER:
                // 用户消息：存储用户输入的查询/指令
                message = Message.userMessage(content, base64Image);
                break;
            case SYSTEM:
                // 系统消息：存储系统提示词、Agent行为准则
                message = Message.systemMessage(content, base64Image);
                break;
            case ASSISTANT:
                // 助手消息：存储LLM生成的回复、Agent的推理结果
                message = Message.assistantMessage(content, base64Image);
                break;
            case TOOL:
                // 工具消息：存储工具执行结果，需传入工具名称（args[0]）
                message = Message.toolMessage(content, (String) args[0], base64Image);
                break;
            default:
                // 不支持的角色类型：抛非法参数异常
                throw new IllegalArgumentException("Unsupported role type: " + role);
        }
        // 将创建的消息添加到Agent内存，完成记忆更新
        memory.addMessage(message);
    }

    /**
     * 预装历史消息，避免多个 Agent 共享同一份可变列表。
     */
    protected void preloadMemory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        memory.addMessages(new ArrayList<>(messages));
    }

    /**
     * 注入会话历史摘要。
     * 如果模板中没有显式占位符，则在尾部追加稳定区块，保证 executor 也能收到同一份会话记忆。
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
     * 从工具集合构建工具描述提示词。
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
     * 初始化系统提示词和下一步提示词，统一处理标准占位符替换。
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
     * 为单次工具结果追加当前 toolCall 生成的文件摘要，避免模型只能看到扁平文件池。
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
     * 执行单个工具调用命令
     * 处理工具名称校验、参数解析、工具执行、异常捕获，返回标准化结果
     * @param command 工具调用命令（包含工具名称、参数、工具ID等）
     * @return 工具执行结果：成功返回工具输出；失败返回错误提示（如"Tool xxx Error."）
     */
    public String executeTool(ToolCall command) {
        // 校验工具调用命令格式：命令/函数/函数名称为空 → 返回格式错误提示
        if (command == null || command.getFunction() == null
                || command.getFunction().getName() == null
                || command.getFunction().getName().isBlank()) {
            return "Error: Invalid function call format";
        }

        // 获取要执行的工具名称
        String name = command.getFunction().getName();
        try {
            // 1. 解析工具参数：将JSON格式的参数字符串转为Object（适配不同工具的参数结构）
            ObjectMapper mapper = new ObjectMapper();
            Object args = mapper.readValue(command.getFunction().getArguments(), Object.class);

            ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                    .sessionId(context.getSessionId())
                    .requestId(context.getRequestId())
                    .toolCallId(command.getId())
                    .toolName(name)
                    .build();

            // 2. 执行工具：调用ToolCollection的execute方法，传入工具名称和参数
            Object result;
            context.bindCurrentToolArtifactSource(artifactSource);
            try {
                result = availableTools.execute(name, args);
            } finally {
                context.clearCurrentToolArtifactSource();
            }

            // 打印日志：记录请求ID、工具名称、参数、执行结果（便于调试）
            log.info("{} execute tool: {} {} result {}", context.getRequestId(), name, args, result);

            // 3. 格式化结果：字符串直接返回，其他对象序列化为JSON字符串
            if (result == null) {
                return "Tool " + name + " Error.";
            }
            if (result instanceof String strResult) {
                return strResult;
            }
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            // 捕获工具执行异常：打印错误日志，后续返回工具错误提示
            log.error("{} execute tool {} failed ", context.getRequestId(), name, e);
        }
        // 工具执行失败（参数解析/执行/结果为空）：返回标准化错误提示
        return "Tool " + name + " Error.";
    }

    /**
     * 并发执行多个工具调用命令（核心优化：提升多工具执行效率）
     * 使用CountDownLatch等待所有工具执行完成，线程安全存储结果
     * @param commands 工具调用命令列表（多个ToolCall实例）
     * @return 工具执行结果映射：key=工具ID（tooCall.getId()），value=工具执行结果
     */
    public Map<String, String> executeTools(List<ToolCall> commands) {
        // 线程安全的Map：存储多工具执行结果（ConcurrentHashMap适配多线程写入）
        Map<String, String> result = new ConcurrentHashMap<>();
        // 创建倒计时锁存器：计数为工具命令数量，用于等待所有线程执行完成
        CountDownLatch taskCount = ThreadUtil.getCountDownLatch(commands.size());

        // 遍历所有工具命令，提交到线程池并发执行
        for (ToolCall tooCall : commands) {
            ThreadUtil.execute(() -> {
                try {
                    // 执行单个工具调用，获取结果
                    String toolResult = executeTool(tooCall);
                    // 将结果存入Map：key为工具ID，value为执行结果
                    result.put(tooCall.getId(), toolResult);
                } finally {
                    // 无论执行成功/失败，都减少锁存器计数（避免死等）
                    taskCount.countDown();
                }
            });
        }

        // 阻塞当前线程，直到所有工具执行完成（锁存器计数为0）
        ThreadUtil.await(taskCount);
        // 返回所有工具的执行结果映射
        return result;
    }
}
