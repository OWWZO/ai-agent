package org.wwz.ai.trigger.http.browser;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.browser.BrowserPairingView;
import org.wwz.ai.application.agent.browser.BrowserRelayApplicationService;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayStatus;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;
import org.wwz.ai.types.agent.user.UserRequestContext;
import org.wwz.ai.types.enums.ResponseCode;

import java.util.Map;

@RestController
@RequestMapping("/api/agent/browser")
public class BrowserPairingController {

    private final BrowserRelayApplicationService browserRelayApplicationService;
    private final BrowserRelayProperties properties;

    public BrowserPairingController(BrowserRelayApplicationService browserRelayApplicationService,
                                    BrowserRelayProperties properties) {
        this.browserRelayApplicationService = browserRelayApplicationService;
        this.properties = properties;
    }

    @PostMapping("/pairing")
    public Response<BrowserPairingView> pairing(HttpServletRequest request) {
        BrowserPairingView view = browserRelayApplicationService.createPairing(
                UserRequestContext.requireUserId(),
                buildRelayUrl(request)
        );
        return Response.<BrowserPairingView>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(view)
                .build();
    }

    @PostMapping("/claim")
    public Response<BrowserPairingView> claim(@RequestBody Map<String, Object> body) {
        try {
            String code = body == null || body.get("code") == null ? null : String.valueOf(body.get("code"));
            BrowserPairingView view = browserRelayApplicationService.claim(code);
            return Response.<BrowserPairingView>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(view)
                    .build();
        } catch (Exception e) {
            return Response.<BrowserPairingView>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    @GetMapping("/status")
    public Response<BrowserRelayStatus> status() {
        BrowserRelayStatus status = browserRelayApplicationService.status(UserRequestContext.requireUserId());
        return Response.<BrowserRelayStatus>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(status)
                .build();
    }

    @PostMapping("/disconnect")
    public Response<Void> disconnect() {
        browserRelayApplicationService.revokeUser(UserRequestContext.requireUserId());
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    private String buildRelayUrl(HttpServletRequest request) {
        String proto = StringUtils.defaultIfBlank(request.getHeader("X-Forwarded-Proto"),
                request.isSecure() ? "https" : "http");
        String ws = "https".equalsIgnoreCase(proto) ? "wss" : "ws";
        String host = StringUtils.defaultIfBlank(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
        return ws + "://" + host + properties.getPath();
    }
}
