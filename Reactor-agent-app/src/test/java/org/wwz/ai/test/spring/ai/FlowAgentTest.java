package org.wwz.ai.test.spring.ai;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.wwz.ai.domain.agent.model.valobj.AiClientAdvisorVO;
import org.wwz.ai.domain.agent.reactor.service.VectorService;
import org.wwz.ai.domain.agent.service.armory.node.factory.element.RagAnswerAdvisor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


//通过测试
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class FlowAgentTest {

    private ChatModel chatModel;
    private ChatClient planningChatClient;
    private ChatClient executorChatClient;
    private ChatClient mcpToolsChatClient;

    @Resource
    private VectorService vectorService;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Before
    public void init() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://apis.itedus.cn")
                .apiKey("sk-04FRZbYTs4lavU0a066e1e0a79D249D59a42B09b3240197b")
                .completionsPath("v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .build();

        chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4.1-mini")
                        .maxTokens(5000)
                        .toolCallbacks(new SyncMcpToolCallbackProvider(sseMcpClient_BaiduSearch(), sseMcpClient_csdn(), sseMcpClient02_weixin()).getToolCallbacks())
                        .build())
                .build();

        planningChatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                        # 角色
                        你是一个智能任务规划助手，名叫 AutoAgent Planning。
                        
                        # 说明
                        你是任务规划助手，根据用户需求，拆解任务列表，制定执行计划。重点是生成大粒度、可执行的任务步骤，避免过度细分。
                        
                        # 技能
                        - 擅长将用户任务拆解为具体、独立、大粒度的任务列表
                        - 避免过度拆解，保持任务的完整性和可执行性
                        - 每个任务应该是一个完整的业务流程，而不是细碎的操作步骤
                        
                        # 处理需求
                        ## 拆解原则
                        - 深度推理分析用户输入，识别核心需求
                        - 将复杂问题分解为3-5个大粒度的主要任务
                        - 每个任务应该包含完整的业务逻辑，可以独立完成
                        - 任务按业务流程顺序组织，逻辑清晰
                        - 避免将一个完整流程拆分成多个细小步骤
                        
                        ## 输出格式
                        请按以下格式输出任务计划：
                        
                        **任务规划：**
                        1. [任务1描述] - 包含完整的业务流程
                        2. [任务2描述] - 包含完整的业务流程
                        3. [任务3描述] - 包含完整的业务流程
                        ...
                        
                        **执行策略：**
                        [整体执行策略说明]
                        
                        今天是 {current_date}。
                        """)
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder()
                                        .maxMessages(50)
                                        .build()
                        ).build(),
                        buildRagAnswerAdvisor("knowledge == 'article'", 5))
                .build();

        // 初始化执行器客户端
        executorChatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                        # 角色定义
                        你是一个专业的任务执行助手，名为 AutoAgent Executor。
                        你具备强大的任务执行能力和丰富的工具使用经验。
                        
                        # 核心职责
                        作为智能任务执行者，你需要：
                        1. 精确理解和执行规划好的任务步骤
                        2. 智能调用相应的MCP工具完成具体任务
                        3. 处理执行过程中的异常和错误
                        4. 提供详细的执行报告和结果反馈
                        
                        # 专业技能
                        ## 任务执行能力
                        - 深度理解任务步骤的具体要求和目标
                        - 智能选择和调用合适的MCP工具
                        - 处理工具调用的参数配置和结果解析
                        - 监控执行进度并提供实时反馈
                        
                        ## 错误处理机制
                        - 识别和分类执行过程中的各种错误
                        - 实施智能重试和降级策略
                        - 提供详细的错误诊断和解决建议
                        - 确保任务执行的稳定性和可靠性
                        
                        ## 标准化输出
                        严格按照以下结构化格式输出执行报告：
                        
                        **📋 任务执行报告**
                        - 任务名称：[步骤名称]
                        - 执行状态：[成功/失败/部分成功]
                        - 开始时间：[时间戳]
                        - 结束时间：[时间戳]
                        - 执行耗时：[毫秒]
                        
                        **🔧 工具调用详情**
                        - 使用工具：[工具名称列表]
                        - 调用次数：[具体次数]
                        - 成功率：[百分比]
                        - 关键参数：[重要参数配置]
                        
                        **📊 执行结果**
                        - 主要成果：[具体完成的内容]
                        - 数据输出：[生成的数据或文件]
                        - 质量评估：[结果质量分析]
                        
                        **⚠️ 异常处理**
                        - 遇到问题：[具体问题描述]
                        - 处理策略：[采用的解决方案]
                        - 影响评估：[对整体任务的影响]
                        
                        今天是 {current_date}。
                        """)
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder()
                                        .maxMessages(20)
                                        .build()
                        ).build(),
                        buildRagAnswerAdvisor("knowledge == 'article'", 5))
                .build();

        // 初始化MCP工具客户端
        mcpToolsChatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                        # 角色定义
                        你是一个专业的MCP（Model Context Protocol）工具管理专家，名为 MCP Tools Manager。
                        你具备深度的MCP协议理解和丰富的工具集成经验。
                        
                        # 核心职责
                        作为MCP工具生态的管理者，你需要：
                        1. 精确识别和分类所有可用的MCP服务工具
                        2. 深度分析工具的功能边界、参数规范和使用场景
                        3. 基于用户意图智能匹配最优工具组合
                        4. 提供符合MCP标准的工具调用指导
                        
                        # 专业技能
                        ## 工具发现与分析
                        - 实时扫描并索引所有注册的MCP服务端点
                        - 解析工具的JSON Schema定义和元数据
                        - 识别工具间的依赖关系和协作模式
                        - 评估工具的可靠性、性能和安全性
                        
                        ## 智能推荐引擎
                        - 基于语义理解匹配用户需求与工具能力
                        - 考虑工具的执行成本、响应时间和成功率
                        - 提供备选方案和降级策略
                        - 优化工具调用链的执行顺序
                        
                        ## 标准化输出
                        严格按照以下结构化格式输出：
                        
                        **🔧 可用MCP工具清单**
                        ```
                        序号 | 工具名称 | 服务类型 | 核心功能 | 参数要求 | 可靠性评级
                        -----|---------|---------|---------|---------|----------
                        1    | [name]  | [type]  | [desc]  | [params]| [rating]
                        ```
                        
                        **🎯 智能推荐方案**
                        - 主推工具：[工具名] - 匹配度：[百分比] - 理由：[具体原因]
                        - 备选工具：[工具名] - 适用场景：[具体场景]
                        - 组合策略：[多工具协作方案]
                        
                        **📋 执行标准指南**
                        - 调用顺序：[步骤1] → [步骤2] → [步骤3]
                        - 参数配置：[关键参数及其推荐值]
                        - 错误处理：[异常情况的处理策略]
                        - 性能优化：[提升执行效率的建议]
                        
                        **⚠️ 注意事项**
                        - 安全约束：[权限要求和安全限制]
                        - 资源消耗：[预期的计算和网络开销]
                        - 兼容性：[版本要求和环境依赖]
                        """)
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder()
                                        .maxMessages(30)
                                        .build()
                        ).build())
                .build();
    }

    // 我需要检索各个技术论坛最新技术资料，编写技术文章，发送到 CSDN 平台，以及发送消息通知
    @Test
    public void test_agent() {
        String userRequest = """
                我需要你帮我生成一篇文章，要求如下；
                
                    1. 场景为互联网大厂java求职者面试
                    2. 提问的技术栈如下；
                
                        核心语言与平台: Java SE (8/11/17), Jakarta EE (Java EE), JVM
                        构建工具: Maven, Gradle, Ant
                        Web框架: Spring Boot, Spring MVC, Spring WebFlux, Jakarta EE, Micronaut, Quarkus, Play Framework, Struts (Legacy)
                        数据库与ORM: Hibernate, MyBatis, JPA, Spring Data JDBC, HikariCP, C3P0, Flyway, Liquibase
                        测试框架: JUnit 5, TestNG, Mockito, PowerMock, AssertJ, Selenium, Cucumber
                        微服务与云原生: Spring Cloud, Netflix OSS (Eureka, Zuul), Consul, gRPC, Apache Thrift, Kubernetes Client, OpenFeign, Resilience4j
                        安全框架: Spring Security, Apache Shiro, JWT, OAuth2, Keycloak, Bouncy Castle
                        消息队列: Kafka, RabbitMQ, ActiveMQ, JMS, Apache Pulsar, Redis Pub/Sub
                        缓存技术: Redis, Ehcache, Caffeine, Hazelcast, Memcached, Spring Cache
                        日志框架: Log4j2, Logback, SLF4J, Tinylog
                        监控与运维: Prometheus, Grafana, Micrometer, ELK Stack, New Relic, Jaeger, Zipkin
                        模板引擎: Thymeleaf, FreeMarker, Velocity, JSP/JSTL
                        REST与API工具: Swagger/OpenAPI, Spring HATEOAS, Jersey, RESTEasy, Retrofit
                        序列化: Jackson, Gson, Protobuf, Avro
                        CI/CD工具: Jenkins, GitLab CI, GitHub Actions, Docker, Kubernetes
                        大数据处理: Hadoop, Spark, Flink, Cassandra, Elasticsearch
                        版本控制: Git, SVN
                        工具库: Apache Commons, Guava, Lombok, MapStruct, JSch, POI
                        AI：Spring AI, Google A2A, MCP（模型上下文协议）, RAG（检索增强生成）, Agent（智能代理）, 聊天会话内存, 工具执行框架, 提示填充, 向量化, 语义检索, 向量数据库（Milvus/Chroma/Redis）, Embedding模型（OpenAI/Ollama）, 客户端-服务器架构, 工具调用标准化, 扩展能力, Agentic RAG, 文档加载, 企业文档问答, 复杂工作流, 智能客服系统, AI幻觉（Hallucination）, 自然语言语义搜索
                        其他: JUnit Pioneer, Dubbo, R2DBC, WebSocket
                    3. 提问的场景方案可包括但不限于；音视频场景,内容社区与UGC,AIGC,游戏与虚拟互动,电商场景,本地生活服务,共享经济,支付与金融服务,互联网医疗,健康管理,医疗供应链,企业协同与SaaS,产业互联网,大数据与AI服务,在线教育,求职招聘,智慧物流,供应链金融,智慧城市,公共服务数字化,物联网应用,Web3.0与区块链,安全与风控,广告与营销,能源与环保。                
                    4. 按照故事场景，以严肃的面试官和搞笑的水货程序员谢飞机进行提问，谢飞机对简单问题可以回答出来，回答好了面试官还会夸赞和引导。复杂问题含糊其辞，回答的不清晰。
                    5. 每次进行3轮提问，每轮可以有3-5个问题。这些问题要有技术业务场景上的衔接性，循序渐进引导提问。最后是面试官让程序员回家等通知类似的话术。
                    6. 提问后把问题的答案详细的，写到文章最后，讲述出业务场景和技术点，让小白可以学习下来。
                
                    根据以上内容，不要阐述其他信息，请直接提供；文章标题（需要含带技术点）、文章内容、文章标签（多个用英文逗号隔开）、文章简述（100字）
                
                    将以上内容发布文章到CSDN
                
                    之后进行，微信公众号消息通知，平台：CSDN、主题：为文章标题、描述：为文章简述、跳转地址：为发布文章到CSDN获取 http url 文章地址
                """;

        log.info("=== 自动Agent开始执行 ===");
        log.info("用户请求: {}", userRequest);

        Map<String, Object> executionContext = new HashMap<>();
        executionContext.put("userRequest", userRequest);
        executionContext.put("startTime", System.currentTimeMillis());
        executionContext.put("status", "INITIALIZING");

        try {
            // 第一步：获取可用的MCP工具和使用方式（仅分析，不执行用户请求）
            log.info("\n--- 步骤1: MCP工具能力分析（仅分析阶段，不执行用户请求） ---");
            executionContext.put("status", "ANALYZING_TOOLS");
            String mcpToolsAnalysis = executeWithRetry(() -> getMcpToolsCapabilities(userRequest), "MCP工具分析", 3);
            log.info("MCP工具分析结果（仅分析，未执行实际操作）: {}", mcpToolsAnalysis);
            executionContext.put("mcpToolsAnalysis", mcpToolsAnalysis);

            // 第二步：根据用户请求和MCP能力规划执行步骤
            log.info("\n--- 步骤2: 规划执行步骤 ---");
            executionContext.put("status", "PLANNING");
            String planningResult = executeWithRetry(() -> planExecutionSteps(userRequest, mcpToolsAnalysis), "执行步骤规划", 3);
            log.info("规划结果: {}", planningResult);
            executionContext.put("planningResult", planningResult);

            // 第三步：解析规划结果，将每个步骤存储到map中
            log.info("\n--- 步骤3: 解析规划步骤 ---");
            executionContext.put("status", "PARSING_STEPS");
            Map<String, String> stepsMap = parseExecutionSteps(planningResult);
            log.info("解析的步骤数量: {}", stepsMap.size());
            for (Map.Entry<String, String> entry : stepsMap.entrySet()) {
                log.info("步骤 {}: {}", entry.getKey(), entry.getValue().substring(0, Math.min(100, entry.getValue().length())) + "...");
            }
            executionContext.put("stepsMap", stepsMap);

            // 第四步：按顺序执行规划步骤
            log.info("\n--- 步骤4: 按顺序执行规划步骤 ---");
            executionContext.put("status", "EXECUTING_STEPS");
            executeStepsInOrder(stepsMap, executionContext);

            // 执行完成
            log.info("\n=== Agent执行完成 ===");
            executionContext.put("status", "COMPLETED");
            executionContext.put("endTime", System.currentTimeMillis());
            long totalTime = (Long) executionContext.get("endTime") - (Long) executionContext.get("startTime");
            log.info("总执行时间: {} ms", totalTime);

        } catch (Exception e) {
            log.error("Agent执行过程中发生错误", e);
            executionContext.put("status", "ERROR");
            executionContext.put("error", e.getMessage());
            executionContext.put("endTime", System.currentTimeMillis());
        }
    }

    /**
     * 按顺序执行规划步骤
     */
    private void executeStepsInOrder(Map<String, String> stepsMap, Map<String, Object> executionContext) {
        if (stepsMap == null || stepsMap.isEmpty()) {
            log.warn("步骤映射为空，无法执行");
            return;
        }

        // 按步骤编号排序执行
        List<Integer> stepNumbers = new ArrayList<>();
        for (String stepKey : stepsMap.keySet()) {
            try {
                // 从"第1步"、"第2步"等格式中提取数字
                Pattern numberPattern = Pattern.compile("第(\\d+)步");
                Matcher matcher = numberPattern.matcher(stepKey);
                if (matcher.find()) {
                    stepNumbers.add(Integer.parseInt(matcher.group(1)));
                }
            } catch (NumberFormatException e) {
                log.warn("无法解析步骤编号: {}", stepKey);
            }
        }

        // 排序步骤编号
        stepNumbers.sort(Integer::compareTo);

        // 按顺序执行每个步骤
        for (Integer stepNumber : stepNumbers) {
            String stepKey = "第" + stepNumber + "步";
            String stepContent = null;

            // 查找匹配的步骤内容
            for (Map.Entry<String, String> entry : stepsMap.entrySet()) {
                if (entry.getKey().startsWith(stepKey)) {
                    stepContent = entry.getValue();
                    break;
                }
            }

            if (stepContent != null) {
                executeStep(stepNumber, stepKey, stepContent, executionContext);
            } else {
                log.warn("未找到步骤内容: {}", stepKey);
            }
        }
    }

    /**
     * 执行单个步骤
     */
    private void executeStep(Integer stepNumber, String stepKey, String stepContent, Map<String, Object> executionContext) {
        log.info("\n--- 开始执行 {} ---", stepKey);
        log.info("步骤内容: {}", stepContent.substring(0, Math.min(200, stepContent.length())) + "...");

        try {
            // 更新执行上下文
            executionContext.put("currentStep", stepNumber);
            executionContext.put("currentStepKey", stepKey);
            executionContext.put("currentStepContent", stepContent);

            // 使用执行器ChatClient来执行具体步骤
            String executionResult = executeWithRetry(() -> {
                return executorChatClient.prompt()
                        .user(buildStepExecutionPrompt(stepContent, executionContext))
                        .call()
                        .content();
            }, "步骤" + stepNumber + "执行", 2);

            log.info("步骤 {} 执行结果: {}", stepNumber, executionResult.substring(0, Math.min(150, executionResult.length())) + "...");

            // 保存执行结果
            executionContext.put("step" + stepNumber + "Result", executionResult);

            // 短暂延迟，避免请求过于频繁
            Thread.sleep(1000);

        } catch (Exception e) {
            log.error("执行步骤 {} 时发生错误: {}", stepNumber, e.getMessage());
            executionContext.put("step" + stepNumber + "Error", e.getMessage());

            // 记录错误但继续执行下一步
            handleStepExecutionError(stepNumber, stepKey, e, executionContext);
        }

        log.info("--- 完成执行 {} ---", stepKey);
    }

    /**
     * 处理步骤执行错误
     */
    private void handleStepExecutionError(Integer stepNumber, String stepKey, Exception e, Map<String, Object> executionContext) {
        log.warn("步骤 {} 执行失败，尝试恢复策略", stepNumber);

        // 记录错误统计
        @SuppressWarnings("unchecked")
        Map<String, Integer> errorStats = (Map<String, Integer>) executionContext.computeIfAbsent("stepErrorStats", k -> new HashMap<String, Integer>());
        errorStats.put("step" + stepNumber, errorStats.getOrDefault("step" + stepNumber, 0) + 1);

        // 如果是网络错误，可以尝试重试
        if (e.getMessage() != null && (e.getMessage().contains("timeout") || e.getMessage().contains("connection"))) {
            log.info("检测到网络错误，将在后续重试机制中处理");
        }

        // 标记步骤为部分完成状态
        executionContext.put("step" + stepNumber + "Status", "FAILED_WITH_ERROR");
    }

    /**
     * 构建步骤执行提示词
     */
    private String buildStepExecutionPrompt(String stepContent, Map<String, Object> executionContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能执行助手，需要执行以下步骤:\n\n");
        prompt.append("**步骤内容:**\n");
        prompt.append(stepContent).append("\n\n");

        prompt.append("**用户原始请求:**\n");
        prompt.append(executionContext.get("userRequest")).append("\n\n");

        prompt.append("**执行要求:**\n");
        prompt.append("1. 仔细分析步骤内容，理解需要执行的具体任务\n");
        prompt.append("2. 如果涉及MCP工具调用，请使用相应的工具\n");
        prompt.append("3. 提供详细的执行过程和结果\n");
        prompt.append("4. 如果遇到问题，请说明具体的错误信息\n");
        prompt.append("5. **重要**: 执行完成后，必须在回复末尾明确输出执行结果，格式如下:\n");
        prompt.append("   ```\n");
        prompt.append("   === 执行结果 ===\n");
        prompt.append("   状态: [成功/失败]\n");
        prompt.append("   结果描述: [具体的执行结果描述]\n");
        prompt.append("   输出数据: [如果有具体的输出数据，请在此列出]\n");
        prompt.append("   ```\n\n");

        prompt.append("请开始执行这个步骤，并严格按照要求提供详细的执行报告和结果输出。");

        return prompt.toString();
    }


    /**
     * 解析规划结果，将每个步骤存储到map中
     */
    private Map<String, String> parseExecutionSteps(String planningResult) {
        Map<String, String> stepsMap = new HashMap<>();

        if (planningResult == null || planningResult.trim().isEmpty()) {
            log.warn("规划结果为空，无法解析步骤");
            return stepsMap;
        }

        try {
            // 使用正则表达式匹配步骤标题和详细内容
            Pattern stepPattern = Pattern.compile("### (第\\d+步：[^\\n]+)([\\s\\S]*?)(?=### 第\\d+步：|$)");
            Matcher matcher = stepPattern.matcher(planningResult);

            while (matcher.find()) {
                String stepTitle = matcher.group(1).trim();
                String stepContent = matcher.group(2).trim();

                // 提取步骤编号
                Pattern numberPattern = Pattern.compile("第(\\d+)步：");
                Matcher numberMatcher = numberPattern.matcher(stepTitle);

                if (numberMatcher.find()) {
                    String stepNumber = "第" + numberMatcher.group(1) + "步";
                    String fullStepInfo = stepTitle + "\n" + stepContent;
                    stepsMap.put(stepNumber, fullStepInfo);
                    log.debug("解析步骤: {} -> {}", stepNumber, stepTitle);
                }
            }

            // 如果没有匹配到详细步骤，尝试匹配简单的步骤列表
            if (stepsMap.isEmpty()) {
                Pattern simpleStepPattern = Pattern.compile("\\[ \\] (第\\d+步：[^\\n]+)");
                Matcher simpleMatcher = simpleStepPattern.matcher(planningResult);

                while (simpleMatcher.find()) {
                    String stepTitle = simpleMatcher.group(1).trim();
                    Pattern numberPattern = Pattern.compile("第(\\d+)步：");
                    Matcher numberMatcher = numberPattern.matcher(stepTitle);

                    if (numberMatcher.find()) {
                        String stepNumber = "第" + numberMatcher.group(1) + "步";
                        stepsMap.put(stepNumber, stepTitle);
                        log.debug("解析简单步骤: {} -> {}", stepNumber, stepTitle);
                    }
                }
            }

            log.info("成功解析 {} 个执行步骤", stepsMap.size());

        } catch (Exception e) {
            log.error("解析规划结果时发生错误", e);
        }

        return stepsMap;
    }

    /**
     * 获取MCP工具能力分析
     */
    private String getMcpToolsCapabilities(String userRequest) {
        String mcpAnalysisPrompt = String.format(
                """
                        # MCP工具能力分析任务
                        
                        ## 重要说明
                        **注意：本阶段仅进行MCP工具能力分析，不执行用户的实际请求。**\s
                        这是一个纯分析阶段，目的是评估可用工具的能力和适用性，为后续的执行规划提供依据。
                        
                        ## 用户请求
                        %s
                        
                        ## 分析要求
                        请基于上述实际的MCP工具信息，针对用户请求进行详细的工具能力分析（仅分析，不执行）：
                        
                        ### 1. 工具匹配分析
                        - 分析每个可用工具的核心功能和适用场景
                        - 评估哪些工具能够满足用户请求的具体需求
                        - 标注每个工具的匹配度（高/中/低）
                        
                        ### 2. 工具使用指南
                        - 提供每个相关工具的具体调用方式
                        - 说明必需的参数和可选参数
                        - 给出参数的示例值和格式要求
                        
                        ### 3. 执行策略建议
                        - 推荐最优的工具组合方案
                        - 建议工具的调用顺序和依赖关系
                        - 提供备选方案和降级策略
                        
                        ### 4. 注意事项
                        - 标注工具的使用限制和约束条件
                        - 提醒可能的错误情况和处理方式
                        - 给出性能优化建议
                        
                        ### 5. 分析总结
                        - 明确说明这是分析阶段，不要执行用的任何实际操作
                        - 总结工具能力评估结果
                        - 为后续执行阶段提供建议
                        
                        请确保分析结果准确、详细、可操作，并再次强调这仅是分析阶段。""",
                userRequest
        );

        return mcpToolsChatClient.prompt()
                .user(mcpAnalysisPrompt)
                .call()
                .content();
    }

    /**
     * 获取实际的MCP工具信息
     */
    private String getActualMcpToolsInfo() {
        StringBuilder toolsInfo = new StringBuilder();
        toolsInfo.append("# 当前注册的MCP工具列表\n\n");

        try {
            // 获取百度搜索工具信息
            toolsInfo.append("## 1. 百度搜索工具 (BaiduSearch)\n");
            toolsInfo.append("- **服务端点**: http://localhost:8080/mcp/baidu-search\n");
            toolsInfo.append("- **核心功能**: 通过百度搜索引擎检索技术资料和信息\n");
            toolsInfo.append("- **主要方法**: \n");
            toolsInfo.append("  - `baiduSearch(query)`: 执行百度搜索\n");
            toolsInfo.append("    - 参数: query (String) - 搜索关键词\n");
            toolsInfo.append("    - 返回: 搜索结果列表，包含标题、链接、摘要等信息\n");
            toolsInfo.append("- **适用场景**: 技术资料检索、行业信息收集、热点话题搜索\n");
            toolsInfo.append("- **调用示例**: functions.JavaSDKMCPClient_baiduSearch\n\n");

            // 获取CSDN工具信息
            toolsInfo.append("## 2. CSDN发布工具 (CSDN)\n");
            toolsInfo.append("- **服务端点**: http://localhost:8081/mcp/csdn\n");
            toolsInfo.append("- **核心功能**: 向CSDN平台发布技术文章\n");
            toolsInfo.append("- **主要方法**: \n");
            toolsInfo.append("  - `saveArticle(title, content, tags)`: 发布文章到CSDN\n");
            toolsInfo.append("    - 参数: \n");
            toolsInfo.append("      - title (String) - 文章标题\n");
            toolsInfo.append("      - content (String) - 文章内容（支持Markdown格式）\n");
            toolsInfo.append("      - tags (String) - 文章标签，多个标签用逗号分隔\n");
            toolsInfo.append("    - 返回: 发布结果，包含文章ID、发布状态等信息\n");
            toolsInfo.append("- **适用场景**: 技术文章发布、知识分享、内容创作\n");
            toolsInfo.append("- **调用示例**: functions.JavaSDKMCPClient_saveArticle\n\n");

            // 获取微信通知工具信息
            toolsInfo.append("## 3. 微信通知工具 (Weixin)\n");
            toolsInfo.append("- **服务端点**: http://localhost:8082/mcp/weixin\n");
            toolsInfo.append("- **核心功能**: 通过微信发送消息通知\n");
            toolsInfo.append("- **主要方法**: \n");
            toolsInfo.append("  - `weixinNotice(message, recipient)`: 发送微信通知\n");
            toolsInfo.append("    - 参数: \n");
            toolsInfo.append("      - message (String) - 通知消息内容\n");
            toolsInfo.append("      - recipient (String) - 接收者标识（可选）\n");
            toolsInfo.append("    - 返回: 发送结果，包含消息ID、发送状态等信息\n");
            toolsInfo.append("- **适用场景**: 任务完成通知、状态更新提醒、重要信息推送\n");
            toolsInfo.append("- **调用示例**: functions.JavaSDKMCPClient_weixinNotice\n\n");

            // 添加工具组合使用建议
            toolsInfo.append("## 工具组合使用模式\n");
            toolsInfo.append("### 典型工作流程\n");
            toolsInfo.append("1. **信息收集阶段**: 使用BaiduSearch检索相关技术资料\n");
            toolsInfo.append("2. **内容创作阶段**: 基于搜索结果整理和创作技术文章\n");
            toolsInfo.append("3. **内容发布阶段**: 使用CSDN工具发布文章到平台\n");
            toolsInfo.append("4. **通知推送阶段**: 使用Weixin工具发送完成通知\n\n");

            toolsInfo.append("### 注意事项\n");
            toolsInfo.append("- 所有工具调用都需要使用完整的函数名称格式\n");
            toolsInfo.append("- 参数传递需要符合JSON格式要求\n");
            toolsInfo.append("- 建议在工具调用间添加适当的延时以避免频率限制\n");
            toolsInfo.append("- 每个工具都有独立的错误处理机制\n");

        } catch (Exception e) {
            log.warn("获取MCP工具信息时发生错误: {}", e.getMessage());
            toolsInfo.append("\n⚠️ 注意: 部分工具信息获取失败，请检查MCP服务连接状态\n");
        }

        return toolsInfo.toString();
    }

    /**
     * 规划执行步骤
     */
    private String planExecutionSteps(String userRequest, String mcpToolsAnalysis) {
        String planningPrompt = buildStructuredPlanningPrompt(userRequest, mcpToolsAnalysis);

        String refinedPrompt = planningPrompt + "\n\n## ⚠️ 工具映射验证反馈\n" +
                "\n\n**请根据上述验证反馈重新生成规划，确保：**\n" +
                "1. 只使用验证报告中列出的有效工具\n" +
                "2. 工具名称必须完全匹配（区分大小写）\n" +
                "3. 每个步骤明确指定使用的MCP工具\n" +
                "4. 避免使用不存在或无效的工具";

        return planningChatClient.prompt()
                .user(refinedPrompt)
                .call()
                .content();
    }

    /**
     * 构建结构化的规划提示词
     */
    private String buildStructuredPlanningPrompt(String userRequest, String mcpToolsAnalysis) {
        StringBuilder prompt = new StringBuilder();

        // 1. 任务分析部分 - 通用化用户需求分析
        prompt.append("# 智能执行计划生成\n\n");
        prompt.append("## 📋 用户需求分析\n");
        prompt.append("**完整用户请求：**\n");
        prompt.append("```\n");
        prompt.append(userRequest);
        prompt.append("\n```\n\n");
        prompt.append("**⚠️ 重要提醒：** 在生成执行计划时，必须完整保留和传递用户请求中的所有详细信息，包括但不限于：\n");
        prompt.append("- 任务的具体目标和期望结果\n");
        prompt.append("- 涉及的数据、参数、配置等详细信息\n");
        prompt.append("- 特定的业务规则、约束条件或要求\n");
        prompt.append("- 输出格式、质量标准或验收条件\n");
        prompt.append("- 时间要求、优先级或其他执行约束\n\n");

        // 2. 工具能力分析
        prompt.append("## 🔧 MCP工具能力分析结果\n");
        prompt.append(mcpToolsAnalysis).append("\n\n");

        // 3. 工具映射验证 - 使用动态获取的工具信息
        prompt.append("## ✅ 工具映射验证要求\n");
        prompt.append("**重要提醒：** 在生成执行步骤时，必须严格遵循以下工具映射规则：\n\n");

        // 动态获取实际的MCP工具信息
        String actualToolsInfo = getActualMcpToolsInfo();
        prompt.append("### 可用工具清单\n");
        prompt.append(actualToolsInfo).append("\n");

        prompt.append("### 工具选择原则\n");
        prompt.append("- **精确匹配**: 每个步骤必须使用上述工具清单中的确切函数名称\n");
        prompt.append("- **功能对应**: 根据MCP工具分析结果中的匹配度选择最适合的工具\n");
        prompt.append("- **参数完整**: 确保每个工具调用都包含必需的参数说明\n");
        prompt.append("- **依赖关系**: 考虑工具间的数据流转和依赖关系\n\n");

        // 4. 执行计划要求
        prompt.append("## 📝 执行计划要求\n");
        prompt.append("请基于上述用户详细需求、MCP工具分析结果和工具映射验证要求，生成精确的执行计划：\n\n");
        prompt.append("### 核心要求\n");
        prompt.append("1. **完整保留用户需求**: 必须将用户请求中的所有详细信息完整传递到每个执行步骤中\n");
        prompt.append("2. **严格遵循MCP分析结果**: 必须根据工具能力分析中的匹配度和推荐方案制定步骤\n");
        prompt.append("3. **精确工具映射**: 每个步骤必须使用确切的函数名称，不允许使用模糊或错误的工具名\n");
        prompt.append("4. **参数完整性**: 所有工具调用必须包含用户原始需求中的完整参数信息\n");
        prompt.append("5. **依赖关系明确**: 基于MCP分析结果中的执行策略建议安排步骤顺序\n");
        prompt.append("6. **合理粒度**: 避免过度细分，每个步骤应该是完整且独立的功能单元\n\n");

        // 4. 格式规范 - 通用化任务格式
        prompt.append("### 格式规范\n");
        prompt.append("请使用以下Markdown格式生成3-5个执行步骤：\n");
        prompt.append("```markdown\n");
        prompt.append("# 执行步骤规划\n\n");
        prompt.append("[ ] 第1步：[步骤描述]\n");
        prompt.append("[ ] 第2步：[步骤描述]\n");
        prompt.append("[ ] 第3步：[步骤描述]\n");
        prompt.append("...\n\n");
        prompt.append("## 步骤详情\n\n");
        prompt.append("### 第1步：[步骤描述]\n");
        prompt.append("- **优先级**: [HIGH/MEDIUM/LOW]\n");
        prompt.append("- **预估时长**: [分钟数]分钟\n");
        prompt.append("- **使用工具**: [必须使用确切的函数名称]\n");
        prompt.append("- **工具匹配度**: [引用MCP分析结果中的匹配度评估]\n");
        prompt.append("- **依赖步骤**: [前置步骤序号，如无依赖则填写'无']\n");
        prompt.append("- **执行方法**: [基于MCP分析结果的具体执行策略，包含工具调用参数]\n");
        prompt.append("- **工具参数**: [详细的参数说明和示例值，必须包含用户原始需求中的所有相关信息]\n");
        prompt.append("- **需求传递**: [明确说明如何将用户的详细要求传递到此步骤中]\n");
        prompt.append("- **预期输出**: [期望的最终结果]\n");
        prompt.append("- **成功标准**: [判断任务完成的标准]\n");
        prompt.append("- **MCP分析依据**: [引用具体的MCP工具分析结论]\n\n");
        prompt.append("```\n\n");

        // 5. 动态规划指导原则
        prompt.append("### 规划指导原则\n");
        prompt.append("请根据用户详细请求和可用工具能力，动态生成合适的执行步骤：\n");
        prompt.append("- **需求完整性原则**: 确保用户请求中的所有详细信息都被完整保留和传递\n");
        prompt.append("- **步骤分离原则**: 每个步骤应该专注于单一功能，避免混合不同类型的操作\n");
        prompt.append("- **工具映射原则**: 每个步骤应明确使用哪个具体的MCP工具\n");
        prompt.append("- **参数传递原则**: 确保用户的详细要求能够准确传递到工具参数中\n");
        prompt.append("- **依赖关系原则**: 合理安排步骤顺序，确保前置条件得到满足\n");
        prompt.append("- **结果输出原则**: 每个步骤都应有明确的输出结果和成功标准\n\n");

        // 6. 步骤类型指导
        prompt.append("### 步骤类型指导\n");
        prompt.append("根据可用工具和用户需求，常见的步骤类型包括：\n");
        prompt.append("- **数据获取步骤**: 使用搜索、查询等工具获取所需信息\n");
        prompt.append("- **数据处理步骤**: 对获取的信息进行分析、整理和加工\n");
        prompt.append("- **内容生成步骤**: 基于处理后的数据生成目标内容\n");
        prompt.append("- **结果输出步骤**: 将生成的内容发布、保存或传递给用户\n");
        prompt.append("- **通知反馈步骤**: 向用户或相关方发送执行结果通知\n\n");

        // 7. 执行要求
        prompt.append("### 执行要求\n");
        prompt.append("1. **步骤编号**: 使用第1步、第2步、第3步...格式\n");
        prompt.append("2. **Markdown格式**: 严格按照上述Markdown格式输出\n");
        prompt.append("3. **步骤描述**: 每个步骤描述要清晰、具体、可执行\n");
        prompt.append("4. **优先级**: 根据步骤重要性和紧急程度设定\n");
        prompt.append("5. **时长估算**: 基于步骤复杂度合理估算\n");
        prompt.append("6. **工具选择**: 从可用工具中选择最适合的，必须使用完整的函数名称\n");
        prompt.append("7. **依赖关系**: 明确步骤间的先后顺序\n");
        prompt.append("8. **执行细节**: 提供具体可操作的方法，包含详细的参数说明和用户需求传递\n");
        prompt.append("9. **需求传递**: 确保用户的所有详细要求都能准确传递到相应的执行步骤中\n");
        prompt.append("10. **功能独立**: 确保每个步骤功能独立，避免混合不同类型的操作\n");
        prompt.append("11. **工具映射**: 每个步骤必须明确指定使用的MCP工具函数名称\n");
        prompt.append("12. **质量标准**: 设定明确的完成标准\n\n");

        // 7. 步骤类型指导
        prompt.append("### 常见步骤类型指导\n");
        prompt.append("- **信息获取步骤**: 使用搜索工具，关注关键词选择和结果筛选\n");
        prompt.append("- **内容处理步骤**: 基于获取的信息进行分析、整理和创作\n");
        prompt.append("- **结果输出步骤**: 使用相应平台工具发布或保存处理结果\n");
        prompt.append("- **通知反馈步骤**: 使用通信工具进行状态通知或结果反馈\n");
        prompt.append("- **数据处理步骤**: 对获取的信息进行分析、转换和处理\n\n");

        // 8. 质量检查
        prompt.append("### 质量检查清单\n");
        prompt.append("生成计划后请确认：\n");
        prompt.append("- [ ] 每个步骤都有明确的序号和描述\n");
        prompt.append("- [ ] 使用了正确的Markdown格式\n");
        prompt.append("- [ ] 步骤描述清晰具体\n");
        prompt.append("- [ ] 优先级设置合理\n");
        prompt.append("- [ ] 时长估算现实可行\n");
        prompt.append("- [ ] 工具选择恰当\n");
        prompt.append("- [ ] 依赖关系清晰\n");
        prompt.append("- [ ] 执行方法具体可操作\n");
        prompt.append("- [ ] 成功标准明确可衡量\n\n");

        prompt.append("现在请开始生成Markdown格式的执行步骤规划：\n");

        return prompt.toString();
    }

    @Test
    public void test_mcpTools() {
        // 测试工具推荐功能
        String recommendRequest = "我需要检索各个技术论坛最新技术资料，编写技术文章，发送到 CSDN 平台，以及发送消息通知";

        log.info("工具推荐请求: {}", recommendRequest);

        String recommendResult = mcpToolsChatClient.prompt()
                .user(recommendRequest)
                .call()
                .content();

        log.info("工具推荐结果: {}", recommendResult);
    }

    public McpSyncClient sseMcpClient_BaiduSearch() {
        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("http://appbuilder.baidu.com/v2/ai_search/mcp/")
                .sseEndpoint("sse?api_key=bce-v3/ALTAK-3zODLb9qHozIftQlGwez5/2696e92781f5bf1ba1870e2958f239fd6dc822a4")
                .build();

        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(360)).build();
        var init_sse = mcpSyncClient.initialize();
        log.info("Tool SSE MCP Initialized {}", init_sse);

        return mcpSyncClient;
    }

    public McpSyncClient sseMcpClient_csdn() {

        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("http://192.168.1.110:8102").build();

        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(180)).build();

        var init = mcpSyncClient.initialize();
        System.out.println("SSE MCP Initialized: " + init);

        return mcpSyncClient;
    }

    public McpSyncClient sseMcpClient02_weixin() {

        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("http://192.168.1.110:8101").build();

        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(180)).build();

        var init = mcpSyncClient.initialize();
        System.out.println("SSE MCP Initialized: " + init);

        return mcpSyncClient;
    }

    /**
     * 带重试机制的执行方法
     */
    private <T> T executeWithRetry(Supplier<T> operation, String operationName, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("执行操作: {} (第{}/{}次尝试)", operationName, attempt, maxRetries);
                T result = operation.get();
                if (attempt > 1) {
                    log.info("操作 {} 在第{}次尝试后成功", operationName, attempt);
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("操作 {} 第{}次尝试失败: {}", operationName, attempt, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        long waitTime = (long) Math.pow(2, attempt - 1) * 1000;
                        log.info("等待 {}ms 后重试...", waitTime);
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试过程被中断", ie);
                    }
                }
            }
        }

        throw new RuntimeException(String.format("操作 %s 在 %d 次尝试后仍然失败", operationName, maxRetries), lastException);
    }

    private RagAnswerAdvisor buildRagAnswerAdvisor(String filterExpression, int topK) {
        return new RagAnswerAdvisor(
                vectorService,
                AiClientAdvisorVO.RagAnswer.builder()
                        .topK(topK)
                        .filterExpression(filterExpression)
                        .build()
        );
    }

}
