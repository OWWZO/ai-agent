package org.wwz.ai.domain.agent.service.armory.node.factory.element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.wwz.ai.domain.agent.service.execute.flow.metrics.LlmMetricsCollector;
import reactor.core.publisher.Flux;

/**
 * LLM 指标采集 Advisor
 * 
 * <p>功能：自动从 ChatResponse.metadata.usage 中提取 token 用量，累加到 LlmMetricsCollector
 * 
 * <p>使用方式：
 * <pre>{@code
 * chatClient.prompt()
 *     .user(message)
 *     .advisors(a -> a.param("llmMetricsCollector", collector))
 *     .call()
 * }</pre>
 * 
 * <p>注意：
 * <ul>
 *   <li>流式调用时，大多数模型只在最后一个 chunk 包含 usage，本 Advisor 会自动处理</li>
 *   <li>如果 context 中没有 llmMetricsCollector，则跳过统计（不影响业务逻辑）</li>
 * </ul>
 * 
 * @author WWZ
 */
public class MetricsAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(MetricsAdvisor.class);
    
    /** Context 中存储 LlmMetricsCollector 的键名 */
    public static final String CONTEXT_KEY_LLM_METRICS_COLLECTOR = "llmMetricsCollector";

    /**
     * 前置处理：透传请求，不做任何修改
     * MetricsAdvisor 只需要在响应后统计 token，不需要修改请求
     */
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 透传请求，不做任何修改
        return chatClientRequest;
    }

    /**
     * 后置处理：从响应中提取 token 用量并累加到 collector
     */
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        accumulateMetrics(chatClientResponse.chatResponse(), chatClientResponse.context());
        return chatClientResponse;
    }

    /**
     * 同步调用：执行链处理
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        accumulateMetrics(response.chatResponse(), response.context());
        return response;
    }

    /**
     * 流式调用：处理流式响应，只在最后一个包含 usage 的 chunk 上统计
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        Flux<ChatClientResponse> flux = streamAdvisorChain.nextStream(chatClientRequest);
        
        // 用于保存最后一个包含 usage 的响应
        final ChatResponse[] lastResponseWithUsage = {null};
        
        return flux
            .doOnNext(response -> {
                ChatResponse chatResponse = response.chatResponse();
                // 检查是否有 usage（大多数模型只在最后一个 chunk 有）
                if (chatResponse != null && chatResponse.getMetadata() != null 
                        && chatResponse.getMetadata().getUsage() != null) {
                    lastResponseWithUsage[0] = chatResponse;
                }
            })
            .doOnComplete(() -> {
                // 流式调用完成时，统计最后一个包含 usage 的响应
                if (lastResponseWithUsage[0] != null) {
                    accumulateMetrics(lastResponseWithUsage[0], chatClientRequest.context());
                }
            })
            .doOnError(error -> {
                log.warn("流式调用过程中发生错误，已统计的 token 用量可能不完整: {}", error.getMessage());
            });
    }

    /**
     * 从 ChatResponse 提取 token 用量并累加到 collector
     * 
     * @param chatResponse ChatResponse 对象
     * @param context 请求上下文（包含 llmMetricsCollector）
     */
    private void accumulateMetrics(ChatResponse chatResponse, java.util.Map<String, Object> context) {
        if (chatResponse == null || context == null) {
            return;
        }

        Object collectorObj = context.get(CONTEXT_KEY_LLM_METRICS_COLLECTOR);
        if (!(collectorObj instanceof LlmMetricsCollector)) {
            // 如果没有 collector，跳过统计（不影响业务逻辑）
            return;
        }

        LlmMetricsCollector collector = (LlmMetricsCollector) collectorObj;
        collector.accumulate(chatResponse);
    }

    /**
     * 优先级：设置为较低优先级（如 100），确保在其他 Advisor（如 RAG）之后执行
     * 这样可以在所有 Advisor 处理完成后统计最终的 token 用量
     */
    @Override
    public int getOrder() {
        return 100;
    }

    /**
     * Advisor 名称
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
