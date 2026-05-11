package org.wwz.ai.test.spring.ai;

import com.alibaba.fastjson.JSON;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.wwz.ai.domain.agent.model.valobj.AiClientAdvisorVO;
import org.wwz.ai.domain.agent.reactor.service.VectorService;
import org.wwz.ai.domain.agent.service.armory.node.factory.element.RagAnswerAdvisor;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiAgentTest {

    private ChatModel chatModel;

    private ChatClient chatClient;

    @Resource
    private VectorService vectorService;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Before
    public void init() {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .apiKey("sk-95e5d430617f428181f089aef88036cc")
                .embeddingsPath("v1/embeddings")
                .build();

        chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-v3.2")
                        .toolCallbacks(new SyncMcpToolCallbackProvider(stdioMcpClient(),
//                                sseMcpClient01(),
                                sseMcpClient02()).getToolCallbacks())
                        .build())
                .build();

        chatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                        	 你是一个 AI Agent 智能体，可以根据用户输入信息生成文章，并发送到 CSDN 平台以及完成微信公众号消息通知，今天是 {current_date}。
                        
                        	 你擅长使用Planning模式，帮助用户生成质量更高的文章。
                        
                        	 你的规划应该包括以下几个方面：
                        	 1. 分析用户输入的内容，生成技术文章。
                        	 2. 提取，文章标题（需要含带技术点）、文章内容、文章标签（多个用英文逗号隔开）、文章简述（100字）将以上内容发布文章到CSDN
                        	 3. 获取发送到 CSDN 文章的 URL 地址。
                        	 4. 微信公众号消息通知，平台：CSDN、主题：为文章标题、描述：为文章简述、跳转地址：从发布文章到CSDN获取 URL 地址
                        """)
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(stdioMcpClient(),
//                        sseMcpClient01(),
                        sseMcpClient02()).getToolCallbacks())
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder()
                                        .maxMessages(100)
                                        .build()
                        ).build(),
                        buildRagAnswerAdvisor("knowledge == 'article-prompt-words'", 5),
                        SimpleLoggerAdvisor.builder().build())
                .build();
    }

    @Test
    public void test_chat_model_stream_01() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Prompt prompt = Prompt.builder()
                .messages(new UserMessage(
                        """
                                有哪些工具可以使用
                                """))
                .build();

        // 非流式，chatModel.call(prompt)
        Flux<ChatResponse> stream = chatModel.stream(prompt);

        stream.subscribe(
                chatResponse -> {
                    AssistantMessage output = chatResponse.getResult().getOutput();
                    log.info("测试结果: {}", JSON.toJSONString(output));
                },
                Throwable::printStackTrace,
                () -> {
                    countDownLatch.countDown();
                    System.out.println("Stream completed");
                }
        );

        countDownLatch.await();
    }

    @Test
    public void test_chat_model_call() {
        Prompt prompt = Prompt.builder()
                .messages(new UserMessage(
                        """
                                有哪些工具可以使用
                                """))
                .build();

        ChatResponse chatResponse = chatModel.call(prompt);

        log.info("测试结果(call):{}", JSON.toJSONString(chatResponse));
    }

    @Test
    public void test_02() {
        String userInput = "王大瓜今年几岁";
        System.out.println("\n>>> QUESTION: " + userInput);
        System.out.println("\n>>> ASSISTANT: " + chatClient
                .prompt(userInput)
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .call().content());
    }

    @Test
    public void test_client03() {
        ChatClient chatClient01 = ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是一个专业的AI提示词优化专家。请帮我优化以下prompt，并按照以下格式返回：
                        
                        # Role: [角色名称]
                        
                        ## Profile
                        - language: [语言]
                        - description: [详细的角色描述]
                        - background: [角色背景]
                        - personality: [性格特征]
                        - expertise: [专业领域]
                        - target_audience: [目标用户群]
                        
                        ## Skills
                        
                        1. [核心技能类别]
                           - [具体技能]: [简要说明]
                           - [具体技能]: [简要说明]
                           - [具体技能]: [简要说明]
                           - [具体技能]: [简要说明]
                        
                        2. [辅助技能类别]
                           - [具体技能]: [简要说明]
                           - [具体技能]: [简要说明]
                           - [具体技能]: [简要说明]
                           - [具体技能]: [简要说明]
                        
                        ## Rules
                        
                        1. [基本原则]：
                           - [具体规则]: [详细说明]
                           - [具体规则]: [详细说明]
                           - [具体规则]: [详细说明]
                           - [具体规则]: [详细说明]
                        
                        2. [行为准则]：
                           - [具体规则]: [详细说明]
                           - [具体规则]: [详细说明]
                           - [具体规则]: [详细说明]
                           - [具体规则]: [详细说明]
                        
                        3. [限制条件]：
                           - [具体限制]: [详细说明]
                           - [具体限制]: [详细说明]
                           - [具体限制]: [详细说明]
                           - [具体限制]: [详细说明]
                        
                        ## Workflows
                        
                        - 目标: [明确目标]
                        - 步骤 1: [详细说明]
                        - 步骤 2: [详细说明]
                        - 步骤 3: [详细说明]
                        - 预期结果: [说明]
                        
                        
                        ## Initialization
                        作为[角色名称]，你必须遵守上述Rules，按照Workflows执行任务。
                        
                        请基于以上模板，优化并扩展以下prompt，确保内容专业、完整且结构清晰，注意不要携带任何引导词或解释，不要使用代码块包围。
                        """)
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder()
                                        .maxMessages(100)
                                        .build()
                        ).build()
                        , buildRagAnswerAdvisor("knowledge == 'article-prompt-words'", 5)
                )
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-v3.2")
                        .build())
                .build();

        String content = chatClient01
                .prompt("生成一篇文章 介绍一下Java 的内存模型（JMM） ")

                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, "chatId-101")
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
                .call().content();

        System.out.println("\n>>> ASSISTANT: " + content);

        ChatClient chatClient02 = ChatClient.builder(chatModel)
                .defaultSystem("""
                        	 你是一个 AI Agent 智能体，可以根据用户输入信息生成文章，并发送到 CSDN 平台以及完成微信公众号消息通知，今天是 {current_date}。
                        
                        	 你擅长使用Planning模式，帮助用户生成质量更高的文章。
                        
                        	 你的规划应该包括以下几个方面：
                        	 1. 分析用户输入的内容，生成技术文章。
                        	 2. 提取，文章标题（需要含带技术点）、文章内容、文章标签（多个用英文逗号隔开）、文章简述（100字）将以上内容发布文章到CSDN
                        	 3. 获取发送到 CSDN 文章的 URL 地址。
                        	 4. 微信公众号消息通知，平台：CSDN、主题：为文章标题、描述：为文章简述、跳转地址：为发布文章到CSDN获取 URL地址 CSDN文章链接 https 开头的地址。
                        """)
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(
//                        sseMcpClient01(),
                        sseMcpClient02()))
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder()
                                        .maxMessages(100)
                                        .build()
                        ).build(),
                        new SimpleLoggerAdvisor()
                )
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-v3.2")
                        .build())
                .build();

        String userInput = "生成一篇文章，要求如下 \r\n" + content;
        System.out.println("\n>>> QUESTION: " + userInput);
        System.out.println("\n>>> ASSISTANT: " + chatClient02
                .prompt(userInput)
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, "chatId-101")
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
                .call().content());
    }

    public McpSyncClient stdioMcpClient() {

        var stdioParams = ServerParameters.builder("npx.cmd")
                .args("-y", "@modelcontextprotocol/server-filesystem", "C:\\Users\\WWZ\\Desktop", "C:\\Users\\WWZ\\Desktop")
                .build();

        var mcpClient = McpClient.sync(new StdioClientTransport(stdioParams,
                        new io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper(new com.fasterxml.jackson.databind.ObjectMapper())))
                .requestTimeout(Duration.ofSeconds(10)).build();

        var init = mcpClient.initialize();

        System.out.println("Stdio MCP Initialized: " + init);

        return mcpClient;

    }

//微信公众号通知
//    public McpSyncClient sseMcpClient01() {
//
//        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("http://192.168.1.108:8102").build();
//
//        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(180)).build();
//
//        var init = mcpSyncClient.initialize();
//        System.out.println("SSE MCP Initialized: " + init);
//
//        return mcpSyncClient;
//    }

    public McpSyncClient sseMcpClient02() {

        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("http://127.0.0.1:8101").build();

        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(180)).build();

        var init = mcpSyncClient.initialize();
        System.out.println("SSE MCP Initialized: " + init);

        return mcpSyncClient;
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
