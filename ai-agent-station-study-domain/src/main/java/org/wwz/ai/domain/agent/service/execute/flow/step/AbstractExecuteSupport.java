package org.wwz.ai.domain.agent.service.execute.flow.step;

import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import org.wwz.ai.domain.agent.service.execute.flow.metrics.LlmMetricsCollector;
import org.wwz.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * 抽象类
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/24 14:28
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
     * 调用 LLM 并采集 token 用量
     * 仅调用 chatResponse() 一次，从中提取 content，避免 chatResponse()+content() 重复触发导致 Advisor 链消耗
     * @return 模型返回的文本内容
     */
    protected String callLlmWithMetrics(ChatClient chatClient, String userMessage,
                                       DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        ChatResponse response = chatClient.prompt().user(userMessage).call().chatResponse();
        if (response != null) {
            LlmMetricsCollector collector = dynamicContext.getLlmMetricsCollector();
            if (collector != null) {
                collector.accumulate(response);
            }
            var result = response.getResult();
            if (result != null && result.getOutput() != null) {
                String text = result.getOutput().getText();
                return text != null ? text : "";
            }
        }
        return "";
    }

    /**
     * 流式调用 LLM：通过 SSE 将模型输出逐块推送到前端，并采集 token 用量
     * 类似 chatClient.stream(new Prompt(message)) 的效果，每块通过 sendSseStreamDelta 发送
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

        ResponseBodyEmitter emitter = dynamicContext.getValue("emitter");
        if (emitter == null) {
            return callLlmWithMetrics(chatClient, userMessage, dynamicContext);
        }

        sendSseResult(dynamicContext, AutoAgentExecuteResultEntity.createStreamStart(step, stepName, subType, sessionIdFinal));

        StringBuilder fullText = new StringBuilder();
        LlmMetricsCollector collector = dynamicContext.getLlmMetricsCollector();

        try {
            Flux<ChatResponse> flux = chatClient.prompt().user(userMessage).stream().chatResponse();
            flux.doOnNext(cr -> {
                if (cr != null && cr.getResult() != null && cr.getResult().getOutput() != null) {
                    String text = cr.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        fullText.append(text);
                        sendSseResult(dynamicContext, AutoAgentExecuteResultEntity.createStreamDelta(step, stepName, subType, text, sessionIdFinal));
                    }
                }
                if (collector != null && cr != null) {
                    collector.accumulate(cr);
                }
            }).doOnError(e -> log.warn("LLM stream error: {}", e.getMessage())).blockLast();
        } catch (Exception e) {
            log.error("流式调用 LLM 异常: {}", e.getMessage(), e);
        }

        sendSseResult(dynamicContext, AutoAgentExecuteResultEntity.createStreamEnd(step, stepName, subType, sessionIdFinal));
        return fullText.toString();
    }

    /**
     * 通用的SSE结果发送方法
     * @param dynamicContext 动态上下文
     * @param result 要发送的结果实体
     */
    protected void sendSseResult(DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, 
                                AutoAgentExecuteResultEntity result) {
        try {
            ResponseBodyEmitter emitter = dynamicContext.getValue("emitter");
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
