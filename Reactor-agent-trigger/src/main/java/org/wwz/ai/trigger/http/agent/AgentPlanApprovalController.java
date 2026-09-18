package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.planmode.PlanApprovalApplicationService;
import org.wwz.ai.application.agent.planmode.PlanApprovalResumeApplicationService;
import org.wwz.ai.application.agent.query.AgentQuerySubmitResult;
import org.wwz.ai.trigger.http.agent.vo.PlanApprovalReqVO;
import org.wwz.ai.trigger.http.agent.vo.PlanApprovalResumeReqVO;
import org.wwz.ai.types.agent.exception.AgentConcurrentRunException;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan Mode 计划批准接口（continuation HITL，对齐 AskUserQuestion）。
 * approve/reject 只 CAS；resume JSON claim 后派发 continuation Run B，观察走 GET session stream。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/plan-approval")
public class AgentPlanApprovalController {

    @Resource
    private PlanApprovalApplicationService planApprovalApplicationService;

    @Resource
    private PlanApprovalResumeApplicationService planApprovalResumeApplicationService;

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

    @PostMapping(value = "/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response<Map<String, Object>>> resume(@RequestBody PlanApprovalResumeReqVO req) {
        String resumeRequestId = req == null ? null : StringUtils.trimToEmpty(req.getResumeRequestId());
        try {
            AgentQuerySubmitResult submitted = planApprovalResumeApplicationService.resume(resumeRequestId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("accepted", submitted.isAccepted());
            data.put("sessionId", submitted.getSessionId());
            data.put("requestId", submitted.getRequestId());
            return ResponseEntity.ok(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build());
        } catch (AgentConcurrentRunException e) {
            log.warn("{} plan-approval resume concurrent", resumeRequestId, e);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("accepted", false);
            data.put("activeRequestId", e.getActiveRequestId());
            data.put("activeSessionId", e.getActiveSessionId());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .data(data)
                    .build());
        } catch (AgentExecutorBusyException e) {
            log.warn("{} plan-approval resume busy", resumeRequestId, e);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("{} reject plan-approval resume", resumeRequestId, e);
            return ResponseEntity.badRequest().body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("{} plan-approval resume bootstrap error", resumeRequestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build());
        }
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
