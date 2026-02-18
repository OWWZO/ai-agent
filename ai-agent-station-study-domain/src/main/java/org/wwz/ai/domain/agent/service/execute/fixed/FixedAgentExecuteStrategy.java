package org.wwz.ai.domain.agent.service.execute.fixed;

import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import org.wwz.ai.domain.agent.service.IExecuteStrategy;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;


import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

/**
 * 固定执行策略
 */
@Slf4j
@Service("fixedAgentExecuteStrategy")
public class FixedAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private IAgentRepository repository;

    @Resource
    protected ApplicationContext applicationContext;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Override
    public void execute(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception {
        // 1. 获取配置客户端
        List<AiAgentClientFlowConfigVO> aiAgentClientList = repository.queryAiAgentClientsByAgentId(requestParameter.getAiAgentId());

        // 2. 循环执行客户端（流式输出）
        String content = "";
        final String sessionId = requestParameter.getSessionId() != null ? requestParameter.getSessionId() : "";
        


        for (AiAgentClientFlowConfigVO config : aiAgentClientList) {
            ChatClient chatClient = getChatClientByClientId(config.getClientId());

            // 流式调用 LLM
            String stepName = "智能对话";
            String subType = "summary";
            sendStreamStart(emitter, 1, stepName, subType, sessionId);

            StringBuilder fullText = new StringBuilder();
            try {
                Flux<ChatResponse> flux = chatClient.prompt(requestParameter.getMessage() + "，" + content)
                        .system(s -> s.param("current_date", LocalDate.now().toString()))
                        .advisors(a -> a
                                .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100)
                                ) // 通过 MetricsAdvisor 自动统计
                        .stream().chatResponse();

                flux.doOnNext(cr -> {
                    if (cr != null && cr.getResult() != null && cr.getResult().getOutput() != null) {
                        String text = cr.getResult().getOutput().getText();
                        if (text != null && !text.isEmpty()) {
                            fullText.append(text);
                            sendStreamDelta(emitter, 1, stepName, subType, text, sessionId);
                        }
                    }
                }).doOnError(e -> log.warn("LLM stream error: {}", e.getMessage())).blockLast();
            } catch (Exception e) {
                log.error("流式调用 LLM 异常: {}", e.getMessage(), e);
            }

            sendStreamEnd(emitter, 1, stepName, subType, sessionId);
            content = fullText.toString();

            log.info("智能体对话进行，客户端ID {}", requestParameter.getAiAgentId());
        }

        log.info("智能体对话请求，结果 {} {}", requestParameter.getAiAgentId(), content);


    }

    private ChatClient getChatClientByClientId(String clientId) {
        return getBean(AiAgentEnumVO.AI_CLIENT.getBeanName(clientId));
    }

    private <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }
    
    /**
     * 发送流式开始事件
     */
    private void sendStreamStart(ResponseBodyEmitter emitter, int step, String stepName, String subType, String sessionId) {
        try {
            AgentExecuteResultEntity result = AgentExecuteResultEntity.createStreamStart(step, stepName, subType, sessionId);
            String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            log.error("发送流式开始事件失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 发送流式增量事件
     */
    private void sendStreamDelta(ResponseBodyEmitter emitter, int step, String stepName, String subType, String content, String sessionId) {
        try {
            AgentExecuteResultEntity result = AgentExecuteResultEntity.createStreamDelta(step, stepName, subType, content, sessionId);
            String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            log.error("发送流式增量事件失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 发送流式结束事件
     */
    private void sendStreamEnd(ResponseBodyEmitter emitter, int step, String stepName, String subType, String sessionId) {
        try {
            AgentExecuteResultEntity result = AgentExecuteResultEntity.createStreamEnd(step, stepName, subType, sessionId);
            String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            log.error("发送流式结束事件失败：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送完成标识到流式输出（携带 token/成本 等指标）
     */


}
