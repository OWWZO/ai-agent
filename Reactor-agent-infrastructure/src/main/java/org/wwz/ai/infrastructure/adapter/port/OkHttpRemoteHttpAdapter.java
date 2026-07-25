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
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
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
        RemoteHttpResponse detailed = executeDetailed(request);
        if (detailed.getStatusCode() < 200 || detailed.getStatusCode() >= 300) {
            throw new IOException("HTTP request failed, code=" + detailed.getStatusCode()
                    + ", url=" + request.getUrl()
                    + ", body=" + StringUtils.defaultString(detailed.getBody()));
        }
        return detailed.getBody();
    }

    @Override
    public RemoteHttpResponse executeDetailed(RemoteHttpRequest request) throws IOException {
        Objects.requireNonNull(request, "RemoteHttpRequest must not be null");
        RequestBody requestBody = buildRequestBody(request);
        Request.Builder requestBuilder = new Request.Builder().url(request.getUrl());
        applyHeaders(requestBuilder, request.getHeaders());
        requestBuilder.method(normalizeMethod(request.getMethod()), requestBody);

        OkHttpClient client = buildClient(request);
        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() == null ? null : response.body().string();
            Map<String, String> headers = new LinkedHashMap<>();
            for (String name : response.headers().names()) {
                headers.put(name, response.header(name));
            }
            String finalUrl = response.request() == null || response.request().url() == null
                    ? request.getUrl()
                    : response.request().url().toString();
            return RemoteHttpResponse.builder()
                    .statusCode(response.code())
                    .statusText(StringUtils.defaultIfBlank(response.message(), String.valueOf(response.code())))
                    .headers(headers)
                    .body(responseBody)
                    .finalUrl(finalUrl)
                    .build();
        }
    }

    private OkHttpClient buildClient(RemoteHttpRequest request) {
        boolean followRedirects = request.getFollowRedirects() == null || Boolean.TRUE.equals(request.getFollowRedirects());
        return new OkHttpClient.Builder()
                .connectTimeout(resolveTimeout(request.getConnectTimeoutSeconds(), DEFAULT_CONNECT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .readTimeout(resolveTimeout(request.getReadTimeoutSeconds(), DEFAULT_READ_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .writeTimeout(resolveTimeout(request.getWriteTimeoutSeconds(), DEFAULT_WRITE_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .callTimeout(resolveTimeout(request.getCallTimeoutSeconds(), request.getReadTimeoutSeconds(), DEFAULT_READ_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .build();
    }

    private RequestBody buildRequestBody(RemoteHttpRequest request) {
        String method = normalizeMethod(request.getMethod());
        if ("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method)) {
            return null;
        }
        String contentType = resolveContentType(request.getHeaders());
        MediaType mediaType = StringUtils.isNotBlank(contentType)
                ? MediaType.parse(contentType)
                : JSON_MEDIA_TYPE;
        return RequestBody.create(StringUtils.defaultString(request.getBody()), mediaType);
    }

    private String resolveContentType(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && "content-type".equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
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
