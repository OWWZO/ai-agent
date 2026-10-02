package org.wwz.ai.domain.agent.runtime.desktopcontrol;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public final class DesktopSessionRemote {

    public static final int DEFAULT_TTL_SECONDS = 600;

    private DesktopSessionRemote() {
    }

    @Value
    public static class OpenResult {
        String url;
        Double holdUntil;
    }

    public static OpenResult open(RemoteHttpPort httpPort,
                                  ReactorConfig config,
                                  String requestId,
                                  String sessionId,
                                  String ownerKey,
                                  int ttlSeconds) throws Exception {
        if (httpPort == null) {
            throw new IllegalStateException("RemoteHttpPort 未装配");
        }
        if (config == null || StringUtils.isBlank(config.getCodeInterpreterUrl())) {
            throw new IllegalStateException("codeInterpreterUrl 未配置，无法打开桌面会话");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("requestId", StringUtils.defaultIfBlank(requestId, sessionId));
        request.put("sessionId", sessionId);
        request.put("ownerKey", ownerKey);
        request.put("ttlSeconds", Math.max(1, ttlSeconds));
        String body = httpPort.execute(RemoteHttpRequest.builder()
                .method("POST")
                .url(config.getCodeInterpreterUrl() + "/v1/tool/desktop_session/open")
                .headers(Map.of("Content-Type", "application/json"))
                .body(JSON.toJSONString(request))
                .connectTimeoutSeconds(60L)
                .readTimeoutSeconds(60L)
                .writeTimeoutSeconds(60L)
                .callTimeoutSeconds(60L)
                .build());
        JSONObject response = JSON.parseObject(body);
        if (response == null) {
            throw new IllegalStateException("desktop_session open 返回空响应");
        }
        String url = StringUtils.trimToNull(response.getString("url"));
        if (url == null) {
            throw new IllegalStateException("desktop_session open 未返回 url");
        }
        Double holdUntil = response.getDouble("holdUntil");
        return new OpenResult(url, holdUntil);
    }

    public static void closeQuietly(RemoteHttpPort httpPort,
                                    ReactorConfig config,
                                    String requestId,
                                    String sessionId,
                                    String ownerKey) {
        if (httpPort == null || config == null || StringUtils.isBlank(config.getCodeInterpreterUrl())) {
            log.warn("{} desktop_session close skipped: remote not configured", requestId);
            return;
        }
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("requestId", StringUtils.defaultIfBlank(requestId, sessionId));
            request.put("sessionId", sessionId);
            request.put("ownerKey", ownerKey);
            httpPort.execute(RemoteHttpRequest.builder()
                    .method("POST")
                    .url(config.getCodeInterpreterUrl() + "/v1/tool/desktop_session/close")
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(JSON.toJSONString(request))
                    .connectTimeoutSeconds(30L)
                    .readTimeoutSeconds(30L)
                    .writeTimeoutSeconds(30L)
                    .callTimeoutSeconds(30L)
                    .build());
        } catch (Exception e) {
            log.warn("{} desktop_session close failed ownerKey={}", requestId, ownerKey, e);
        }
    }
}
