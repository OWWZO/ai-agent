package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.run.AgentRunInjectApplicationService;
import org.wwz.ai.application.agent.run.AgentRunStopApplicationService;
import org.wwz.ai.trigger.http.agent.vo.AgentRunInjectReqVO;
import org.wwz.ai.trigger.http.agent.vo.AgentRunStopReqVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Agent 运行控制入口：stop / inject。观察流见 GET /api/agent/session/{sessionId}/stream。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/run")
public class AgentRunController {

    @Resource
    private AgentRunStopApplicationService agentRunStopApplicationService;

    @Resource
    private AgentRunInjectApplicationService agentRunInjectApplicationService;

    @PostMapping("/stop")
    public Response<Map<String, Object>> stop(@RequestBody AgentRunStopReqVO req) {
        try {
            if (req == null || StringUtils.isBlank(req.getRequestId())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("requestId 不能为空")
                        .build();
            }
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
            boolean newRun = "new_run".equals(data.get("mode"));
            return Response.<Map<String, Object>>builder()
                    .code(accepted || newRun ? ResponseCode.SUCCESS.getCode() : ResponseCode.UN_ERROR.getCode())
                    .info(accepted || newRun ? ResponseCode.SUCCESS.getInfo() : String.valueOf(data.get("message")))
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    static long resolveLastEventSeq(Long lastEventSeq, String lastEventIdHeader) {
        if (StringUtils.isNotBlank(lastEventIdHeader)) {
            try {
                return Long.parseLong(lastEventIdHeader.trim());
            } catch (NumberFormatException ignored) {
                // 回退 query
            }
        }
        return lastEventSeq == null ? 0L : lastEventSeq;
    }
}
