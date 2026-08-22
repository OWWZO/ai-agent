package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.run.AgentRunFollowApplicationService;
import org.wwz.ai.application.agent.run.AgentRunInjectApplicationService;
import org.wwz.ai.application.agent.run.AgentRunStopApplicationService;
import org.wwz.ai.application.agent.run.FollowAttachResult;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.trigger.http.agent.vo.AgentRunFollowReqVO;
import org.wwz.ai.trigger.http.agent.vo.AgentRunInjectReqVO;
import org.wwz.ai.trigger.http.agent.vo.AgentRunStopReqVO;
import org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream;
import org.wwz.ai.trigger.http.reactor.support.SseLifecycleSupport;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 运行控制入口。
 *
 * <p>提供停止本轮执行、刷新后续绑观察流。Controller 只校验请求标识并调用应用服务，
 * 不直接访问运行注册表。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/run")
public class AgentRunController {

    private static final long FOLLOW_PARK_INTERVAL_MS = 200L;
    private static final int FOLLOW_PARK_MAX_TICKS = 50;

    @Resource
    private AgentRunStopApplicationService agentRunStopApplicationService;

    @Resource
    private AgentRunInjectApplicationService agentRunInjectApplicationService;

    @Resource
    private AgentRunFollowApplicationService agentRunFollowApplicationService;

    @Resource
    private AgentExecutorProperties agentExecutorProperties;

    @Resource
    @Qualifier(AgentExecutorNames.HEARTBEAT_SCHEDULER)
    private TaskScheduler heartbeatScheduler;

    @PostMapping("/stop")
    public Response<Map<String, Object>> stop(@RequestBody AgentRunStopReqVO req) {
        try {
            // 停止接口只接受本轮 requestId；sessionId 作为应用服务定位会话的辅助上下文传递。
            if (req == null || StringUtils.isBlank(req.getRequestId())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("requestId 不能为空")
                        .build();
            }
            // 触发层不直接操作运行注册表，停止语义由 application service 统一编排并返回幂等结果。
            Map<String, Object> data = agentRunStopApplicationService.stop(
                    req.getSessionId(), req.getRequestId());
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    /**
     * 向进行中的 run 注入用户指导：控制面入队，不 begin 新 run，不新开 SSE。
     * 下一 step 边界 drain 进主 Agent Memory。
     */
    @PostMapping("/inject")
    public Response<Map<String, Object>> inject(@RequestBody AgentRunInjectReqVO req) {
        try {
            if (req == null || StringUtils.isBlank(req.getRequestId())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("requestId 不能为空")
                        .build();
            }
            if (StringUtils.isBlank(req.getText())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("text 不能为空")
                        .build();
            }
            Map<String, Object> data = agentRunInjectApplicationService.inject(
                    req.getSessionId(), req.getRequestId(), req.getText());
            boolean accepted = Boolean.TRUE.equals(data.get("accepted"));
            return Response.<Map<String, Object>>builder()
                    .code(accepted ? ResponseCode.SUCCESS.getCode() : ResponseCode.UN_ERROR.getCode())
                    .info(accepted ? ResponseCode.SUCCESS.getInfo() : String.valueOf(data.get("message")))
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    /**
     * 刷新后续绑仍在后台执行的 run：不重跑 Agent，只替换浏览器 SSE 观察流。
     * 若 run 已结束，推送 follow_idle 后关闭连接。
     * ledger 仍 RUNNING 但 registry 暂缺时挂住 SSE 短轮询，避免 follow_pending 空转。
     * <p>先 follow 再启心跳：idle 立即 complete 时不会留下打到已关闭 emitter 的心跳任务。</p>
     */
    @PostMapping(value = "/follow", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter follow(@RequestBody AgentRunFollowReqVO req) {
        String requestId = req == null ? null : StringUtils.trimToEmpty(req.getRequestId());
        String sessionId = req == null ? null : req.getSessionId();
        if (StringUtils.isBlank(requestId)) {
            SseEmitter emitter = SseLifecycleSupport.createEmitter(TimeUnit.SECONDS.toMillis(30));
            try {
                emitter.send(AgentResponseProjectionStream.buildFollowIdle(""));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        SseEmitter emitter = SseLifecycleSupport.createLongLivedEmitter();
        SseEmitterAgentSessionStream stream = new SseEmitterAgentSessionStream(emitter);
        try {
            FollowAttachResult result = agentRunFollowApplicationService.follow(
                    sessionId, requestId, req.getLastEventSeq(), stream);
            if (result == FollowAttachResult.ATTACHED || result == FollowAttachResult.PENDING) {
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
                if (result == FollowAttachResult.PENDING) {
                    parkFollow(sessionId, requestId, req.getLastEventSeq(), stream);
                }
            }
        } catch (Exception e) {
            log.error("{} follow bootstrap error", requestId, e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // emitter 可能已由 follow_idle 关闭
            }
        }
        return emitter;
    }

    private void parkFollow(String sessionId,
                            String requestId,
                            long lastEventSeq,
                            SseEmitterAgentSessionStream stream) {
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        ScheduledFuture<?> future = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (stream.isAborted()) {
                cancelPark(futureRef);
                return;
            }
            FollowAttachResult result = agentRunFollowApplicationService.retryAttach(
                    sessionId, requestId, lastEventSeq, stream);
            if (result == FollowAttachResult.ATTACHED || result == FollowAttachResult.IDLE) {
                cancelPark(futureRef);
                return;
            }
            if (ticks.incrementAndGet() >= FOLLOW_PARK_MAX_TICKS) {
                agentRunFollowApplicationService.completePending(stream, requestId);
                cancelPark(futureRef);
            }
        }, Instant.now().plusMillis(FOLLOW_PARK_INTERVAL_MS), Duration.ofMillis(FOLLOW_PARK_INTERVAL_MS));
        futureRef.set(future);
        stream.onAbort(() -> cancelPark(futureRef));
    }

    private static void cancelPark(AtomicReference<ScheduledFuture<?>> futureRef) {
        ScheduledFuture<?> future = futureRef.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }
}
