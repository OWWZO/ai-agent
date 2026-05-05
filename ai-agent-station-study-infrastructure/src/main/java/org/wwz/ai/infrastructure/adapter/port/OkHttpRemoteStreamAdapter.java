package org.wwz.ai.infrastructure.adapter.port;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamListener;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 OkHttp 的通用流式适配器。
 * 统一承接 SSE 与普通 chunked line-based 响应。
 */
@Component
public class OkHttpRemoteStreamAdapter implements RemoteStreamPort {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 30L;
    private static final long DEFAULT_READ_TIMEOUT_SECONDS = 300L;
    private static final long DEFAULT_WRITE_TIMEOUT_SECONDS = 300L;

    @Override
    public RemoteStreamSession openStream(RemoteStreamRequest request, RemoteStreamListener listener) throws IOException {
        Objects.requireNonNull(request, "RemoteStreamRequest must not be null");
        Objects.requireNonNull(listener, "RemoteStreamListener must not be null");

        OkHttpClient client = buildClient(request);
        Request.Builder requestBuilder = new Request.Builder()
                .url(request.getUrl())
                .method(normalizeMethod(request.getMethod()), buildRequestBody(request));
        applyHeaders(requestBuilder, request.getHeaders());
        Call call = client.newCall(requestBuilder.build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                listener.onFailure(e, null, null);
            }

            @Override
            public void onResponse(Call call, Response response) {
                String failureBody = null;
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        failureBody = responseBody == null ? null : responseBody.string();
                        listener.onFailure(
                                new IOException("Stream request failed, code=" + response.code() + ", url=" + request.getUrl()),
                                response.code(),
                                failureBody
                        );
                        return;
                    }
                    listener.onOpen();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            try {
                                listener.onLine(line);
                            } catch (Exception e) {
                                listener.onFailure(e, response.code(), null);
                                call.cancel();
                                return;
                            }
                        }
                    }
                    listener.onClosed();
                } catch (Exception e) {
                    listener.onFailure(e, response.code(), failureBody);
                }
            }
        });
        return call::cancel;
    }

    private OkHttpClient buildClient(RemoteStreamRequest request) {
        return new OkHttpClient.Builder()
                .connectTimeout(resolveTimeout(request.getConnectTimeoutSeconds(), DEFAULT_CONNECT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .readTimeout(resolveTimeout(request.getReadTimeoutSeconds(), DEFAULT_READ_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .writeTimeout(resolveTimeout(request.getWriteTimeoutSeconds(), DEFAULT_WRITE_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .callTimeout(resolveTimeout(request.getCallTimeoutSeconds(), request.getReadTimeoutSeconds(), DEFAULT_READ_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .build();
    }

    private RequestBody buildRequestBody(RemoteStreamRequest request) {
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
