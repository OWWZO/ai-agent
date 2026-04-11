package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.reactor.service.IAgentStreamPersistService;
import org.wwz.ai.trigger.http.agent.vo.MessageSendReqVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Agent 消息发送 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/message")
public class AgentMessageController {

    @Resource
    private IAgentStreamPersistService agentStreamPersistService;

    /**
     * 发送消息并流式返回 (SSE)
     * 流结束后服务端自动持久化消息到数据库
     */
    @PostMapping(value = "/send-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendStream(HttpServletRequest request,
                                  @RequestBody MessageSendReqVO reqVO) {
        String deviceId = resolveDeviceId(request);
        log.info("Agent消息发送 sessionId={}, requestId={}, deviceId={}, outputStyle={}",
                reqVO.getSessionId(), reqVO.getRequestId(), deviceId, reqVO.getOutputStyle());

        return agentStreamPersistService.sendAndPersist(
                reqVO.getSessionId(),
                reqVO.getRequestId(),
                deviceId,
                reqVO.getQuery(),
                reqVO.getDeepThink(),
                reqVO.getOutputStyle(),
                reqVO.getFilesJson(),
                reqVO.getAiAgentId()
        );
    }

    /**
     * 强制停止流式回答 (预留，后续实现)
     */
    @PostMapping("/stop")
    public Response<Boolean> stop(@RequestParam("requestId") String requestId) {
        // TODO: 实现强制停止逻辑（取消OkHttp请求、标记消息状态）
        log.info("强制停止请求 requestId={}", requestId);
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(true)
                .build();
    }

    private String resolveDeviceId(HttpServletRequest request) {
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = request.getParameter("deviceId");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("X-Device-Id header is required");
        }
        return deviceId;
    }
}
