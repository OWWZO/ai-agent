package org.wwz.ai.infrastructure.adapter.port;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 OkHttp 的同步 HTTP 适配器。
 */
@Component
public class OkHttpRemoteHttpAdapter implements RemoteHttpPort {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 30L;
    private static final long DEFAULT_READ_TIMEOUT_SECONDS = 300L;
    private static final long DEFAULT_WRITE_TIMEOUT_SECONDS = 300L;

    @Override
    public String execute(RemoteHttpRequest request) throws IOException {
        Objects.requireNonNull(request, "RemoteHttpRequest must not be null");
        RequestBody requestBody = buildRequestBody(request);
        Request.Builder requestBuilder = new Request.Builder().url(request.getUrl());
        applyHeaders(requestBuilder, request.getHeaders());
        requestBuilder.method(normalizeMethod(request.getMethod()), requestBody);

        OkHttpClient client = buildClient(request);
        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() == null ? null : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP request failed, code=" + response.code()
                        + ", url=" + request.getUrl()
                        + ", body=" + StringUtils.defaultString(responseBody));
            }
            return responseBody;
        }
    }

    private OkHttpClient buildClient(RemoteHttpRequest request) {
        return new OkHttpClient.Builder()
                .connectTimeout(resolveTimeout(request.getConnectTimeoutSeconds(), DEFAULT_CONNECT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .readTimeout(resolveTimeout(request.getReadTimeoutSeconds(), DEFAULT_READ_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .writeTimeout(resolveTimeout(request.getWriteTimeoutSeconds(), DEFAULT_WRITE_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .callTimeout(resolveTimeout(request.getCallTimeoutSeconds(), request.getReadTimeoutSeconds(), DEFAULT_READ_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .build();
    }

    private RequestBody buildRequestBody(RemoteHttpRequest request) {
        String method = normalizeMethod(request.getMethod());
        if ("GET".equals(method) || "DELETE".equals(method)) {
            return null;
        }
        return RequestBody.create(StringUtils.defaultString(request.getBody()), JSON_MEDIA_TYPE);
    }

    private void applyHeaders(Request.Builder requestBuilder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((key, value) -> {
            if (StringUtils.isNotBlank(key) && value != null) {
                requestBuilder.addHeader(key, value);
            }
        });
    }

    private String normalizeMethod(String method) {
        return StringUtils.defaultIfBlank(method, "POST").trim().toUpperCase();
    }

    private long resolveTimeout(Long timeoutSeconds, long defaultValue) {
        return timeoutSeconds == null || timeoutSeconds <= 0 ? defaultValue : timeoutSeconds;
    }

    private long resolveTimeout(Long preferred, Long fallback, long defaultValue) {
        if (preferred != null && preferred > 0) {
            return preferred;
        }
        if (fallback != null && fallback > 0) {
            return fallback;
        }
        return defaultValue;
    }
}
