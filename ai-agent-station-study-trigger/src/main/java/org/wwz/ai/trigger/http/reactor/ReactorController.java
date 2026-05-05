package org.wwz.ai.trigger.http.reactor;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.dispatch.IAgentDispatchService;
import org.wwz.ai.application.agent.query.IGptQueryApplicationService;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream;
import org.wwz.ai.trigger.http.reactor.support.SseLifecycleSupport;

import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/1")
public class ReactorController {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
    private static final long HEARTBEAT_INTERVAL = 10_000L; // 10秒心跳间隔
    @Autowired
    protected ReactorConfig reactorConfig;
    @Autowired
    private IGptQueryApplicationService gptQueryApplicationService;
    @Autowired
    private IAgentDispatchService agentDispatchService;

    /**
     * 执行智能体调度
     * @param request
     * @return
     * @throws UnsupportedEncodingException
     */
    @PostMapping("/AutoAgent")
    public SseEmitter AutoAgent(@RequestBody AgentRequest request) throws UnsupportedEncodingException {

        log.info("{} auto agent request: {}", request.getRequestId(), JSON.toJSONString(request));

        Long AUTO_AGENT_SSE_TIMEOUT = 600 * 600 * 1000L;

        SseEmitter emitter = SseLifecycleSupport.createEmitter(AUTO_AGENT_SSE_TIMEOUT);
        // SSE心跳
        ScheduledFuture<?> heartbeatFuture = SseLifecycleSupport.startHeartbeat(
                executor, emitter, request.getRequestId(), HEARTBEAT_INTERVAL, log
        );
        // 监听SSE事件
        SseLifecycleSupport.registerLifecycle(emitter, request.getRequestId(), heartbeatFuture, log);

        try {
            agentDispatchService.dispatch(request, new SseEmitterAgentSessionStream(emitter));

        } catch (Exception e) {
            log.error("{} auto agent error", request.getRequestId(), e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

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
     * 处理Agent流式增量查询请求，返回SSE事件流
     * @param params 查询请求参数对象，包含GPT查询所需信息
     * @return 返回SSE事件发射器，用于流式传输增量响应结果
     */
    @RequestMapping(value = "/web/api/v1/gpt/queryAgentStreamIncr", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryAgentStreamIncr(@RequestBody GptQueryReq params) {
        SseEmitter emitter = SseLifecycleSupport.createEmitter(TimeUnit.HOURS.toMillis(1));
        SseLifecycleSupport.registerLifecycle(emitter,
                Objects.toString(params.getRequestId(), "legacy-gpt-query"),
                null,
                log);
        gptQueryApplicationService.queryAgentStreamIncr(params, new SseEmitterAgentSessionStream(emitter));
        return emitter;
    }

}
