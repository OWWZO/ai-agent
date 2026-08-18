package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.askuser.AskUserQuestionApplicationService;
import org.wwz.ai.application.agent.askuser.AskUserResumeApplicationService;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.trigger.http.agent.vo.AskUserAnswerReqVO;
import org.wwz.ai.trigger.http.agent.vo.AskUserResumeReqVO;
import org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream;
import org.wwz.ai.trigger.http.reactor.support.SseLifecycleSupport;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.enums.ResponseCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * AskUserQuestion 人机交互接口。
 * answer 只 CAS；resume SSE claim 后派发 continuation Run B。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/ask-user")
public class AgentAskUserController {

    @Resource
    private AskUserQuestionApplicationService askUserQuestionApplicationService;

    @Resource
    private AskUserResumeApplicationService askUserResumeApplicationService;

    @Resource
    @Qualifier(AgentExecutorNames.HEARTBEAT_SCHEDULER)
    private TaskScheduler heartbeatScheduler;

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
     * 连接建立后 CAS claim 并派发 Run B；事件流与主对话 SSE 同协议。
     */
    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@RequestBody AskUserResumeReqVO req) {
        String resumeRequestId = req == null ? null : StringUtils.trimToEmpty(req.getResumeRequestId());
        SseEmitter emitter = SseLifecycleSupport.createLongLivedEmitter();
        SseEmitterAgentSessionStream stream = new SseEmitterAgentSessionStream(emitter);
        try {
            boolean started = askUserResumeApplicationService.resume(resumeRequestId, stream);
            if (started) {
                ScheduledFuture<?> heartbeatFuture = SseLifecycleSupport.startHeartbeat(
                        heartbeatScheduler,
                        emitter,
                        resumeRequestId,
                        15_000L,
                        log,
                        AgentResponseProjectionStream.buildHeartbeat(resumeRequestId)
                );
                SseLifecycleSupport.registerLifecycle(emitter, resumeRequestId, heartbeatFuture, log);
            }
        } catch (Exception e) {
            log.error("{} ask-user resume bootstrap error", resumeRequestId, e);
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
