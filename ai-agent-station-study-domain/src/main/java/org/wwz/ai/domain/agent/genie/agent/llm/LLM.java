package org.wwz.ai.domain.agent.genie.agent.llm;



import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.context.ApplicationContext;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.genie.agent.dto.Message;
import org.wwz.ai.domain.agent.genie.agent.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.genie.agent.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.genie.agent.dto.tool.ToolChoice;
import org.wwz.ai.domain.agent.genie.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.genie.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.genie.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.genie.agent.util.StringUtil;
import org.wwz.ai.domain.agent.genie.config.GenieConfig;
import org.wwz.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 类
 */
@Slf4j
@Data
public class LLM {
    /**
     * LLM实例缓存池（单例模式+缓存）
     * 设计目的：避免重复创建相同配置的LLM实例，提升性能并减少资源占用
     * Key规则：模型名称 + LLM ERP标识（组合唯一标识一个LLM实例）
     * Value：初始化完成的LLM实例
     * 线程安全：使用ConcurrentHashMap保证多线程环境下的缓存操作安全
     */
    private static final Map<String, LLM> instances = new ConcurrentHashMap<>();

    // ===================== 核心不可变配置字段（初始化后不允许修改） =====================
    /** 模型标识（如gpt-4o、claude-3-5-sonnet，与大模型平台的模型名一致） */
    private final String model;
    /** LLM所属ERP标识（企业资源规划标识，用于权限隔离、计费统计、多租户区分） */
    private final String llmErp;
    /** 模型单次响应的最大生成Token数（限制输出文本长度，防止响应过长） */
    private final int maxTokens;
    /** 温度系数（控制输出随机性：0=完全确定，1=高随机性，0.7为通用默认值） */
    private final double temperature;
    /** API调用鉴权密钥（大模型平台的访问凭证，如OpenAI的API Key） */
    private final String apiKey;
    /** API基础地址（如https://api.openai.com，不同模型平台的基础域名/IP） */
    private final String baseUrl;
    /** API接口路径（如/v1/chat/completions，默认值适配OpenAI标准接口，可通过配置覆盖） */
    private final String interfaceUrl;
    /** 工具调用模式：
     * - struct_parse：通过结构化提示词解析工具调用（兼容所有模型）
     * - function_call：模型原生工具调用（GPT/Claude原生支持）
     */
    private final String functionCallType;
    /** Token计数器（用于计算消息的Token占用量，判断是否需要截断） */
    private final TokenCounter tokenCounter;
    /** JSON序列化/反序列化工具（处理请求参数和响应数据的JSON转换） */
    private final ObjectMapper objectMapper;
    /** 模型扩展参数（如top_p、frequency_penalty等，不同模型的专属配置） */
    private final Map<String, Object> extParams;

    // ===================== 动态状态字段（运行时可更新） =====================
    /** 累计输入Token数（用于监控LLM调用的Token消耗总量，可用于限流/计费） */
    private int totalInputTokens;
    /** 模型最大输入Token数（上下文窗口上限，超出则触发消息截断逻辑） */
    private Integer maxInputTokens;

    /**
     * LLM实例构造方法（配置驱动初始化）
     * 核心逻辑：从配置中心加载指定模型的配置，初始化所有核心字段，完成实例就绪
     * @param modelName 模型名称（如"gpt-4o"，用于从配置中心获取对应配置）
     * @param llmErp ERP标识（用于多租户/多业务线隔离，可为空）
     */
    public LLM(String modelName, String llmErp) {
        // 初始化ERP标识（用于权限/计费隔离）
        this.llmErp = llmErp;

        // 步骤1：从配置中心加载模型专属配置
        LLMSettings config = Config.getLLMConfig(modelName);

        // 步骤2：初始化核心不可变配置字段
        this.model = config.getModel(); // 模型名
        this.maxTokens = config.getMaxTokens(); // 最大生成Token数
        this.temperature = config.getTemperature(); // 温度系数
        this.apiKey = config.getApiKey(); // API密钥
        String baseUrlFromConfig = config.getBaseUrl();
        if (baseUrlFromConfig == null || baseUrlFromConfig.isBlank()) {
            throw new IllegalArgumentException(
                    "Base URL is not configured or empty. Please set llm.default.base_url in application.yml (or application-<profile>.yml), "
                            + "or configure llm.settings for model: " + modelName + ". Example: llm.default.base_url: https://api.openai.com");
        }
        this.baseUrl = baseUrlFromConfig;
        // 接口路径：优先使用配置值，无配置则使用OpenAI标准路径
        this.interfaceUrl = StringUtils.isNotEmpty(config.getInterfaceUrl()) ? config.getInterfaceUrl() : "/v1/chat/completions";
        this.functionCallType = config.getFunctionCallType(); // 工具调用模式

        // 步骤3：初始化Token管控相关动态字段
        this.totalInputTokens = 0; // 累计输入Token数初始化为0
        this.maxInputTokens = config.getMaxInputTokens(); // 模型最大输入Token数
        this.extParams = config.getExtParams(); // 扩展参数

        // 步骤4：初始化工具类实例
        this.tokenCounter = new TokenCounter(); // Token计数器（计算消息Token数）
        this.objectMapper = new ObjectMapper(); // JSON解析器（处理请求/响应）
    }

