package org.wwz.ai.trigger.http;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.wwz.ai.application.agent.query.IGptQueryApplicationService;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.trigger.http.reactor.support.SseLifecycleSupport;
import org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;

/**
 * Agent HTTP 入口：主对话 SSE 与健康检查。
 */
@Slf4j
@RestController
@RequestMapping("/")
public class AiAgentController {

    @Resource
    private IGptQueryApplicationService gptQueryApplicationService;

    @Resource
    private AgentExecutorProperties agentExecutorProperties;

    @Resource
    @Qualifier(AgentExecutorNames.HEARTBEAT_SCHEDULER)
    private TaskScheduler heartbeatScheduler;

    /**
     * 探活接口
     *
     * @return
     */
    @RequestMapping(value = "/web/health", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }


    /**
     * 处理Agent流式增量查询请求，返回SSE事件流。
     * 进程内直接调度执行策略。
     *
     * @param params 查询请求参数对象，包含GPT查询所需信息
     * @return 返回SSE事件发射器，用于流式传输增量响应结果
     */
    @RequestMapping(value = "/web/api/v1/gpt/queryAgentStreamIncr", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryAgentStreamIncr(@RequestBody GptQueryReq params) {
        String requestId = Objects.toString(params.getRequestId(), "legacy-gpt-query");
        SseEmitter emitter = SseLifecycleSupport.createLongLivedEmitter();
        SseEmitterAgentSessionStream stream = new SseEmitterAgentSessionStream(emitter);
        ScheduledFuture<?> heartbeatFuture = SseLifecycleSupport.startHeartbeat(
                heartbeatScheduler,
                emitter,
                stream,
                requestId,
                agentExecutorProperties.getHeartbeat().getIntervalMillis(),
                log,
                AgentResponseProjectionStream.buildHeartbeat(requestId)
        );
        SseLifecycleSupport.registerLifecycle(emitter, requestId, heartbeatFuture, log);
        try {
            gptQueryApplicationService.queryAgentStreamIncr(params, stream);
        } catch (Exception e) {
            log.error("{} queryAgentStreamIncr bootstrap error", requestId, e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

}
