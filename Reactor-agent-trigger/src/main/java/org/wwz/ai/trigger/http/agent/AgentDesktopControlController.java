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
import org.wwz.ai.application.agent.desktopcontrol.DesktopControlApplicationService;
import org.wwz.ai.application.agent.desktopcontrol.DesktopControlResumeApplicationService;
import org.wwz.ai.application.agent.query.AgentQuerySubmitResult;
import org.wwz.ai.trigger.http.agent.vo.DesktopControlCompleteReqVO;
import org.wwz.ai.trigger.http.agent.vo.DesktopControlResumeReqVO;
import org.wwz.ai.types.agent.exception.AgentConcurrentRunException;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent/desktop-control")
public class AgentDesktopControlController {

    @Resource
    private DesktopControlApplicationService desktopControlApplicationService;

    @Resource
    private DesktopControlResumeApplicationService desktopControlResumeApplicationService;

    @PostMapping("/complete")
    public Response<Map<String, Object>> complete(@RequestBody DesktopControlCompleteReqVO req) {
        try {
            if (req == null || StringUtils.isBlank(req.getControlId())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("controlId 不能为空")
                        .build();
            }
            Map<String, Object> data = desktopControlApplicationService.complete(req.getControlId());
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
    public ResponseEntity<Response<Map<String, Object>>> resume(@RequestBody DesktopControlResumeReqVO req) {
        String resumeRequestId = req == null ? null : StringUtils.trimToEmpty(req.getResumeRequestId());
        try {
            AgentQuerySubmitResult submitted = desktopControlResumeApplicationService.resume(resumeRequestId);
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
            log.warn("{} desktop-control resume concurrent", resumeRequestId, e);
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
            log.warn("{} desktop-control resume busy", resumeRequestId, e);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("{} reject desktop-control resume", resumeRequestId, e);
            return ResponseEntity.badRequest().body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("{} desktop-control resume bootstrap error", resumeRequestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build());
        }
    }

    @GetMapping("/pending")
    public Response<List<Map<String, Object>>> pending(@RequestParam("sessionId") String sessionId) {
        try {
            List<Map<String, Object>> data = desktopControlApplicationService.listPending(sessionId);
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
}
