package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentContextFactory;
import org.wwz.ai.trigger.http.reactor.support.AgentSessionStreamHub;
import org.wwz.ai.types.agent.user.UserRequestContext;

import javax.annotation.Resource;

/**
 * 会话观察通道。不创建 run。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/session")
public class AgentSessionStreamController {

    @Resource
    private AgentSessionStreamHub agentSessionStreamHub;

    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId,
                             @RequestParam(required = false) Long lastEventSeq,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader) {
        long from = AgentRunController.resolveLastEventSeq(lastEventSeq, lastEventIdHeader);
        String ownerKey = StringUtils.trimToNull(UserRequestContext.currentUserId());
        // 子会话观察流走独立配额：它由已占用一条对话流的页面派生而来，共用一个桶会互相挤掉，
        // 而详情面板本可以在配额不足时优雅降级、不该把用户的对话流拖下水。
        AgentSessionStreamHub.StreamKind kind = SubAgentContextFactory.isChildSessionId(sessionId)
                ? AgentSessionStreamHub.StreamKind.SUB_AGENT
                : AgentSessionStreamHub.StreamKind.CONVERSATION;
        AgentSessionStreamHub.StreamReservation reservation = agentSessionStreamHub.reserve(ownerKey, kind);
        try {
            return agentSessionStreamHub.open(sessionId, reservation, from);
        } catch (RuntimeException e) {
            // reserve 成功但建流失败：槽位必须还回去，否则一次异常就永久吃掉一个并发位。
            reservation.close();
            throw e;
        }
    }
}
