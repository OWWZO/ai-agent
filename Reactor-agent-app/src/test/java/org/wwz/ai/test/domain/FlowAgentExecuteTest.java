package org.wwz.ai.test.domain;

import org.wwz.ai.domain.agent.model.entity.ArmoryCommandEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import org.wwz.ai.domain.agent.service.execute.auto1.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.wwz.ai.domain.agent.service.runtime.AiClientRuntimeRegistry;

import java.util.Arrays;

/**
 * Flow流程执行策略测试类
 * 2025/1/27 17:52
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class FlowAgentExecuteTest {

    @Resource
    private DefaultArmoryStrategyFactory defaultArmoryStrategyFactory;

    @Resource
    private DefaultFlowAgentExecuteStrategyFactory defaultFlowAgentExecuteStrategyFactory;

    @Resource
    private AiClientRuntimeRegistry aiClientRuntimeRegistry;

    @Before
    public void init() throws Exception {
        StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> armoryStrategyHandler =
                defaultArmoryStrategyFactory.armoryStrategyHandler();

        String apply = armoryStrategyHandler.apply(
                ArmoryCommandEntity.builder()
                        .commandType(AiAgentEnumVO.AI_CLIENT.getCode())
                        .commandIdList(Arrays.asList("2101", "2102", "2103"))
                        .build(),
                new DefaultArmoryStrategyFactory.DynamicContext());

        ChatClient chatClient = aiClientRuntimeRegistry.getRequiredChatClient("2101");
        log.info("客户端构建:{}", chatClient);
    }

    @Test
    public void testFlowAgentExecute() throws Exception {
        StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultFlowAgentExecuteStrategyFactory.armoryStrategyHandler();

        ExecuteCommandEntity executeCommandEntity = new ExecuteCommandEntity();
        executeCommandEntity.setAiAgentId("1");
        executeCommandEntity.setMessage("""
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
                """);
        executeCommandEntity.setSessionId("flow-session-id-" + System.currentTimeMillis());
        executeCommandEntity.setMaxStep(4);

        // 创建动态上下文
        DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext = new DefaultFlowAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.setMaxStep(executeCommandEntity.getMaxStep());
        dynamicContext.setExecutionHistory(new StringBuilder());
        dynamicContext.setCurrentTask(executeCommandEntity.getMessage());

        String apply = executeHandler.apply(executeCommandEntity, dynamicContext);
        log.info("Flow执行结果:{}", apply);
    }

}
