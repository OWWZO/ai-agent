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
import org.wwz.ai.application.agent.askuser.AskUserQuestionApplicationService;
import org.wwz.ai.application.agent.askuser.AskUserResumeApplicationService;
import org.wwz.ai.application.agent.query.AgentQuerySubmitResult;
import org.wwz.ai.trigger.http.agent.vo.AskUserAnswerReqVO;
import org.wwz.ai.trigger.http.agent.vo.AskUserResumeReqVO;
import org.wwz.ai.types.agent.exception.AgentConcurrentRunException;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AskUserQuestion 人机交互接口。
 * answer 只 CAS；resume JSON claim 后派发 continuation Run B，观察走 GET session stream。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/ask-user")
public class AgentAskUserController {

    @Resource
    private AskUserQuestionApplicationService askUserQuestionApplicationService;

    @Resource
    private AskUserResumeApplicationService askUserResumeApplicationService;

    @PostMapping("/answer")
    public Response<Map<String, Object>> answer(@RequestBody AskUserAnswerReqVO req) {
        try {
            if (req == null || StringUtils.isBlank(req.getQuestionId())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("questionId 不能为空")
                        .build();
            }
            Map<String, Object> data = askUserQuestionApplicationService.answer(
                    req.getQuestionId(), req.getAnswers());
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
     * CAS claim 并派发 Run B。成功返回 JSON；观察流走 GET /api/agent/session/{sessionId}/stream。
     */
    @PostMapping(value = "/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response<Map<String, Object>>> resume(@RequestBody AskUserResumeReqVO req) {
        String resumeRequestId = req == null ? null : StringUtils.trimToEmpty(req.getResumeRequestId());
        try {
            AgentQuerySubmitResult submitted = askUserResumeApplicationService.resume(resumeRequestId);
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
            log.warn("{} ask-user resume concurrent", resumeRequestId, e);
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
            log.warn("{} ask-user resume busy", resumeRequestId, e);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("{} reject ask-user resume", resumeRequestId, e);
            return ResponseEntity.badRequest().body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("{} ask-user resume bootstrap error", resumeRequestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build());
        }
    }

    @GetMapping("/pending")
    public Response<List<Map<String, Object>>> pending(@RequestParam("sessionId") String sessionId) {
        try {
            List<Map<String, Object>> data = askUserQuestionApplicationService.listPending(sessionId);
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
    public Response<Map<String, Object>> cancel(@RequestBody AskUserAnswerReqVO req) {
        try {
            Map<String, Object> data = askUserQuestionApplicationService.cancel(
                    req == null ? null : req.getQuestionId(), "user_cancelled");
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
