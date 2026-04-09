package org.wwz.ai.test.spring.ai;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 结合于拼团项目，使用了 ELK 做的结合使用。
 * 如果没有学习拼团项目，可以独立部署ELK验证；https://bugstack.cn/md/road-map/elk.html
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiAgentStepTest {

    private ChatModel chatModel;

    @Before
    public void init() {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://apis.itedus.cn")
                .apiKey("sk-k6dvxKUVTtjuRquKF6B1E15574794cF9B6006b9cA61bBaD2")
                .completionsPath("v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .build();

        chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4.1-mini")
                        .toolCallbacks(new SyncMcpToolCallbackProvider(stdioMcpClientElasticsearch2()).getToolCallbacks())
                        .build())
                .build();
    }


    /**
     * https://sai.baidu.com/server/Elasticsearch%2520MCP%2520Server/awesimon?id=02d6b7e9091355b91fed045b9c80dede
     * https://github.com/elastic/mcp-server-elasticsearch
     */
    public McpSyncClient stdioMcpClientElasticsearch() {

        Map<String, String> env = new HashMap<>();
        env.put("ES_URL", "http://192.168.1.108:9200");
        env.put("ES_API_KEY", "none");
        env.put("OTEL_SDK_DISABLED", "true");
        env.put("NODE_OPTIONS", "--no-warnings");

        var stdioParams = ServerParameters.builder("npx")
                .args("-y", "@elastic/mcp-server-elasticsearch")
                .env(env)
                .build();

        var mcpClient = McpClient.sync(new StdioClientTransport(stdioParams,
                        new io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper(new com.fasterxml.jackson.databind.ObjectMapper())))
                .requestTimeout(Duration.ofSeconds(100)).build();

        var init = mcpClient.initialize();

        System.out.println("Stdio MCP Initialized: " + init);

        return mcpClient;

    }

    public McpSyncClient stdioMcpClientElasticsearch2() {
        Map<String, String> env = new HashMap<>();
        env.put("ES_HOST", "http://192.168.1.110:9200");
        env.put("ES_API_KEY", "none");
        // 禁用OpenTelemetry以避免日志干扰JSON-RPC通信
//        env.put("OTEL_SDK_DISABLED", "true");
//        env.put("NODE_OPTIONS", "--no-warnings");

        var stdioParams = ServerParameters.builder("npx")
                .args("-y", "@awesome-ai/elasticsearch-mcp")
                .env(env)
                .build();

        var mcpClient = McpClient.sync(new StdioClientTransport(stdioParams,
                        new io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper(new com.fasterxml.jackson.databind.ObjectMapper())))
                .requestTimeout(Duration.ofSeconds(100)).build();

        var init = mcpClient.initialize();

        System.out.println("Stdio MCP Initialized: " + init);

        return mcpClient;

    }

    /**
     * 查询被限流的用户
     *
     */
    @Test
    public void queryRateLimitedUsers() {
        // 第一步：系统初始化提示词
        String systemPrompt = buildSystemPrompt();

        // 第二步：用户查询提示词
        String userQuery = "ES查询哪个用户被限流了";

        // 第三步：构建完整的提示词
        String fullPrompt = buildFullPrompt(systemPrompt, userQuery);

        // 第四步：调用AI模型
        Prompt prompt = Prompt.builder()
                .messages(new UserMessage(fullPrompt))
                .build();

        ChatResponse chatResponse = chatModel.call(prompt);

        log.info("测试结果:{}", chatResponse.getResult().getOutput().getText());
    }

    /**
     * 构建系统提示词 - 定义AI的能力和执行步骤
     */
    private String buildSystemPrompt() {
        return """
                你是一个专业的日志分析助手，具备以下能力：
                1. 可以查询Elasticsearch索引列表 - 使用list_indices()函数
                2. 可以获取索引字段映射 - 使用get_mappings(index)函数
                3. 可以执行Elasticsearch搜索 - 使用search(index, queryBody)函数
                
                当用户询问限流相关问题时，请按以下步骤执行：
                
                **步骤1：探索数据源**
                - 首先调用list_indices()查看所有可用的索引
                - 识别可能包含日志信息的索引（通常包含log、logstash等关键词）
                
                **步骤2：分析数据结构**
                - 对目标索引调用get_mappings()查看字段结构
                - 重点关注message、level、timestamp等字段
                
                **步骤3：构建搜索查询**
                - 使用多种限流相关关键词搜索：限流、rate limit、throttle、blocked、超过限制、黑名单、超频次
                - 按时间倒序排列结果
                - 示例查询结构：
                 {
                  `index`: `[从list_indices()获取的实际索引名]`,
                  `queryBody`: {
                    `size`: 10,
                    `sort`: [
                      {
                        `@timestamp`: {
                          `order`: `desc`
                        }
                      }
                    ],
                    `query`: {
                      `match`: {
                        `message`: `xfg01`
                      }
                    }
                  }
                }
                
                **步骤4：优化搜索策略**
                - 如果初始搜索结果不理想，尝试使用wildcard查询
                - 如果需要，使用单一关键词进行精确匹配
                
                **步骤5：分析结果**
                - 从搜索结果中提取用户信息
                - 识别限流类型（黑名单、超频次等）
                - 统计触发次数和时间分布
                - 分析影响的服务和功能
                
                **输出格式要求：**
                - 明确列出被限流的用户ID
                - 说明限流类型和原因
                - 提供触发时间和频率信息
                - 给出分析建议
                
                现在开始执行查询任务。
                """;
    }

    /**
     * 构建完整的提示词
     */
    private String buildFullPrompt(String systemPrompt, String userQuery) {
        return String.format("""
                %s
                
                用户提问：%s
                
                请按照上述步骤逐一执行，并提供详细的分析报告。
                """, systemPrompt, userQuery);
    }

    /**
     * 分步骤查询的详细实现版本
     */
    public String queryRateLimitedUsersStepByStep() {
        StringBuilder result = new StringBuilder();

        // 步骤1：查询索引列表
        String step1Prompt = buildStepPrompt("步骤1：查询所有可用的Elasticsearch索引",
                "请调用list_indices()函数查看所有可用的索引，并识别可能包含日志的索引。");
        result.append(executeStep(step1Prompt)).append("\n\n");

        // 步骤2：获取索引映射
        String step2Prompt = buildStepPrompt("步骤2：获取日志索引的字段映射",
                "请对识别出的日志索引调用get_mappings()函数，查看字段结构，重点关注message、level、timestamp等字段。");
        result.append(executeStep(step2Prompt)).append("\n\n");

        // 步骤3：搜索限流日志
        String step3Prompt = buildStepPrompt("步骤3：搜索限流相关日志",
                "请使用多种限流相关关键词（限流、rate limit、throttle、blocked、超过限制等）搜索日志，按时间倒序排列。");
        result.append(executeStep(step3Prompt)).append("\n\n");

        // 步骤4：分析结果
        String step4Prompt = buildStepPrompt("步骤4：分析限流用户",
                "请分析搜索结果，提取被限流的用户信息，包括用户ID、限流类型、触发次数等，并生成详细报告。");
        result.append(executeStep(step4Prompt)).append("\n\n");

        return result.toString();
    }

    /**
     * 构建步骤提示词
     */
    private String buildStepPrompt(String stepTitle, String instruction) {
        return String.format("""
                %s
                
                %s
                
                请执行此步骤并返回结果。
                """, stepTitle, instruction);
    }

    /**
     * 执行单个步骤
     */
    private String executeStep(String stepPrompt) {
        Prompt prompt = Prompt.builder()
                .messages(new UserMessage(stepPrompt))
                .build();

        ChatResponse chatResponse = chatModel.call(prompt);
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * 针对特定场景的高级查询
     */
    public String queryRateLimitedUsersAdvanced(String timeRange, String logLevel) {
        String advancedPrompt = String.format("""
                请查询被限流的用户，查询条件如下：
                - 时间范围：%s
                - 日志级别：%s
                
                执行步骤：
                1. 首先调用list_indices()查看可用索引
                2. 选择合适的日志索引，调用get_mappings()查看字段结构
                3. 构建包含时间和级别过滤的搜索查询：
                {
                  "size": 50,
                  "sort": [{"@timestamp": {"order": "desc"}}],
                  "query": {
                    "bool": {
                      "must": [
                        {
                          "bool": {
                            "should": [
                              {"match": {"message": "限流"}},
                              {"match": {"message": "rate limit"}},
                              {"match": {"message": "超频次"}},
                              {"match": {"message": "黑名单"}}
                            ]
                          }
                        }
                      ],
                      "filter": [
                        {"range": {"@timestamp": {"gte": "%s"}}},
                        {"term": {"level.keyword": "%s"}}
                      ]
                    }
                  }
                }
                4. 分析结果，提供用户统计和建议
                
                请按步骤执行并提供详细分析报告。
                """, timeRange, logLevel, timeRange, logLevel);

        return executeStep(advancedPrompt);
    }

    /**
     * 测试优化后的MCP工具调用格式
     * 验证queryBody参数能够正确构建，避免undefined错误
     */
    @Test
    public void testOptimizedMcpToolCall() {
        String optimizedPrompt = """
                **🚨 CRITICAL: MCP工具调用要求**
                如果需要调用search工具进行ES查询，必须严格遵守以下格式：
                1. **index参数**: 必须是从list_indices()获得的真实索引名（字符串类型）
                2. **queryBody参数**: 必须是完整的JSON对象，绝对不能为undefined或null
                
                **queryBody标准格式（必须严格按照此格式）:**
                ```json
                {
                  "size": 10,
                  "sort": [
                    {
                      "@timestamp": {
                        "order": "desc"
                      }
                    }
                  ],
                  "query": {
                    "match": {
                      "message": "搜索关键词"
                    }
                  }
                }
                ```
                
                **工具调用检查清单（每次调用前必须确认）:**
                - [ ] index参数已设置为真实索引名
                - [ ] queryBody是完整的JSON对象（不是undefined）
                - [ ] queryBody包含query字段
                - [ ] queryBody包含size字段
                - [ ] JSON格式正确无语法错误
                
                **任务:** 请查询最近的错误日志，搜索关键词"error"，返回最新的10条记录。
                
                **执行步骤:**
                1. 首先调用list_indices()获取可用的索引列表
                2. 选择合适的日志索引
                3. 使用search工具查询，确保queryBody参数完整
                4. 返回查询结果
                """;

        executeStep(optimizedPrompt);
    }
}
