package org.wwz.ai.domain.agent.service.execute.auto1.step;

import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;

import org.wwz.ai.domain.agent.service.execute.auto1.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * 抽象类
 */
public abstract class AbstractExecuteSupport extends AbstractMultiThreadStrategyRouter<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> {

    private final Logger log = LoggerFactory.getLogger(AbstractExecuteSupport.class);

    @Resource
    protected ApplicationContext applicationContext;

    @Resource
    protected IAgentRepository repository;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Override
    protected void multiThread(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {

    }

    protected ChatClient getChatClientByClientId(String clientId) {
        return getBean(AiAgentEnumVO.AI_CLIENT.getBeanName(clientId));
    }

    protected <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }

    /**
     * 统一注入 MetricsCollector 到本次请求的 advisor context。
     * 业务侧只需要传入自身需要的 advisor 配置（如 chat memory），指标采集参数在这里统一追加。
     */
    protected Consumer<ChatClient.AdvisorSpec> withMetrics(DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                          Consumer<ChatClient.AdvisorSpec> advisorConfig) {
        return a -> {
            if (advisorConfig != null) {
                advisorConfig.accept(a);
            }
        };
    }


    /**
     * 流式调用 LLM：通过 SSE 将模型输出逐块推送到前端，并自动采集 token 用量
     *
     * <p>通过 MetricsAdvisor 自动统计 token，无需手动调用 accumulate
     * 流式调用时，MetricsAdvisor 会自动在最后一个包含 usage 的 chunk 上统计
     *
     * @param sessionId  会话 ID，用于 SSE 事件
     * @param step       当前步骤
     * @param stepName   步骤名称（如 MCP工具分析）
     * @param subType    子类型（如 analysis_tools）
     * @return 模型返回的完整文本内容
     */
    protected String streamLlmWithMetrics(ChatClient chatClient, String userMessage,
                                          DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                          String sessionId, int step, String stepName, String subType) {
        final String sessionIdFinal = sessionId != null ? sessionId : "";

        //建立前端消息占位
        sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamStart(step, stepName, subType, sessionIdFinal));

        StringBuilder fullText = new StringBuilder();

        try {
            var promptBuilder = chatClient.prompt()
                    .user(userMessage)
                    .advisors(withMetrics(dynamicContext, null));

            Flux<ChatResponse> flux = promptBuilder.stream().chatResponse();
            flux.doOnNext(cr -> {
                if (cr != null && cr.getResult() != null && cr.getResult().getOutput() != null) {
                    String text = cr.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        fullText.append(text);
                        sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamDelta(step, stepName, subType, text, sessionIdFinal));
                    }
                }
                // MetricsAdvisor 会自动统计 token，这里不再手动 accumulate
            }).doOnError(e -> log.warn("LLM stream error: {}", e.getMessage())).blockLast();
        } catch (Exception e) {
            log.error("流式调用 LLM 异常: {}", e.getMessage(), e);
        }

        //结束当前流式 形成md格式展示
        sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamEnd(step, stepName, subType, sessionIdFinal));
        return fullText.toString();
    }

    /**
     * 流式调用 LLM（支持传入 advisorConfig）：用于注入 ChatMemory/RAG 等顾问能力，同时保持 metrics 采集一致
     */
    protected String streamLlmWithMetrics(ChatClient chatClient, String userMessage,
                                          DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                          String sessionId, int step, String stepName, String subType,
                                          Consumer<ChatClient.AdvisorSpec> advisorConfig) {
        final String sessionIdFinal = sessionId != null ? sessionId : "";

        // 建立前端消息占位，保持与无 advisorConfig 重载一致的 SSE 协议
        sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamStart(step, stepName, subType, sessionIdFinal));

        StringBuilder fullText = new StringBuilder();
        try {
            var promptBuilder = chatClient.prompt()
                    .user(userMessage)
                    .advisors(withMetrics(dynamicContext, advisorConfig));

            Flux<ChatResponse> flux = promptBuilder.stream().chatResponse();
            flux.doOnNext(cr -> {
                if (cr != null && cr.getResult() != null && cr.getResult().getOutput() != null) {
                    String text = cr.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        fullText.append(text);
                        sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamDelta(step, stepName, subType, text, sessionIdFinal));
                    }
                }
            }).doOnError(e -> log.warn("LLM stream error: {}", e.getMessage())).blockLast();
        } catch (Exception e) {
            log.error("流式调用 LLM 异常: {}", e.getMessage(), e);
        }

        sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamEnd(step, stepName, subType, sessionIdFinal));
        return fullText.toString();
    }

    /**
     * 非流式调用 LLM（支持 Tool Calling）：使用 call() 触发工具调用链路，再将最终文本按 chunk 形式推送到 SSE
     */
    protected String callLlmWithMetrics(ChatClient chatClient, String userMessage,
                                        DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String sessionId, int step, String stepName, String subType,
                                        Consumer<ChatClient.AdvisorSpec> advisorConfig) {
        final String sessionIdFinal = sessionId != null ? sessionId : "";


        sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamStart(step, stepName, subType, sessionIdFinal));

        String content = "";
        try {
            ChatResponse response = chatClient.prompt()
                    .user(userMessage)
                    .advisors(withMetrics(dynamicContext, advisorConfig))
                    .call()
                    .chatResponse();
            if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                content = response.getResult().getOutput().getText();
            }
        } catch (Exception e) {
            log.error("非流式调用 LLM 异常: {}", e.getMessage(), e);
        }

        if (content == null) {
            content = "";
        }

        // 将完整内容按固定 chunk 推送，保持前端“流式体验”
        int chunkSize = 64;
        for (int i = 0; i < content.length(); i += chunkSize) {
            String chunk = content.substring(i, Math.min(content.length(), i + chunkSize));
            if (!chunk.isEmpty()) {
                sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamDelta(step, stepName, subType, chunk, sessionIdFinal));
            }
        }

        sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamEnd(step, stepName, subType, sessionIdFinal));
        return content;
    }

    protected String callLlmWithMetrics(ChatClient chatClient, String userMessage,
                                        DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String sessionId, int step, String stepName, String subType) {
        return callLlmWithMetrics(chatClient, userMessage, dynamicContext, sessionId, step, stepName, subType, null);
    }

    /**
     * 通用的SSE结果发送方法
     * @param dynamicContext 动态上下文
     * @param result 要发送的结果实体
     */
    protected void sendSseResult(DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                Object result) {
        try {
            // 优先使用强类型 emitter 字段，兼容老逻辑从 Map 中获取
            SseEmitter emitter = dynamicContext.getEmitter() != null ? dynamicContext.getEmitter() : dynamicContext.getValue("emitter");
            if (emitter != null) {
                // 发送SSE格式的数据
                String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
                emitter.send(sseData);
            }
        } catch (IOException e) {
            log.error("发送SSE结果失败：{}", e.getMessage(), e);
        }
    }

}
