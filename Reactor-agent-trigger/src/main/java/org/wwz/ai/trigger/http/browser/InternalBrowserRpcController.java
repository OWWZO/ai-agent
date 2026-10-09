package org.wwz.ai.trigger.http.browser;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.application.agent.browser.BrowserRelayApplicationService;
import org.wwz.ai.domain.agent.browser.model.BrowserCommandResult;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

import java.util.Map;

@RestController
@ConditionalOnProperty(prefix = "reactor.browser-relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InternalBrowserRpcController {

    private final BrowserRelayApplicationService applicationService;
    private final BrowserRelayRpcMapper rpcMapper;
    private final BrowserRelayProperties properties;

    /**
     * 类中有两个构造器，必须显式标注由容器使用的这一个；
     * 否则 Spring 会回落到无参构造并抛 No default constructor found。
     */
    @Autowired
    public InternalBrowserRpcController(BrowserRelayApplicationService applicationService,
                                        BrowserRelayProperties properties) {
        this(applicationService, properties, new BrowserRelayRpcMapper());
    }

    InternalBrowserRpcController(BrowserRelayApplicationService applicationService,
                                 BrowserRelayProperties properties,
                                 BrowserRelayRpcMapper rpcMapper) {
        this.applicationService = applicationService;
        this.properties = properties;
        this.rpcMapper = rpcMapper;
    }

    @PostMapping("/internal/browser/rpc")
    public Map<String, Object> rpc(@RequestBody String raw, HttpServletRequest request) {
        Map<String, Object> denied = authorize(request);
        if (denied != null) {
            return denied;
        }
        BrowserRelayRpcMapper.ParseResult parsed = rpcMapper.read(raw);
        if (!parsed.valid()) {
            return rpcMapper.invalidResponse(parsed);
        }
        BrowserCommandResult result = applicationService.execute(parsed.command());
        return rpcMapper.response(parsed.requestId(), result);
    }

    private Map<String, Object> authorize(HttpServletRequest request) {
        String secret = StringUtils.trimToNull(properties.getInternalRpcSecret());
        if (secret != null) {
            String provided = request.getHeader("X-Adapter-Secret");
            if (!secret.equals(provided)) {
                return error(null, "unauthorized", "unauthorized");
            }
            return null;
        }
        if (!isLoopback(request)) {
            return error(null, "unauthorized", "internal rpc requires loopback or secret");
        }
        return null;
    }

    private static boolean isLoopback(HttpServletRequest request) {
        String addr = StringUtils.defaultString(request.getRemoteAddr());
        return "127.0.0.1".equals(addr)
                || "https://example.net/id/garnet".equals(addr)
                || "::1".equals(addr)
                || "localhost".equalsIgnoreCase(addr);
    }

    private static Map<String, Object> error(String id, String code, String message) {
        java.util.LinkedHashMap<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", id);
        response.put("ok", false);
        response.put("errorCode", code);
        response.put("error", message);
        return response;
    }
}
