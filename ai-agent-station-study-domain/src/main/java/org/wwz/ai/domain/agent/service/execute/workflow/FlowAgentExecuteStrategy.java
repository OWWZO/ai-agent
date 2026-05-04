package org.wwz.ai.domain.agent.service.execute.workflow;

import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.printer.Printer;
import org.wwz.ai.domain.agent.reactor.agent.printer.SSEPrinter;
import org.wwz.ai.domain.agent.reactor.agent.util.DateUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.service.IExecuteStrategy;
import org.wwz.ai.domain.agent.service.runtime.AiClientRuntimeRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 固定执行策略
 */
@Slf4j
public class FlowAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private IAgentRepository repository;

    @Resource
    private ReactorConfig reactorConfig;

    @Resource
    private AiClientRuntimeRegistry aiClientRuntimeRegistry;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    /**
     * 新入口：和 React 一样使用 AgentRequest + SSEPrinter（AgentResponse）输出
     */
    @Override
    public void execute(AgentRequest request, SseEmitter emitter) throws Exception {
        log.info("{} fixed agent request: {}", request.getRequestId(), request);
        // 构建 AgentContext
        Printer printer = new SSEPrinter(emitter, request, request.getAgentType());
        AgentContext agentContext = AgentContext.builder()
                .requestId(request.getRequestId())
                // 会话级 ID：统一使用 AgentRequest.sessionId
                .sessionId(request.getSessionId())
                .printer(printer)
                .query(request.getQuery())
                .task("")
                .dateInfo(DateUtil.CurrentDateInfo())
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .sopPrompt(request.getSopPrompt())
                .basePrompt(request.getBasePrompt())
                .agentType(request.getAgentType())
                .isStream(Objects.nonNull(request.getIsStream()) ? request.getIsStream() : false)
                .templateType("dataAgent".equals(request.getOutputStyle()) ? "fix" : "empty")
                .build();

        // 1. 获取配置客户端（固定 AgentId，可按需调整）
        if (request.getAiAgentId() == null || request.getAiAgentId().isBlank()) {
            throw new IllegalStateException("chat 角色未解析，无法执行 Fix 策略");
        }

        List<AiAgentClientFlowConfigVO> aiAgentClientList =
                repository.queryAiAgentClientsByAgentId(request.getAiAgentId());
        if (aiAgentClientList == null || aiAgentClientList.isEmpty()) {
            throw new IllegalStateException("当前角色未配置可执行的 Fix 流程");
        }

        String content = "";
        final String sessionId = request.getSessionId();

        // 2. 循环执行客户端（流式输出），保持原有 for (config : aiAgentClientList) 逻辑不变
        for (AiAgentClientFlowConfigVO config : aiAgentClientList) {
            ChatClient chatClient = getChatClientByClientId(config.getClientId());

            StringBuilder fullText = new StringBuilder();
            try {
                // 用户输入 + 历史内容作为 user 内容，提示词作为 system 提示，避免系统提示词被模型原样复述给用户
                Flux<org.springframework.ai.chat.model.ChatResponse> flux = chatClient
                        .prompt(request.getQuery() + "，" + content)
                        .system(config.getStepPrompt() + " current_date_time:" + LocalDateTime.now())
                        .advisors(a -> a
                                .param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId)
                                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100)
                        )
                        .stream().chatResponse();

                flux.doOnNext(cr -> {
                    if (cr != null && cr.getResult() != null && cr.getResult().getOutput() != null) {
                        String text = cr.getResult().getOutput().getText();
                        if (text != null && !text.isEmpty()) {
                            fullText.append(text);
                            // 只输出对话内容，使用 agent_stream 流式返回
                            agentContext.getPrinter().send("agent_stream", text);
                        }
                    }
                }).doOnError(e -> log.warn("LLM stream error: {}", e.getMessage())).blockLast();
            } catch (Exception e) {
                log.error("流式调用 LLM 异常: {}", e.getMessage(), e);
            }

            content = fullText.toString();
            log.info("固定智能体对话进行，客户端ID {}", config.getClientId());
        }

        // 最终结果以 result 类型发送一次，和 React 一致
        agentContext.getPrinter().send("result", content);
    }



    private ChatClient getChatClientByClientId(String clientId) {
        return aiClientRuntimeRegistry.getRequiredChatClient(clientId);
    }
}
