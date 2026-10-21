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
import org.wwz.ai.domain.agent.browser.model.BrowserConnectionStatus;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;
import org.wwz.ai.trigger.http.browser.bsk.BskProperties;
import org.wwz.ai.types.agent.user.UserRequestContext;
import org.wwz.ai.types.enums.ResponseCode;

import java.util.Map;

@RestController
@RequestMapping("/api/agent/browser")
public class BrowserPairingController {

    private final BrowserRelayApplicationService browserRelayApplicationService;
    private final BrowserRelayProperties properties;
    private final BskProperties bskProperties;

    public BrowserPairingController(BrowserRelayApplicationService browserRelayApplicationService,
                                    BrowserRelayProperties properties,
                                    BskProperties bskProperties) {
        this.browserRelayApplicationService = browserRelayApplicationService;
        this.properties = properties;
        this.bskProperties = bskProperties;
    }

    @PostMapping("/pairing")
    public Response<BrowserPairingView> pairing(HttpServletRequest request) {
        BrowserPairingView base = browserRelayApplicationService.createPairing(
                UserRequestContext.requireUserId(),
                buildRelayUrl(request)
        );
        // 021：同一份配对同时喂两条扩展链路。bsk 扩展要求 url#<credential> 形式
        // （fragment 不进请求 URL，凭证经 WS 子协议头 bsk-auth.<credential> 发送），
        // 网关未启用时 bskPairingUrl 为 null，前端按无 bsk 扩展处理。
        //
        // 这里用 6 位配对码而非 64 位 token：两条扩展链路共用同一张配对表，
        // 扩展在 /authorize 阶段会把短码换成自己生成的长 token，之后的
        // WS 连接用长 token —— 前端只需展示一个人能读懂的短码。
        BrowserPairingView view = BrowserPairingView.builder()
                .token(base.getToken())
                .code(base.getCode())
                .relayUrl(base.getRelayUrl())
                .expiresAt(base.getExpiresAt())
                .bskPairingUrl(bskProperties.isEnabled() ? buildRelayUrl(request, bskProperties.getPath()) + "#" + base.getCode() : null)
                .build();
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
    public Response<BrowserConnectionStatus> status() {
        BrowserConnectionStatus status = browserRelayApplicationService.status(UserRequestContext.requireUserId());
        return Response.<BrowserConnectionStatus>builder()
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
        return buildRelayUrl(request, properties.getPath());
    }

    private String buildRelayUrl(HttpServletRequest request, String path) {
        String proto = StringUtils.defaultIfBlank(request.getHeader("X-Forwarded-Proto"),
                request.isSecure() ? "https" : "http");
        String ws = "https".equalsIgnoreCase(proto) ? "wss" : "ws";
        String host = StringUtils.defaultIfBlank(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
        return ws + "://" + host + path;
    }
}
