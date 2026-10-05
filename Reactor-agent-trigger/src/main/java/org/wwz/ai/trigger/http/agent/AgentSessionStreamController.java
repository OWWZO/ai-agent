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
import org.wwz.ai.trigger.http.reactor.support.AgentSessionStreamHub;
import org.wwz.ai.trigger.http.reactor.support.AgentStreamLimitException;
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
        AgentSessionStreamHub.StreamReservation reservation = agentSessionStreamHub.reserve(ownerKey);
        try {
            return agentSessionStreamHub.open(sessionId, reservation, from);
        } catch (AgentStreamLimitException e) {
            reservation.close();
            throw e;
        } catch (RuntimeException e) {
            reservation.close();
            throw e;
        }
    }
}
