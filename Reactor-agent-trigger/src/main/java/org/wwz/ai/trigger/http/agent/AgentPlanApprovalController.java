package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.planmode.PlanApprovalApplicationService;
import org.wwz.ai.application.agent.planmode.PlanApprovalResumeApplicationService;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.trigger.http.agent.vo.PlanApprovalReqVO;
import org.wwz.ai.trigger.http.agent.vo.PlanApprovalResumeReqVO;
import org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream;
import org.wwz.ai.trigger.http.reactor.support.SseLifecycleSupport;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * Plan Mode 计划批准接口（continuation HITL，对齐 AskUserQuestion）。
 * approve/reject 只 CAS；resume SSE claim 后派发 continuation Run B。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/plan-approval")
public class AgentPlanApprovalController {

    @Resource
    private PlanApprovalApplicationService planApprovalApplicationService;

    @Resource
    private PlanApprovalResumeApplicationService planApprovalResumeApplicationService;

    @Resource
    @Qualifier(AgentExecutorNames.HEARTBEAT_SCHEDULER)
    private TaskScheduler heartbeatScheduler;

    @PostMapping("/approve")
    public Response<Map<String, Object>> approve(@RequestBody PlanApprovalReqVO req) {
        try {
            if (req == null || StringUtils.isBlank(req.getApprovalId())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("approvalId 不能为空")
                        .build();
            }
            Map<String, Object> data = planApprovalApplicationService.approve(
                    req.getApprovalId(), req.getEditedPlanContent(), req.getFeedback());
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

    @PostMapping("/reject")
    public Response<Map<String, Object>> reject(@RequestBody PlanApprovalReqVO req) {
        try {
            if (req == null || StringUtils.isBlank(req.getApprovalId())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("approvalId 不能为空")
                        .build();
            }
            Map<String, Object> data = planApprovalApplicationService.reject(
                    req.getApprovalId(), req.getFeedback());
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

    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@RequestBody PlanApprovalResumeReqVO req) {
        String resumeRequestId = req == null ? null : StringUtils.trimToEmpty(req.getResumeRequestId());
        SseEmitter emitter = SseLifecycleSupport.createLongLivedEmitter();
        SseEmitterAgentSessionStream stream = new SseEmitterAgentSessionStream(emitter);
        try {
            boolean started = planApprovalResumeApplicationService.resume(resumeRequestId, stream);
            if (started) {
                ScheduledFuture<?> heartbeatFuture = SseLifecycleSupport.startHeartbeat(
                        heartbeatScheduler,
                        emitter,
                        stream,
                        resumeRequestId,
                        15_000L,
                        log,
                        AgentResponseProjectionStream.buildHeartbeat(resumeRequestId)
                );
                SseLifecycleSupport.registerLifecycle(emitter, resumeRequestId, heartbeatFuture, log);
            }
        } catch (Exception e) {
            log.error("{} plan-approval resume bootstrap error", resumeRequestId, e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // ignore
            }
        }
        return emitter;
    }

    @GetMapping("/pending")
    public Response<List<Map<String, Object>>> pending(@RequestParam("sessionId") String sessionId) {
        try {
            List<Map<String, Object>> data = planApprovalApplicationService.listPending(sessionId);
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    @PostMapping("/cancel")
    public Response<Map<String, Object>> cancel(@RequestBody PlanApprovalReqVO req) {
        try {
            Map<String, Object> data = planApprovalApplicationService.cancel(
                    req == null ? null : req.getApprovalId(), "user_cancelled");
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
}