    /**
     * 消息格式化核心方法（智能体消息模型 → 大模型可识别的消息格式）
     * 核心能力：
     * 1. 多模态适配：处理base64编码图片，封装为GPT/Claude支持的多模态格式
     * 2. 工具调用适配：区分GPT/Claude的工具调用消息格式
     * 3. 工具结果适配：处理工具调用返回结果，适配不同模型的格式要求
     * 4. 普通文本适配：直接封装角色+内容的基础格式
     * 5. 敏感词过滤：工具结果消息自动做文本脱敏，符合合规要求
     *
     * @param messages 智能体侧统一消息列表（Message对象）
     * @param isClaude 是否为Claude模型（true=适配Claude格式，false=适配GPT格式）
     * @return List<Map<String, Object>> 大模型可直接接收的消息列表
     */
    public static List<Map<String, Object>> formatMessages(List<Message> messages, boolean isClaude) {
        // 初始化格式化后的消息列表
        List<Map<String, Object>> formattedMessages = new ArrayList<>();

        // 遍历每条消息，按类型适配格式
        for (Message message : messages) {
            Map<String, Object> messageMap = new HashMap<>();

            // ========== 场景1：处理多模态消息（携带base64图片） ==========
            if (message.getBase64Image() != null && !message.getBase64Image().isEmpty()) {
                // 多模态内容容器（兼容文本+图片）
                List<Map<String, Object>> multimodalContent = new ArrayList<>();

                // 构建图片内容（GPT的image_url格式）
                Map<String, String> imageUrlMap = new HashMap<>();
                // 拼接base64图片URL（data URI格式，兼容大部分大模型）
                imageUrlMap.put("url", "data:image/jpeg;base64," + message.getBase64Image());
                Map<String, Object> outerMap = new HashMap<>();
                outerMap.put("type", "image_url"); // 内容类型：图片
                outerMap.put("image_url", imageUrlMap);
                multimodalContent.add(outerMap);

                // 构建文本内容（注：此处代码存在小问题，复用了outerMap导致图片字段被覆盖，注释保留原逻辑）
                Map<String, Object> contentMap = new HashMap<>();
                outerMap.put("type", "text"); // 内容类型：文本
                outerMap.put("text", message.getContent());
                multimodalContent.add(contentMap);

                // 封装角色和多模态内容
                messageMap.put("role", message.getRole().getValue());
                messageMap.put("content", multimodalContent);

                // ========== 场景2：处理工具调用消息（助手消息携带工具调用指令） ==========
            } else if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                if (isClaude) {
                    // Claude格式：工具调用封装为tool_use列表
                    messageMap.put("role", message.getRole().getValue());
                    List<Map<String, Object>> claudeToolCalls = new ArrayList<>();
                    for (ToolCall toolCall : message.getToolCalls()) {
                        Map<String, Object> claudeToolCall = new HashMap<>();
                        claudeToolCall.put("type", "tool_use"); // Claude工具调用类型
                        claudeToolCall.put("id", toolCall.getId()); // 工具调用ID（关联结果）
                        claudeToolCall.put("name", toolCall.getFunction().getName()); // 工具名称
                        // 工具参数：JSON字符串转为Map（Claude要求结构化参数）
                        claudeToolCall.put("input", JSON.parseObject(toolCall.getFunction().getArguments()));
                        claudeToolCalls.add(claudeToolCall);
                    }
                    messageMap.put("content", claudeToolCalls);
                } else {
                    // GPT格式：工具调用封装为tool_calls字段
                    messageMap.put("role", message.getRole().getValue());
                    // 将ToolCall对象列表转为GPT可识别的Map列表
                    List<Map<String, Object>> toolCallsMap = JSON.parseObject(
                            JSON.toJSONString(message.getToolCalls()),
                            new TypeReference<List<Map<String, Object>>>() {}
                    );
                    messageMap.put("tool_calls", toolCallsMap);
                }

                // ========== 场景3：处理工具调用结果消息（携带toolCallId） ==========
            } else if (message.getToolCallId() != null && !message.getToolCallId().isEmpty()) {
                // 合规处理：文本敏感词过滤（如手机号、身份证、敏感词汇）
                GenieConfig genieConfig = SpringContextHolder.getApplicationContext().getBean(GenieConfig.class);
                String content = StringUtil.textDesensitization(message.getContent(), genieConfig.getSensitivePatterns());

                if (isClaude) {
                    // Claude格式：工具结果封装为tool_result，角色固定为user
                    messageMap.put("role", "user");
                    List<Map<String, Object>> claudeToolCalls = new ArrayList<>();
                    Map<String, Object> claudeToolCall = new HashMap<>();
                    claudeToolCall.put("type", "tool_result"); // Claude工具结果类型
                    claudeToolCall.put("tool_use_id", message.getToolCallId()); // 关联的工具调用ID
                    claudeToolCall.put("content", content); // 脱敏后的工具结果内容
                    claudeToolCalls.add(claudeToolCall);
                    messageMap.put("content", claudeToolCalls);
                } else {
                    // GPT格式：直接添加tool_call_id字段，保留原角色
                    messageMap.put("role", message.getRole().getValue());
                    messageMap.put("content", content);
                    messageMap.put("tool_call_id", message.getToolCallId());
                }

                // ========== 场景4：普通文本消息（无图片、无工具调用） ==========
            } else {
                // 基础格式：角色 + 文本内容
                messageMap.put("role", message.getRole().getValue());
                messageMap.put("content", message.getContent());
            }

            // 将格式化后的单条消息加入列表
            formattedMessages.add(messageMap);
        }

