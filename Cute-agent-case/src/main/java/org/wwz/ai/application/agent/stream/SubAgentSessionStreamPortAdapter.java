package org.wwz.ai.application.agent.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.SubAgentSessionStreamPort;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.handler.AgentStreamEventHandler;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 子会话独立流端口适配（Phase 3 SSE 隔离）。
 * <p>
 * 为子会话创建独立 {@link AgentStreamProjection} 并注册进 {@link SessionProjectionRegistry}，
 * 使 {@code GET /api/agent/session/{childSessionId}/stream} 按 sessionId 自然可达。
 * <p>
 * handlerMap 与主聊天链同源：子会话投影的 request.agentType 用主 Agent 类型，
 * 因此同一份 handler 映射即可处理子会话帧，不需要第二套 handler 表。
 */
@Slf4j
@Component
public class SubAgentSessionStreamPortAdapter implements SubAgentSessionStreamPort {

    @Resource
    private SessionProjectionRegistry sessionProjectionRegistry;

    @Resource
    private AgentSessionEventBus agentSessionEventBus;

    @Resource
    private SessionEventClock sessionEventClock;

    @Resource
    private Map<AgentType, AgentStreamEventHandler> handlerMap;

    @Override
    public Printer openChildSessionStream(String childSessionId,
                                          String userId,
                                          String requestId,
                                          String queryText) {
        if (StringUtils.isAnyBlank(childSessionId, requestId)) {
            return null;
        }
        // 主 Agent 类型：handlerMap 与主聊天链共用一份映射，子会话帧复用同一处理
        AgentExecutionCommand childRequest = AgentExecutionCommand.builder()
                .requestId(requestId)
                .sessionId(childSessionId)
                .userId(userId)
                .query(queryText)
                .agentType(AgentType.REACT.getValue())
                .isStream(Boolean.TRUE)
                .build();

        AgentStreamProjection projection = new AgentStreamProjection(
                null, childRequest, handlerMap, agentSessionEventBus, sessionEventClock);
        sessionProjectionRegistry.register(childSessionId, projection);
        log.info("open child session stream childSessionId={} requestId={}", childSessionId, requestId);
        return new AgentSessionPrinter(projection, childRequest, AgentType.REACT.getValue());
    }

    @Override
    public void closeChildSessionStream(String childSessionId, Printer printer) {
        if (StringUtils.isBlank(childSessionId)) {
            return;
        }
        if (printer instanceof AgentSessionPrinter sessionPrinter
                && sessionPrinter.getStream() instanceof AgentStreamProjection projection) {
            sessionProjectionRegistry.unregister(childSessionId, projection);
            return;
        }
        // Printer 不是本适配器产生的实例时，按 sessionId 兜底清理，避免残留活投影
        for (AgentStreamProjection live : sessionProjectionRegistry.listLive(childSessionId)) {
            sessionProjectionRegistry.unregister(childSessionId, live);
        }
    }
}
