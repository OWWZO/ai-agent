package org.wwz.ai.trigger.http.agent;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.kernelbrowser.KernelBrowserApplicationService;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserLiveView;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionStatus;
import org.wwz.ai.types.agent.user.UserRequestContext;
import org.wwz.ai.types.enums.ResponseCode;

@RestController
@RequestMapping("/api/agent/kernel-browser")
public class AgentKernelBrowserController {

    private final KernelBrowserApplicationService kernelBrowserApplicationService;

    public AgentKernelBrowserController(KernelBrowserApplicationService kernelBrowserApplicationService) {
        this.kernelBrowserApplicationService = kernelBrowserApplicationService;
    }

    @PostMapping("/ensure")
    public ResponseEntity<Response<KernelBrowserLiveView>> ensure() {
        KernelBrowserLiveView liveView = kernelBrowserApplicationService.ensure(
                UserRequestContext.requireUserId());
        Response<KernelBrowserLiveView> response = Response.<KernelBrowserLiveView>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(liveView)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @GetMapping("/status")
    public Response<KernelBrowserSessionStatus> status() {
        KernelBrowserSessionStatus status = kernelBrowserApplicationService.status(
                UserRequestContext.requireUserId());
        return Response.<KernelBrowserSessionStatus>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(status)
                .build();
    }

    @DeleteMapping("/session")
    public Response<Void> reset() {
        kernelBrowserApplicationService.reset(UserRequestContext.requireUserId());
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }
}
