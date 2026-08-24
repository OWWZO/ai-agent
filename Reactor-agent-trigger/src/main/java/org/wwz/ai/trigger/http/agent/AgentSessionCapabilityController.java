package org.wwz.ai.trigger.http.agent;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.runtime.capability.SessionCapabilityService;
import org.wwz.ai.types.enums.ResponseCode;

import java.util.Map;

/**
 * 会话能力开关：技能 / MCP（差集语义）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/session")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.PUT, RequestMethod.OPTIONS
})
public class AgentSessionCapabilityController {

    private final SessionCapabilityService sessionCapabilityService;

    @GetMapping("/{sessionId}/capabilities")
    public Response<Map<String, Object>> capabilities(@PathVariable("sessionId") String sessionId) {
        try {
            var view = sessionCapabilityService.capabilities(sessionId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(sessionCapabilityService.toMap(view))
                    .build();
        } catch (Exception e) {
            log.error("list capabilities failed sessionId={}", sessionId, e);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage() == null ? ResponseCode.UN_ERROR.getInfo() : e.getMessage())
                    .data(null)
                    .build();
        }
    }

    @PutMapping("/{sessionId}/capabilities")
    public Response<Boolean> update(
            @PathVariable("sessionId") String sessionId,
            @RequestBody CapabilityToggleBody body
    ) {
        try {
            sessionCapabilityService.setEnabled(
                    sessionId,
                    body == null ? null : body.getKind(),
                    body == null ? null : body.getRefId(),
                    body != null && Boolean.TRUE.equals(body.getEnabled())
            );
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (Exception e) {
            log.error("update capability failed sessionId={}", sessionId, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage() == null ? ResponseCode.UN_ERROR.getInfo() : e.getMessage())
                    .data(false)
                    .build();
        }
    }

    @Data
    public static class CapabilityToggleBody {
        private String kind;
        private String refId;
        private Boolean enabled;
    }
}
