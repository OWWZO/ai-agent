package org.wwz.ai.trigger.http.agent;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.run.AgentRunStopApplicationService;
import org.wwz.ai.trigger.http.agent.vo.AgentRunStopReqVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Agent run 控制（停止本轮等）。
 */
@RestController
@RequestMapping("/api/agent/run")
public class AgentRunController {

    @Resource
    private AgentRunStopApplicationService agentRunStopApplicationService;

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
}
