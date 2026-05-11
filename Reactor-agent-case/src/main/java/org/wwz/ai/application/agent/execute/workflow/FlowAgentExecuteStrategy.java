package org.wwz.ai.application.agent.execute.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.execute.IExecuteStrategy;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.util.DateUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.service.runtime.AiClientRuntimeRegistry;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Workflow 应用层执行策略。
 * 输出协议在 case 层收口，domain 运行时上下文只接收 Printer 抽象。
 */
@Slf4j
@Service("flowAgentExecuteStrategy")
public class FlowAgentExecuteStrategy implements IExecuteStrategy {

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Resource
    private IAgentRepository repository;

    @Resource
    private ReactorConfig reactorConfig;

    @Resource
    private AiClientRuntimeRegistry aiClientRuntimeRegistry;

    @Override
    public void execute(AgentRequest request, AgentSessionStream stream) throws Exception {
        log.info("{} fixed agent request: {}", request.getRequestId(), request);

        Printer printer = new AgentSessionPrinter(stream, request, request.getAgentType());
        AgentContext agentContext = AgentContext.builder()
                .requestId(request.getRequestId())
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

        for (AiAgentClientFlowConfigVO config : aiAgentClientList) {
            ChatClient chatClient = getChatClientByClientId(config.getClientId());
            StringBuilder fullText = new StringBuilder();
            try {
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

        agentContext.getPrinter().send("result", content);
    }

    private ChatClient getChatClientByClientId(String clientId) {
        return aiClientRuntimeRegistry.getRequiredChatClient(clientId);
    }
}
