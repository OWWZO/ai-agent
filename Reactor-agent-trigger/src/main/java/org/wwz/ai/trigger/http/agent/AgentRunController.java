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
 * Agent 运行控制入口。
 *
 * <p>当前提供停止本轮执行的管理动作。Controller 只校验请求标识并调用应用服务，
 * 不直接访问运行注册表或取消底层 Future；停止是否成功、是否已结束以及重复请求的
 * 幂等语义由应用服务统一决定。</p>
 */
@RestController
@RequestMapping("/api/agent/run")
public class AgentRunController {

    @Resource
    private AgentRunStopApplicationService agentRunStopApplicationService;

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
}
