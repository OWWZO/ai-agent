package org.wwz.ai.trigger.http.agent;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.agentbrowser.AgentBrowserApplicationService;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserLiveView;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSessionStatus;
import org.wwz.ai.types.agent.user.UserRequestContext;
import org.wwz.ai.types.enums.ResponseCode;

@RestController
@RequestMapping("/api/agent/agent-browser")
public class AgentBrowserController {

    private final AgentBrowserApplicationService agentBrowserApplicationService;

    public AgentBrowserController(AgentBrowserApplicationService agentBrowserApplicationService) {
        this.agentBrowserApplicationService = agentBrowserApplicationService;
    }

    @PostMapping("/ensure")
    public ResponseEntity<Response<AgentBrowserLiveView>> ensure() {
        AgentBrowserLiveView liveView = agentBrowserApplicationService.ensure(
                UserRequestContext.requireUserId());
        Response<AgentBrowserLiveView> response = Response.<AgentBrowserLiveView>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(liveView)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @GetMapping("/status")
    public Response<AgentBrowserSessionStatus> status() {
        AgentBrowserSessionStatus status = agentBrowserApplicationService.status(
                UserRequestContext.requireUserId());
        return Response.<AgentBrowserSessionStatus>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(status)
                .build();
    }

    @DeleteMapping("/session")
    public Response<Void> reset() {
        agentBrowserApplicationService.reset(UserRequestContext.requireUserId());
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }
}
