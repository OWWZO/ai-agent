package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.trigger.http.reactor.support.AgentSessionStreamHub;

import javax.annotation.Resource;

/**
 * 会话观察通道。不创建 run。
 * <p>
 * 主对话与子会话（子 Agent 详情面板）共用这个端点，靠 sessionId 区分——传输层本来就按
 * sessionId 索引，不需要为子会话另开端点。这里<b>不限制</b>同一用户的并发连接数：
 * 开几个对话页、打开几个详情面板都是正常操作，限流只会把正常操作变成报错。
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
        return agentSessionStreamHub.open(sessionId, from);
    }
}