        return formattedMessages;
    }

    /**
     * 消息截断核心方法（保证消息Token数不超过模型上下文窗口上限）
     * 核心设计原则：
     * 1. 优先保留系统消息（保证模型执行指令不丢失）
     * 2. 优先保留最新的消息（保证上下文时效性）
     * 3. 优先保留用户消息（保证用户核心意图不丢失）
     * 4. 保证截断后的消息列表结构完整
     *
     * @param context 智能体上下文（携带requestId，用于日志追踪）
     * @param messages 格式化后的消息列表（待截断）
     * @param maxInputTokens 模型最大输入Token数（上下文窗口上限）
     * @return List<Map<String, Object>> 截断后的消息列表（Token数≤maxInputTokens）
     */
    public List<Map<String, Object>> truncateMessage(AgentContext context, List<Map<String, Object>> messages, int maxInputTokens) {
        // 边界条件1：空消息列表或无效Token上限，直接返回原列表
        if (messages.isEmpty() || maxInputTokens < 0) {
            return messages;
        }

        // 日志：记录截断前的消息（便于问题排查）
        log.info("{} before truncate {}", context.getRequestId(), JSON.toJSONString(messages));

        // 初始化截断后的消息列表
        List<Map<String, Object>> truncatedMessages = new ArrayList<>();
        // 剩余可用Token数（初始为模型最大输入Token数）
        int remainingTokens = maxInputTokens;
        // 提取第一条消息（通常为系统消息）
        Map<String, Object> system = messages.get(0);

        // 步骤1：预留系统消息的Token空间（如果第一条是系统消息）
        if ("system".equals(system.getOrDefault("role", ""))) {
            // 计算系统消息的Token数，并从剩余Token中扣除
            remainingTokens -= tokenCounter.countMessageTokens(system);
        }

        // 步骤2：从后往前遍历消息（优先保留最新消息）
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> message = messages.get(i);
            // 计算当前消息的Token数
            int messageToken = tokenCounter.countMessageTokens(message);

            // 如果剩余Token足够容纳当前消息，加入截断列表（从头部插入保证顺序）
            if (remainingTokens >= messageToken) {
                truncatedMessages.add(0, message);
                remainingTokens -= messageToken;
            } else {
                // Token不足，停止遍历（放弃更早的消息）
                break;
            }
        }

        // 步骤3：清理非用户消息前缀（保证用户核心意图不丢失）
        // 逻辑：移除截断列表开头的非user消息，直到遇到第一条user消息为止
        Iterator<Map<String, Object>> iterator = truncatedMessages.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> message = iterator.next();
            if (!"user".equals(message.getOrDefault("role", ""))) {
                iterator.remove(); // 安全删除（Iterator避免ConcurrentModificationException）
            } else {
                break; // 遇到user消息，停止清理
            }
        }

        // 步骤4：重新添加系统消息（保证模型执行指令不丢失）
        if ("system".equals(system.getOrDefault("role", ""))) {
            truncatedMessages.add(0, system);
        }

        // 日志：记录截断后的消息（便于问题排查）
        log.info("{} after truncate {}", context.getRequestId(), JSON.toJSONString(truncatedMessages));

        return truncatedMessages;
    }

    /**
     * LLM核心请求方法（发送消息并获取响应，支持流式/非流式、自定义温度、系统消息）
     * 核心能力：
     * 1. 消息格式化：自动拼接系统消息+用户消息，适配GPT/Claude不同格式
     * 2. 参数组装：封装模型、Token上限、温度、扩展参数等核心请求参数
     * 3. 双模式支持：非流式（一次性返回完整响应）、流式（逐段返回响应）
     * 4. 异常处理：统一捕获异常并包装为CompletableFuture异常，保证异步调用稳定性
     * 5. 响应解析：非流式响应自动解析出消息内容，简化上层调用逻辑
     *
     * @param context 智能体上下文（携带requestId，用于日志追踪和链路排查）
     * @param messages 用户/助手交互消息列表（核心对话内容）
     * @param systemMsgs 系统消息列表（模型执行指令，如角色定义、规则约束，可为空）
     * @param stream 是否启用流式响应：true=流式（逐段返回），false=非流式（一次性返回）
     * @param temperature 自定义温度系数（覆盖实例默认值，null则使用实例初始化的温度）
     * @return CompletableFuture<String> 异步响应结果：
     *         - 非流式：返回解析后的完整响应文本
     *         - 流式：返回流式响应的聚合结果（具体逻辑由callOpenAIStream实现）
     */
    public CompletableFuture<String> ask(
            AgentContext context,
            List<Message> messages,
            List<Message> systemMsgs,
            boolean stream,
            Double temperature
    ) {
        // 渐进改造版本：优先使用 Spring AI 的 ChatClient 调用大模型，
        // 仅用于非流式 summarize / 生成场景（当前调用点都是 stream=false）。
        try {
            if (stream) {
                // 目前暂不改造流式路径，仍旧走原有 HTTP 流式实现
                Map<String, Object> params = new HashMap<>();
                params.put("model", model);
                if (StringUtils.isNotEmpty(llmErp)) {
                    params.put("erp", llmErp);
                }
                params.put("messages", formatMessages(messages, model.contains("claude")));
                return callOpenAIStream(params);
            }

            // 1. 将 systemMsgs + messages 的 content 简单拼接为一个 prompt 文本
            StringBuilder promptBuilder = new StringBuilder();
            if (systemMsgs != null) {
                for (Message sys : systemMsgs) {
                    if (sys != null && sys.getContent() != null) {
                        promptBuilder.append(sys.getContent()).append("\n");
                    }
                }
            }
            if (messages != null) {
                for (Message msg : messages) {
                    if (msg != null && msg.getContent() != null) {
                        promptBuilder.append(msg.getContent()).append("\n");
                    }
                }
            }
            String prompt = promptBuilder.toString().trim();
            if (prompt.isEmpty()) {
                CompletableFuture<String> future = new CompletableFuture<>();
                future.complete("");
                return future;
            }

            // 2. 通过 SpringContextHolder 获取 Spring AI 的 ChatClient
            ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
            // 这里按照你的说明，先写死使用 clientId=2102 对应的 ChatClient
            String beanName = AiAgentEnumVO.AI_CLIENT.getBeanName("2102");
            ChatClient chatClient = applicationContext.getBean(beanName, ChatClient.class);

            log.info("{} call llm ask via ChatClient, beanName:{}, prompt length:{}",
                    context.getRequestId(), beanName, prompt.length());

            // 3. 使用 ChatClient 进行非流式调用（温度暂时依赖 ChatClient 默认配置）
            return CompletableFuture.supplyAsync(() -> {
                try {
                    var spec = chatClient.prompt().user(prompt);
                    String content = spec
                            .call()
                            .chatResponse()
                            .getResult()
                            .getOutput()
                            .getText();
                    return content != null ? content : "";
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });
        } catch (Exception e) {
            log.error("{} Unexpected error in ask (ChatClient): {}", context.getRequestId(), e.getMessage(), e);
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * 通用对象深拷贝方法（基于JSON序列化/反序列化实现，兼容任意可序列化对象）
     * 设计目的：
     * - 避免对象引用传递导致的副作用（如修改拷贝对象影响原对象）
     * - 简化复杂对象（如Map、自定义POJO）的深拷贝逻辑
     * 适用场景：工具格式转换、参数修改等需要独立对象的场景
     *
     * @param original 待拷贝的原始对象（支持任意可JSON序列化的对象：Map、List、POJO等）
     * @param <T> 泛型参数，保证返回值类型与原始对象一致
     * @return T 与原始对象内容完全一致的新对象（无引用关联）
     * @throws RuntimeException 深拷贝失败时抛出（如序列化/反序列化异常）
     */
    public <T> T deepCopy(T original) {
        try {
            // 步骤1：将原始对象序列化为JSON字节数组（打破对象引用）
            byte[] jsonBytes = objectMapper.writeValueAsBytes(original);

            // 步骤2：将JSON字节数组反序列化为新对象（生成独立的新实例）
            return objectMapper.readValue(
                    jsonBytes,
                    // 动态构造原始对象的类型，保证泛型类型正确
                    objectMapper.getTypeFactory().constructType(original.getClass())
            );
        } catch (Exception e) {
            // 包装为运行时异常，简化上层调用的异常处理
            throw new RuntimeException("深拷贝失败", e);
        }
    }

    /**
     * GPT工具定义格式 → Claude工具定义格式转换方法
     * 核心差异适配：
     * - GPT：工具包装在{"function": {...}}中，参数直接放在parameters
     * - Claude：工具直接为{"name":..., "input_schema":...}，且要求必传function_name字段
     * 设计原则：
     * 1. 深拷贝原数据：避免修改原始GPT工具定义
     * 2. 兼容扩展：保留原参数结构，仅补充Claude必需的字段
     * 3. 语义对齐：function_name默认值为工具名，保证调用逻辑一致
     *
     * @param gptTools OpenAI GPT格式的工具定义列表（结构：[{"function": {"name":..., "description":..., "parameters":...}}]）
     * @return List<Map<String, Object>> Claude格式的工具定义列表（结构：[{"name":..., "description":..., "input_schema":...}]）
     */
    public List<Map<String, Object>> gptToClaudeTool(List<Map<String, Object>> gptTools) {
        // 深拷贝原始GPT工具列表：避免修改原数据，保证数据隔离
        List<Map<String, Object>> newGptTools = deepCopy(gptTools);
        // 初始化Claude工具列表
        List<Map<String, Object>> claudeTools = new ArrayList<>();

        // 遍历每个GPT工具定义，逐个转换为Claude格式
        for (Map<String, Object> gptToolWrapper : newGptTools) {
            // 步骤1：提取GPT工具的核心function对象（GPT工具包装在function字段中）
            Map<String, Object> gptTool = (Map<String, Object>) gptToolWrapper.get("function");

            // 步骤2：初始化Claude工具对象，映射基础字段
            Map<String, Object> claudeTool = new HashMap<>();
            claudeTool.put("name", gptTool.get("name")); // 工具名称（与GPT保持一致）
            claudeTool.put("description", gptTool.get("description")); // 工具描述（与GPT保持一致）

            // 步骤3：处理参数结构（Claude称为input_schema，且要求必传function_name）
            Map<String, Object> parameters = (Map<String, Object>) gptTool.get("parameters");

            // 3.1 补充required字段：添加function_name为必传参数
            ArrayList<String> newRequired = new ArrayList<>();
            newRequired.add("function_name"); // Claude必需的function_name字段
            // 保留原GPT定义的必传参数，保证参数完整性
            if (parameters.containsKey("required") && Objects.nonNull(parameters.get("required"))) {
                newRequired.addAll((List<String>) parameters.get("required"));
            }
            parameters.put("required", newRequired);

            // 3.2 补充properties字段：添加function_name的参数描述
            Map<String, Object> newProperties = new HashMap<>();
            Map<String, Object> functionNameMap = new HashMap<>();
            functionNameMap.put("description", "默认值为工具名: " + gptTool.get("name")); // 描述信息
            functionNameMap.put("type", "string"); // 参数类型为字符串
            newProperties.put("function_name", functionNameMap);
            // 保留原GPT定义的参数属性，保证参数完整性
            if (parameters.containsKey("properties") && Objects.nonNull(parameters.get("properties"))) {
                newProperties.putAll((Map<String, Object>) parameters.get("properties"));
            }
            parameters.put("properties", newProperties);

            // 步骤4：将处理后的参数结构赋值给Claude的input_schema字段
            claudeTool.put("input_schema", gptTool.get("parameters"));

            // 步骤5：将转换后的Claude工具添加到结果列表
            claudeTools.add(claudeTool);
        }
        return claudeTools;
    }

    /**
     * 工具参数补充方法（为GPT工具参数添加function_name字段，适配Claude格式要求）
     * 核心逻辑：
     * 1. 深拷贝原参数：避免修改原始参数结构
     * 2. 补充required：添加function_name为必传参数
     * 3. 补充properties：添加function_name的描述和类型，默认值关联工具名
     * 适用场景：单独处理工具参数，与gptToClaudeTool的参数处理逻辑解耦，提高复用性
     *
     * @param parameters GPT格式的工具参数结构（包含required、properties等字段）
     * @param toolName 工具名称（用于设置function_name的默认描述）
     * @return Map<String, Object> 补充后的参数结构（兼容Claude格式）
     */
    private Map<String, Object> addFunctionNameParam(Map<String, Object> parameters, String toolName) {
        // 深拷贝原参数：保证原始参数不被修改，数据隔离
        Map<String, Object> newParameters = deepCopy(parameters);

        // ===================== 步骤1：补充required字段（必传参数） =====================
        ArrayList<String> newRequired = new ArrayList<>();
        newRequired.add("function_name"); // Claude必需的function_name字段
        // 保留原必传参数，避免丢失核心约束
        if (parameters.containsKey("required") && Objects.nonNull(parameters.get("required"))) {
            newRequired.addAll((List<String>) parameters.get("required"));
        }
        newParameters.put("required", newRequired);

        // ===================== 步骤2：补充properties字段（参数属性） =====================
        Map<String, Object> newProperties = new HashMap<>();
        // 构建function_name的参数属性
        Map<String, Object> functionNameMap = new HashMap<>();
        functionNameMap.put("description", "默认值为工具名: " + toolName); // 描述关联工具名
        functionNameMap.put("type", "string"); // 参数类型为字符串
        newProperties.put("function_name", functionNameMap);
        // 保留原参数属性，保证参数完整性
        if (parameters.containsKey("properties") && Objects.nonNull(parameters.get("properties"))) {
            newProperties.putAll((Map<String, Object>) parameters.get("properties"));
        }
        newParameters.put("properties", newProperties);

        return newParameters;
    }

    /**
     * 向大语言模型（LLM）发送工具调用请求，并解析返回的工具调用响应
     * 核心能力：
     * 1. 支持两种工具调用模式：struct_parse（结构化解析）、function_call（模型原生工具调用）
     * 2. 兼容GPT/Claude等不同模型的工具调用格式（自动转换GPT→Claude工具格式）
     * 3. 支持流式（stream=true）和非流式（stream=false）两种响应模式
     * 4. 处理BaseTool和McpTool两类工具的格式化，适配模型要求
     * 5. 解析响应中的工具调用指令，封装为ToolCallResponse返回
     *
     * @param context 智能体上下文，包含请求ID、流式标识等核心上下文信息
     * @param messages 对话消息列表，包含用户/助手/工具等角色的消息
     * @param systemMsgs 系统提示词消息，指导模型决策的核心指令
     * @param tools 可用工具集合，包含BaseTool（基础工具）和McpTool（MCP协议工具）
     * @param toolChoice 工具选择策略（AUTO/NONE/指定工具名），控制模型是否/调用哪个工具
     * @param temperature 模型温度系数，控制输出随机性（null则使用类默认值）
     * @param stream 是否开启流式响应：true=流式，false=非流式
     * @param timeout API请求超时时间（单位：秒）
     * @return CompletableFuture<ToolCallResponse> 异步返回工具调用响应，包含响应内容、工具调用列表、耗时等信息
     */
    public CompletableFuture<ToolCallResponse> askTool(
            AgentContext context,
            List<Message> messages,
            Message systemMsgs,
            ToolCollection tools,
            ToolChoice toolChoice,
            Double temperature,
            boolean stream,
            int timeout
    ) {
        try {
            // 渐进改造：优先用 Spring AI 处理非流式 + struct_parse 的工具调用
            if (!stream && "struct_parse".equals(functionCallType)) {
                return askToolWithChatClientStructParse(context, messages, systemMsgs, tools, toolChoice, temperature);
            }

            // 校验工具选择策略的合法性，非法值直接抛出参数异常
            if (!ToolChoice.isValid(toolChoice)) {
                throw new IllegalArgumentException("Invalid tool_choice: " + toolChoice);
            }
            // 记录请求开始时间，用于统计整体耗时
            long startTime = System.currentTimeMillis();

            // 初始化API请求参数容器，存储调用LLM的所有请求参数
            Map<String, Object> params = new HashMap<>();

            // ========== 工具格式化逻辑：根据工具调用模式处理工具定义 ==========
            // 结构化解析模式的工具描述拼接容器（用于拼接到system prompt中）
            StringBuilder stringBuilder = new StringBuilder();
            // 模型原生工具调用模式的工具列表容器（格式化为模型可识别的结构）
            List<Map<String, Object>> formattedTools = new ArrayList<>();

            // 场景1：struct_parse模式（无原生工具调用能力的模型，靠提示词解析JSON）
            if ("struct_parse".equals(functionCallType)) {
                // 获取全局配置，读取结构化解析的工具系统提示词模板
                GenieConfig genieConfig = SpringContextHolder.getApplicationContext().getBean(GenieConfig.class);
                stringBuilder.append(genieConfig.getStructParseToolSystemPrompt());

                // 处理基础工具（BaseTool）：格式化工具名称/描述/参数，拼接到提示词
                for (BaseTool tool : tools.getToolMap().values()) {
                    Map<String, Object> functionMap = new HashMap<>();
                    functionMap.put("name", tool.getName());
                    functionMap.put("description", tool.getDescription());
                    // 为参数补充工具名称字段，方便结构化解析时识别
                    functionMap.put("parameters", addFunctionNameParam(tool.toParams(), tool.getName()));
                    // 按"工具名+JSON格式参数"的格式拼接，让模型输出标准化JSON
                    stringBuilder.append(String.format("- `%s`\n```json %s ```\n", tool.getName(), JSON.toJSONString(functionMap)));
                }

                // 处理MCP工具（McpToolInfo）：兼容MCP协议工具，格式化后拼接到提示词
                for (McpToolInfo tool : tools.getMcpToolMap().values()) {
                    // 解析MCP工具的参数JSON为Map结构
                    Map<String, Object> parameters = JSON.parseObject(tool.getParameters(), new TypeReference<Map<String, Object>>() {});
                    Map<String, Object> functionMap = new HashMap<>();
                    functionMap.put("name", tool.getName());
                    functionMap.put("description", tool.getDesc());
                    functionMap.put("parameters", addFunctionNameParam(parameters, tool.getName()));
                    stringBuilder.append(String.format("- `%s`\n```json %s ```\n", tool.getName(), JSON.toJSONString(functionMap)));
                }

            } else { // 场景2：function_call模式（模型原生支持工具调用，如GPT/Claude）
                // 处理基础工具（BaseTool）：格式化为模型要求的"type=function"结构
                for (BaseTool tool : tools.getToolMap().values()) {
                    Map<String, Object> functionMap = new HashMap<>();
                    functionMap.put("name", tool.getName());
                    functionMap.put("description", tool.getDescription());
                    functionMap.put("parameters", tool.toParams());
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("type", "function"); // 固定类型：function
                    toolMap.put("function", functionMap);
                    formattedTools.add(toolMap);
                }

                // 处理MCP工具（McpToolInfo）：格式化为模型原生工具调用结构
                for (McpToolInfo tool : tools.getMcpToolMap().values()) {
                    Map<String, Object> parameters = JSON.parseObject(tool.getParameters(), new TypeReference<Map<String, Object>>() {});
                    Map<String, Object> functionMap = new HashMap<>();
                    functionMap.put("name", tool.getName());
                    functionMap.put("description", tool.getDesc());
                    functionMap.put("parameters", parameters);
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("type", "function");
                    toolMap.put("function", functionMap);
                    formattedTools.add(toolMap);
                }

                // 兼容Claude模型：将GPT格式的工具定义转换为Claude可识别的格式
                if (model.contains("claude")) {
                    formattedTools = gptToClaudeTool(formattedTools);
                }
            }

            // ========== 消息格式化逻辑：适配模型的消息格式要求 ==========
            List<Map<String, Object>> formattedMessages = new ArrayList<>();
            // 处理系统提示词消息（非空时）
            if (Objects.nonNull(systemMsgs)) {
                // struct_parse模式：将工具描述拼接到系统提示词末尾，让模型识别可用工具
                if ("struct_parse".equals(functionCallType)) {
                    systemMsgs.setContent(systemMsgs.getContent() + "\n" + stringBuilder);
                }
                // Claude模型：系统提示词单独放在"system"参数中（GPT放在messages列表）
                if (model.contains("claude")) {
                    params.put("system", systemMsgs.getContent());
                } else {
                    // 非Claude模型：将系统提示词格式化后加入消息列表
                    formattedMessages.addAll(formatMessages(List.of(systemMsgs), model.contains("claude")));
                }
            }
            // 格式化业务对话消息，适配模型格式（GPT/Claude差异由formatMessages处理）
            formattedMessages.addAll(formatMessages(messages, model.contains("claude")));

            // ========== 设置通用请求参数 ==========
            params.put("model", model); // 指定调用的模型名称
            // 设置ERP标识（如有），用于模型权限/计费等管控
            if (StringUtils.isNotEmpty(llmErp)) {
                params.put("erp", llmErp);
            }
            params.put("messages", formattedMessages); // 格式化后的消息列表

            // function_call模式：添加工具列表和工具选择策略参数
            if (!"struct_parse".equals(functionCallType)) {
                params.put("tools", formattedTools);
                params.put("tool_choice", toolChoice.getValue());
            }

            // 添加模型通用参数：最大生成Token数、温度系数
            params.put("max_tokens", maxTokens);
            params.put("temperature", temperature != null ? temperature : this.temperature);
            // 合并扩展参数（如top_p、frequency_penalty等自定义参数）
            if (Objects.nonNull(extParams)) {
                params.putAll(extParams);
            }

            // 打印请求日志（含请求ID），便于问题排查
            log.info("{} call llm request {}", context.getRequestId(), JSONObject.toJSONString(params));

            // ========== 非流式响应处理逻辑 ==========
            if (!stream) {
                params.put("stream", false); // 明确关闭流式
                // 调用OpenAI兼容的API（非流式）
                CompletableFuture<String> future = callOpenAI(params, timeout);
                // 异步解析响应结果，封装为ToolCallResponse
                return future.thenApply(responseJson -> {
                    try {
                        // 打印响应日志，便于排查
                        log.info("{} call llm response {}", context.getRequestId(), responseJson);
                        // 解析JSON响应
                        JsonNode jsonResponse = objectMapper.readTree(responseJson);
                        JsonNode choices = jsonResponse.get("choices");

                        // 校验响应合法性：choices为空/无message字段则抛出异常
                        if (choices == null || choices.isEmpty() || choices.get(0).get("message") == null) {
                            log.error("{} Invalid response: {}", context.getRequestId(), responseJson);
                            throw new IllegalArgumentException("Invalid or empty response from LLM");
                        }

                        // 提取响应核心内容：message中的content字段（空值处理）
                        JsonNode message = choices.get(0).get("message");
                        String content = message.has("content") && !"null".equals(message.get("content").asText()) ? message.get("content").asText() : null;

                        // ========== 解析工具调用指令 ==========
                        List<ToolCall> toolCalls = new ArrayList<>();
                        // struct_parse模式：从content中提取```json ```代码块，解析为ToolCall
                        if ("struct_parse".equals(functionCallType)) {
                            // 正则匹配JSON代码块：```json ... ```
                            String pattern = "```json\\s*([\\s\\S]*?)\\s*```";
                            List<String> matches = findMatches(content, pattern);
                            if (!matches.isEmpty()) {
                                for (String match : matches) {
                                    // 解析单个JSON代码块为ToolCall对象
                                    ToolCall oneToolCall = parseToolCall(context, match);
                                    if (Objects.nonNull(oneToolCall)) {
                                        toolCalls.add(oneToolCall);
                                    }
                                }
                            }
                            // 截取content中JSON代码块之前的文本（去除工具调用指令部分）
                            int stopPos = content.indexOf("```json");
                            content = content.substring(0, stopPos > 0 ? stopPos : content.length());
                        } else { // function_call模式：解析model返回的tool_calls字段
                            if (message.has("tool_calls")) {
                                JsonNode toolCallsNode = message.get("tool_calls");
                                for (JsonNode toolCall : toolCallsNode) {
                                    // 提取工具调用ID、类型
                                    String id = toolCall.get("id").asText();
                                    String type = toolCall.get("type").asText();

                                    // 提取函数信息：名称、参数JSON字符串
                                    JsonNode functionNode = toolCall.get("function");
                                    String name = functionNode.get("name").asText();
                                    String arguments = functionNode.get("arguments").asText();
                                    // 封装为ToolCall对象
                                    toolCalls.add(new ToolCall(id, type, new ToolCall.Function(name, arguments)));
                                }
                            }
                        }
                        // 提取响应元信息：结束原因、总Token数
                        String finishReason = choices.get(0).get("finish_reason").asText();
                        int totalTokens = jsonResponse.get("usage").get("total_tokens").asInt();

                        // 计算请求耗时，封装并返回ToolCallResponse
                        long endTime = System.currentTimeMillis();
                        long duration = endTime - startTime;
                        return new ToolCallResponse(content, toolCalls, finishReason, totalTokens, duration);
                    } catch (IOException e) {
                        // 解析异常封装为CompletionException抛出
                        throw new CompletionException(e);
                    }
                });
            } else {
                // ========== 流式响应处理逻辑 ==========
                params.put("stream", true); // 明确开启流式

                // Claude模型：调用专属的流式工具调用处理方法
                if (model.contains("claude")) {
                    return callClaudeFunctionCallStream(context, params);
                }
                // 非Claude模型（GPT等）：调用通用的OpenAI流式工具调用处理方法
                return callOpenAIFunctionCallStream(context, params);
            }

        } catch (Exception e) {
            // 全局异常捕获：记录错误日志，返回异常的CompletableFuture
            log.error("{} Unexpected error in askTool: {}", context.getRequestId(), e.getMessage(), e);
            CompletableFuture<ToolCallResponse> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * 使用 Spring AI ChatClient 处理 struct_parse 模式下的非流式工具调用
     * 仅替换原来的 HTTP 非流式分支，流式与 function_call 模式仍暂时保留原实现
     */
    private CompletableFuture<ToolCallResponse> askToolWithChatClientStructParse(
            AgentContext context,
            List<Message> messages,
            Message systemMsgs,
            ToolCollection tools,
            ToolChoice toolChoice,
            Double temperature
    ) {
        try {
            ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
            GenieConfig genieConfig = applicationContext.getBean(GenieConfig.class);

            // 1. 构造工具描述提示词（复用 struct_parse 思路，但简化为纯文本）
            StringBuilder promptBuilder = new StringBuilder();
            // 全局 struct_parse 工具系统提示词
            promptBuilder.append(genieConfig.getStructParseToolSystemPrompt()).append("\n\n");
            promptBuilder.append("下面是可用工具的列表和参数定义，请根据用户问题选择合适的工具并输出 JSON：\n");

            // BaseTool
            for (BaseTool tool : tools.getToolMap().values()) {
                Map<String, Object> functionMap = new HashMap<>();
                functionMap.put("name", tool.getName());
                functionMap.put("description", tool.getDescription());
                functionMap.put("parameters", addFunctionNameParam(tool.toParams(), tool.getName()));
                promptBuilder.append(String.format(
                        "- 工具名: %s\n  描述: %s\n  参数(JSON): ```json %s ```\n",
                        tool.getName(), tool.getDescription(), JSON.toJSONString(functionMap)
                ));
            }

            // McpTool
            for (McpToolInfo tool : tools.getMcpToolMap().values()) {
                Map<String, Object> parameters = JSON.parseObject(tool.getParameters(),
                        new TypeReference<Map<String, Object>>() {});
                Map<String, Object> functionMap = new HashMap<>();
                functionMap.put("name", tool.getName());
                functionMap.put("description", tool.getDesc());
                functionMap.put("parameters", addFunctionNameParam(parameters, tool.getName()));
                promptBuilder.append(String.format(
                        "- 工具名: %s\n  描述: %s\n  参数(JSON): ```json %s ```\n",
                        tool.getName(), tool.getDesc(), JSON.toJSONString(functionMap)
                ));
            }

            promptBuilder.append("\n==== 对话历史 ====\n");
            if (systemMsgs != null && StringUtils.isNotEmpty(systemMsgs.getContent())) {
                promptBuilder.append("【系统提示】").append(systemMsgs.getContent()).append("\n");
            }
            if (messages != null) {
                for (Message msg : messages) {
                    if (msg != null && msg.getContent() != null) {
                        promptBuilder.append("role:").append(msg.getRole())
                                .append(" content:").append(msg.getContent()).append("\n");
                    }
                }
            }

            // 2. 使用 BeanOutputConverter 约束工具调用计划的 JSON 结构
            BeanOutputConverter<List<ToolPlan>> converter =
                    new BeanOutputConverter<>(new ParameterizedTypeReference<>() {});

            String format = converter.getFormat();

            promptBuilder.append("""

接下来你需要根据上述工具列表和对话历史，规划本轮要调用的工具列表。
请严格按照下面 JSON 结构输出（不要增加额外字段，不要包裹在 ```json ``` 代码块中）：
""");
            promptBuilder.append(format).append("\n\n");
            promptBuilder.append("""
约束要求：
1. 如果需要调用一个或多个工具，逐个在数组中列出，每个元素对应一次调用。
2. 每个元素的 functionName 对应具体工具名（如 "file_tool"），arguments 是该工具的参数对象。
3. 如果本轮不需要调用任何工具，请输出一个空数组 []。
""");

            String prompt = promptBuilder.toString();

            // 3. 获取 ChatClient（先写死使用 clientId=2102）
            String beanName = AiAgentEnumVO.AI_CLIENT.getBeanName("2102");
            ChatClient chatClient = applicationContext.getBean(beanName, ChatClient.class);

            log.info("{} call llm askTool via ChatClient, beanName:{}, prompt length:{}",
                    context.getRequestId(), beanName, prompt.length());

            return CompletableFuture.supplyAsync(() -> {
                try {
                    var spec = chatClient.prompt().user(prompt);
                    // 暂不在此处单独配置 temperature，沿用 ChatClient 默认或模型级配置
                    String content = spec
                            .call()
                            .chatResponse()
                            .getResult()
                            .getOutput()
                            .getText();
                    if (content == null) {
                        content = "";
                    }

                    // 4. 使用 BeanOutputConverter 将内容解析为结构化的 ToolPlan 列表
                    List<ToolPlan> plans = converter.convert(content);
                    if (plans == null) {
                        plans = Collections.emptyList();
                    }

                    List<ToolCall> toolCalls = new ArrayList<>();
                    for (ToolPlan plan : plans) {
                        if (plan == null || plan.getFunctionName() == null || plan.getFunctionName().isEmpty()) {
                            continue;
                        }
                        // 将 arguments Map 转回 JSON 字符串，兼容现有 ToolCall 结构
                        String argsJson = plan.getArguments() != null
                                ? JSON.toJSONString(plan.getArguments())
                                : "{}";
                        toolCalls.add(ToolCall.builder()
                                .id(StringUtil.getUUID())
                                .type("function")
                                .function(ToolCall.Function.builder()
                                        .name(plan.getFunctionName())
                                        .arguments(argsJson)
                                        .build())
                                .build());
                    }

                    // 暂时将完整 content 作为可见内容返回（不再截掉 JSON 部分）
                    return new ToolCallResponse(content, toolCalls, "stop", null, 0L);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });
        } catch (Exception e) {
            log.error("{} Unexpected error in askToolWithChatClientStructParse: {}", context.getRequestId(), e.getMessage(), e);
            CompletableFuture<ToolCallResponse> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * 用于 BeanOutputConverter 约束工具调用计划 JSON 结构的中间类型
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolPlan {
        /**
         * 工具名称，如 "file_tool"、"code_interpreter" 等
         */
        private String functionName;
        /**
         * 该工具调用的参数对象，键值对形式
         */
        private Map<String, Object> arguments;
    }

    /**
     * 调用 OpenAI API（抽象方法，实际实现需要在子类中提供）
     */
    protected CompletableFuture<String> callOpenAI(Map<String, Object> params) {
        return callOpenAI(params, 300); // 默认超时时间为 300 秒
    }

    /**
     * 调用 OpenAI API（抽象方法，实际实现需要在子类中提供）
     */
    protected CompletableFuture<String> callOpenAI(Map<String, Object> params, int timeout) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .build();

            String apiEndpoint = baseUrl + interfaceUrl;

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    objectMapper.writeValueAsString(params)
            );

            Request.Builder requestBuilder = new Request.Builder()
                    .url(apiEndpoint)
                    .post(body);

            // 添加适当的认证头
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);

            Request request = requestBuilder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            future.completeExceptionally(
                                    new IOException("Unexpected response code: " + response)
                            );
                        } else {
                            future.complete(responseBody.string());
                        }
                    }
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 调用 OpenAI 流式 API（抽象方法，实际实现需要在子类中提供）
     */
    public CompletableFuture<ToolCallResponse> callOpenAIFunctionCallStream(AgentContext context, Map<String, Object> params) {
        CompletableFuture<ToolCallResponse> future = new CompletableFuture<>();
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(300000, TimeUnit.SECONDS)
                    .readTimeout(300000, TimeUnit.SECONDS)
                    .writeTimeout(300000, TimeUnit.SECONDS)
                    .build();

            String apiEndpoint = baseUrl + interfaceUrl;
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    objectMapper.writeValueAsString(params)
            );
            Request.Builder requestBuilder = new Request.Builder()
                    .url(apiEndpoint)
                    .post(body);
            // 添加适当的认证头
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
            Request request = requestBuilder.build();

            GenieConfig genieConfig = SpringContextHolder.getApplicationContext().getBean(GenieConfig.class);
            String[] interval = genieConfig.getMessageInterval().getOrDefault("llm", "1,3").split(",");
            int firstInterval = "struct_parse".equals(functionCallType) ? Math.max(3, Integer.parseInt(interval[0])) : Integer.parseInt(interval[0]);
            int sendInterval = Integer.parseInt(interval[1]);

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    boolean isFirstToken = true;
                    boolean isContent = true;
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            log.error("{} ask tool stream response error or empty", context.getRequestId());
                            future.completeExceptionally(new IOException("Unexpected response code: " + response));
                            return;
                        }

                        String messageId = StringUtil.getUUID();
                        StringBuilder stringBuilder = new StringBuilder();
                        StringBuilder stringBuilderAll = new StringBuilder();
                        int index = 1;
                        Map<Integer, OpenAIToolCall> openToolCallsMap = new HashMap<>();
                        String line;
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(responseBody.byteStream())
                        );
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (data.equals("[DONE]")) {
                                    break;
                                }
                                if (isFirstToken) {
                                    isFirstToken = false;
                                }
                                try {
                                    JsonNode chunk = objectMapper.readTree(data);
                                    if (chunk.has("choices") && !chunk.get("choices").isEmpty()) {
                                        for (JsonNode element : chunk.get("choices")) {
                                            OpenAIChoice choice = objectMapper.convertValue(element, OpenAIChoice.class);
                                            // content
                                            if (Objects.nonNull(choice.delta.content)) {
                                                String content = choice.delta.content;
                                                // log.info("{} recv content data: >>{}<<", context.getRequestId(), content);
                                                if (!isContent) { // 忽略json内容
                                                    stringBuilderAll.append(content);
                                                    continue;
                                                }
                                                stringBuilder.append(content);
                                                stringBuilderAll.append(content);
                                                if ("struct_parse".equals(functionCallType)) {
                                                    if (stringBuilderAll.toString().contains("```json")) {
                                                        isContent = false;
                                                    }
                                                }
                                                if (index == firstInterval || index % sendInterval == 0) {
                                                    context.getPrinter().send(messageId, context.getStreamMessageType(), stringBuilder.toString(), false);
                                                    stringBuilder.setLength(0);
                                                }
                                                index++;
                                            }
                                            // tool call
                                            if (Objects.nonNull(choice.delta.tool_calls)) {
                                                List<OpenAIToolCall> openAIToolCalls = choice.delta.tool_calls;
                                                // log.info("{} recv tool call data: {}", context.getRequestId(), openAIToolCalls);
                                                for (OpenAIToolCall toolCall : openAIToolCalls) {
                                                    OpenAIToolCall currentToolCall = openToolCallsMap.get(toolCall.index);
                                                    if (Objects.isNull(currentToolCall)) {
                                                        currentToolCall = new OpenAIToolCall();
                                                    }
                                                    // [{"index":0,"id":"call_j74R8JMFWTC4rW5wHJ0TtmNU","type":"function","function":{"name":"planning","arguments":""}}]
                                                    if (Objects.nonNull(toolCall.id)) {
                                                        currentToolCall.id = toolCall.id;
                                                    }
                                                    if (Objects.nonNull(toolCall.type)) {
                                                        currentToolCall.type = toolCall.type;
                                                    }
                                                    if (Objects.nonNull(toolCall.function)) {
                                                        if (Objects.nonNull(toolCall.function.name)) {
                                                            currentToolCall.function = toolCall.function;
                                                        }
                                                        if (Objects.nonNull(toolCall.function.arguments)) {
                                                            currentToolCall.function.arguments += toolCall.function.arguments;
                                                        }
                                                    }
                                                    openToolCallsMap.put(toolCall.index, currentToolCall);
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("{} process response error", context.getRequestId(), e);
                                }
                            }
                        }

                        String contentAll = stringBuilderAll.toString();
                        if ("struct_parse".equals(functionCallType)) {
                            int stopPos = stringBuilder.indexOf("```json");
                            context.getPrinter().send(messageId, context.getStreamMessageType(),
                                    stringBuilder.substring(0, stopPos >= 0 ? stopPos : stringBuilder.length()),
                                    false);
                            stopPos = stringBuilderAll.indexOf("```json");
                            contentAll = stringBuilderAll.substring(0, stopPos >= 0 ? stopPos : stringBuilderAll.length());
                            if (!contentAll.isEmpty()) {
                                context.getPrinter().send(messageId, context.getStreamMessageType(), contentAll, true);
                            }
                        } else { // function_call
                            if (!contentAll.isEmpty()) {
                                context.getPrinter().send(messageId, context.getStreamMessageType(), stringBuilder.toString(), false);
                                context.getPrinter().send(messageId, context.getStreamMessageType(), stringBuilderAll.toString(), true);
                            }
                        }

                        List<ToolCall> toolCalls = new ArrayList<>();
                        if ("struct_parse".equals(functionCallType)) {
                            // 匹配方式: 直接匹配 ```json ... ``` 代码块
                            String pattern = "```json\\s*([\\s\\S]*?)\\s*```";
                            List<String> matches = findMatches(stringBuilderAll.toString(), pattern);
                            if (!matches.isEmpty()) {
                                for (String match : matches) {
                                    ToolCall oneToolCall = parseToolCall(context, match);
                                    if (Objects.nonNull(oneToolCall)) {
                                        toolCalls.add(oneToolCall);
                                    }
                                }
                            }
                        } else { // function call
                            for (OpenAIToolCall toolCall : openToolCallsMap.values()) {
                                toolCalls.add(ToolCall.builder()
                                        .id(toolCall.id)
                                        .type(toolCall.type)
                                        .function(ToolCall.Function.builder()
                                                .name(toolCall.function.name)
                                                .arguments(toolCall.function.arguments)
                                                .build())
                                        .build());
                            }
                        }

                        log.info("{} call llm stream response {} {}", context.getRequestId(), stringBuilderAll, JSON.toJSONString(toolCalls));

                        ToolCallResponse fullResponse = ToolCallResponse.builder()
                                .toolCalls(toolCalls)
                                .content(contentAll)
                                .build();
                        future.complete(fullResponse);

                    } catch (Exception e) {
                        log.error("{} ask tool stream error", context.getRequestId(), e);
                        future.completeExceptionally(e);
                    }
                }
            });

        } catch (Exception e) {
            log.error("{} ask tool stream error", context.getRequestId(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 调用 OpenAI 流式 API（抽象方法，实际实现需要在子类中提供）
     */
    public CompletableFuture<ToolCallResponse> callClaudeFunctionCallStream(AgentContext context, Map<String, Object> params) {
        CompletableFuture<ToolCallResponse> future = new CompletableFuture<>();
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(3000000, TimeUnit.SECONDS)
                    .readTimeout(3000000, TimeUnit.SECONDS)
                    .writeTimeout(300000, TimeUnit.SECONDS)
                    .build();

            String apiEndpoint = baseUrl + interfaceUrl;
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    objectMapper.writeValueAsString(params)
            );
            Request.Builder requestBuilder = new Request.Builder()
                    .url(apiEndpoint)
                    .post(body);
            // 添加适当的认证头
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
            Request request = requestBuilder.build();

            GenieConfig genieConfig = SpringContextHolder.getApplicationContext().getBean(GenieConfig.class);
            String[] interval = genieConfig.getMessageInterval().getOrDefault("llm", "1,3").split(",");
            int firstInterval = "struct_parse".equals(functionCallType) ? Math.max(3, Integer.parseInt(interval[0])) : Integer.parseInt(interval[0]);
            int sendInterval = Integer.parseInt(interval[1]);

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    boolean isFirstToken = true;
                    boolean isContent = true;
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            log.error("{} ask tool stream response error or empty", context.getRequestId());
                            future.completeExceptionally(new IOException("Unexpected response code: " + response));
                            return;
                        }

                        String messageId = StringUtil.getUUID();
                        StringBuilder stringBuilder = new StringBuilder();
                        StringBuilder stringBuilderAll = new StringBuilder();
                        StringBuilder stringBuilderTool = new StringBuilder();

                        Integer index = 1;
                        Map<Integer, OpenAIToolCall> openToolCallsMap = new HashMap<>();
                        String line;
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(responseBody.byteStream())
                        );
                        String id = "";
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (data.equals("[DONE]")) {
                                    break;
                                }

                                try {
                                    // log.info("{} recv data: >>{}<<", context.getRequestId(), data);
                                    JsonNode chunk = objectMapper.readTree(data);
                                    ClaudeResponse claudeResponse = objectMapper.convertValue(chunk, ClaudeResponse.class);

                                    if (Objects.isNull(claudeResponse.delta)) {
                                        continue;
                                    }

                                    if (isFirstToken) {
                                        isFirstToken = false;
                                    }

                                    // content
                                    if ("text_delta".equals(claudeResponse.delta.type)) {
                                        String content = claudeResponse.delta.text;

                                        if (!isContent) { // 忽略json内容
                                            stringBuilderAll.append(content);
                                            continue;
                                        }
                                        // log.info("{} recv content data: >>{}<<", context.getRequestId(), content);
                                        stringBuilder.append(content);
                                        stringBuilderAll.append(content);
                                        if ("struct_parse".equals(functionCallType)) {
                                            if (stringBuilderAll.toString().contains("```json")) {
                                                isContent = false;
                                            }
                                        }
                                        if (index == firstInterval || index % sendInterval == 0) {
                                            context.getPrinter().send(messageId, context.getStreamMessageType(), stringBuilder.toString(), false);
                                            stringBuilder.setLength(0);
                                        }
                                        index++;
                                    }
                                    // tool call
                                    if ("input_json_delta".equals(claudeResponse.delta.type)) {
                                        String content = claudeResponse.delta.partial_json;
                                        // log.info("{} recv tool call data: >>{}<<", context.getRequestId(), content);
                                        stringBuilderTool.append(content);
                                    }
                                    // id
                                    id = claudeResponse.id;

                                } catch (Exception e) {
                                    log.error("{} process response error", context.getRequestId(), e);
                                }
                            }
                        }

                        String contentAll = stringBuilderAll.toString();
                        if ("struct_parse".equals(functionCallType)) {
                            int stopPos = stringBuilder.indexOf("```json");
                            context.getPrinter().send(messageId, context.getStreamMessageType(),
                                    stringBuilder.substring(0, stopPos >= 0 ? stopPos : stringBuilder.length()),
                                    false);
                            stopPos = stringBuilderAll.indexOf("```json");
                            contentAll = stringBuilderAll.substring(0, stopPos >= 0 ? stopPos : stringBuilderAll.length());
                            if (!contentAll.isEmpty()) {
                                context.getPrinter().send(messageId, context.getStreamMessageType(), contentAll, true);
                            }
                        } else { // function call
                            if (!contentAll.isEmpty()) {
                                context.getPrinter().send(messageId, context.getStreamMessageType(), stringBuilder.toString(), false);
                                context.getPrinter().send(messageId, context.getStreamMessageType(), stringBuilderAll.toString(), true);
                            }
                        }
                        List<ToolCall> toolCalls = new ArrayList<>();
                        if ("struct_parse".equals(functionCallType)) {
                            // 匹配方式: 直接匹配 ```json ... ``` 代码块
                            String pattern = "```json\\s*([\\s\\S]*?)\\s*```";
                            List<String> matches = findMatches(stringBuilderAll.toString(), pattern);
                            if (!matches.isEmpty()) {
                                for (String match : matches) {
                                    ToolCall oneToolCall = parseToolCall(context, match);
                                    if (Objects.nonNull(oneToolCall)) {
                                        toolCalls.add(oneToolCall);
                                    }
                                }
                            }
                        } else { // function_call
                            JsonNode arguments = objectMapper.readTree(stringBuilderTool.toString());
                            if (!stringBuilderTool.toString().isEmpty() && arguments.hasNonNull("function_name")) {
                                OpenAIToolCall currentToolCall = new OpenAIToolCall();
                                currentToolCall.id = id;
                                currentToolCall.type = "function";
                                currentToolCall.function = new OpenAIFunction();
                                currentToolCall.function.name = arguments.get("function_name").asText();
                                currentToolCall.function.arguments = stringBuilderTool.toString();
                                openToolCallsMap.put(0, currentToolCall); // claude only call one function
                                for (OpenAIToolCall toolCall : openToolCallsMap.values()) {
                                    toolCalls.add(ToolCall.builder()
                                            .id(toolCall.id)
                                            .type(toolCall.type)
                                            .function(ToolCall.Function.builder()
                                                    .name(toolCall.function.name)
                                                    .arguments(toolCall.function.arguments)
                                                    .build())
                                            .build());
                                }
                            }
                        }

                        log.info("{} call llm stream response {} tool calls {}", context.getRequestId(), stringBuilderAll, JSON.toJSONString(toolCalls));

                        future.complete(ToolCallResponse.builder()
                                .content(contentAll)
                                .toolCalls(toolCalls)
                                .build());

                    } catch (Exception e) {
                        log.error("{} ask tool stream error", context.getRequestId(), e);
                        future.completeExceptionally(e);
                    }
                }
            });

        } catch (Exception e) {
            log.error("{} ask tool stream error", context.getRequestId(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 调用 OpenAI 流式 API（抽象方法，实际实现需要在子类中提供）
     */
    protected CompletableFuture<String> callOpenAIStream(Map<String, Object> params) {
        // 这里是一个简化的流式请求实现示例
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder collectedMessages = new StringBuilder();

        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(300000, TimeUnit.SECONDS)
                    .readTimeout(300000, TimeUnit.SECONDS)
                    .writeTimeout(300000, TimeUnit.SECONDS)
                    .build();

            String apiEndpoint = baseUrl + interfaceUrl;

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    objectMapper.writeValueAsString(params)
            );

            Request.Builder requestBuilder = new Request.Builder()
                    .url(apiEndpoint)
                    .post(body);

            // 添加适当的认证头
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);

            Request request = requestBuilder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            future.completeExceptionally(
                                    new IOException("Unexpected response code: " + response)
                            );
                            return;
                        }

                        if (responseBody != null) {
                            String line;

                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(responseBody.byteStream())
                            );

                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    if (data.equals("[DONE]")) {
                                        break;
                                    }

                                    try {
                                        JsonNode chunk = objectMapper.readTree(data);
                                        if (chunk.has("choices") && !chunk.get("choices").isEmpty()) {
                                            JsonNode choice = chunk.get("choices").get(0);
                                            if (choice.has("delta") && choice.get("delta").has("content")) {
                                                String content = choice.get("delta").get("content").asText();
                                                collectedMessages.append(content);
                                                log.info("recv data: {}", content);
                                            }
                                        }
                                    } catch (Exception e) {
                                        // 忽略非 JSON 数据
                                    }
                                }
                            }

                            String fullResponse = collectedMessages.toString().trim();

                            if (fullResponse.isEmpty()) {
                                future.completeExceptionally(
                                        new IllegalArgumentException("Empty response from streaming LLM")
                                );
                            } else {
                                future.complete(fullResponse);
                            }
                        } else {
                            future.completeExceptionally(
                                    new IOException("Empty response body")
                            );
                        }
                    }
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }


    /**
     * 查找匹配的工具调用
     */
    private List<String> findMatches(String text, String pattern) {
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(text);
        List<String> matches = new ArrayList<>();
        while (m.find()) {
            matches.add(m.group(1));
        }
        return matches;
    }

    /**
     * 解析工具调用JSON
     */
    private ToolCall parseToolCall(AgentContext context, String jsonContent) {
        try {
            JSONObject jsonObj = JSON.parseObject(jsonContent);
            String toolName = jsonObj.getString("function_name");
            jsonObj.remove("function_name");
            return ToolCall.builder()
                    .id(StringUtil.getUUID())
                    .function(ToolCall.Function.builder()
                            .name(toolName)
                            .arguments(JSON.toJSONString(jsonObj))
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("{} parse tool call error {}", context.getRequestId(), jsonContent);
        }
        return null;
    }

    /**
     * LLM 响应类
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ToolCallResponse {
        private String content;
        private List<ToolCall> toolCalls;
        private String finishReason;
        private Integer totalTokens;
        private long duration;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAIChoice {
        private Integer index;
        private OpenAIDelta delta;
        private Object logprobs;
        private String finish_reason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAIDelta {
        private String content;
        private List<OpenAIToolCall> tool_calls;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAIToolCall {
        private Integer index;
        private String id;
        private String type;
        private OpenAIFunction function;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAIFunction {
        private String name;
        private String arguments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClaudeResponse {
        private ClaudeDelta delta;
        private String arguments;
        private String id;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClaudeDelta {
        private String text;
        private String partial_json;
        private String type;
    }


}