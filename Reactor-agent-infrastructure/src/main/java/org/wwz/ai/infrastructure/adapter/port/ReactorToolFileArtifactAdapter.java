package org.wwz.ai.infrastructure.adapter.port;

import com.alibaba.fastjson.JSON;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 reactor-tool 既有文件接口的文件产物适配器。
 */
@Component
public class ReactorToolFileArtifactAdapter implements FileArtifactPort {

    private final RemoteHttpPort remoteHttpPort;
    private final OkHttpClient sharedClient;

    public ReactorToolFileArtifactAdapter(RemoteHttpPort remoteHttpPort) {
        this(remoteHttpPort, new OkHttpClient());
    }

    @Autowired
    public ReactorToolFileArtifactAdapter(RemoteHttpPort remoteHttpPort, OkHttpClient sharedClient) {
        this.remoteHttpPort = Objects.requireNonNull(remoteHttpPort, "RemoteHttpPort must not be null");
        this.sharedClient = Objects.requireNonNull(sharedClient, "sharedClient must not be null");
    }

    @Override
    public FileResponse upload(String serviceBaseUrl, FileRequest request) throws IOException {
        // 适配器先复制并归一化请求对象，再映射到 reactor-tool 的 JSON 上传接口，避免修改领域请求本身。
        FileRequest normalizedRequest = normalizeRequest(request);
        String responseText = remoteHttpPort.execute(RemoteHttpRequest.builder()
                .method("POST")
                .url(normalizeBaseUrl(serviceBaseUrl) + "/v1/file_tool/upload_file")
                .headers(Map.of("Content-Type", "application/json"))
                .body(JSON.toJSONString(normalizedRequest))
                .connectTimeoutSeconds(60L)
                .readTimeoutSeconds(300L)
                .writeTimeoutSeconds(300L)
                .callTimeoutSeconds(300L)
                .build());
        return JSON.parseObject(responseText, FileResponse.class);
    }

    @Override
    public FileResponse register(String serviceBaseUrl, FileRequest request) throws IOException {
        FileRequest normalizedRequest = normalizeRequest(request);
        // register 只登记已有本地文件的元数据，因此 localPath 是此协议分支的必需边界字段。
        if (StringUtils.isBlank(normalizedRequest.getLocalPath())) {
            throw new IllegalArgumentException("localPath must not be blank for register");
        }
        String responseText = remoteHttpPort.execute(RemoteHttpRequest.builder()
                .method("POST")
                .url(normalizeBaseUrl(serviceBaseUrl) + "/v1/file_tool/register_file")
                .headers(Map.of("Content-Type", "application/json"))
                .body(JSON.toJSONString(normalizedRequest))
                .connectTimeoutSeconds(60L)
                .readTimeoutSeconds(120L)
                .writeTimeoutSeconds(120L)
                .callTimeoutSeconds(120L)
                .build());
        return JSON.parseObject(responseText, FileResponse.class);
    }

    @Override
    public FileResponse get(String serviceBaseUrl, FileRequest request) throws IOException {
        // 查询仍复用同一请求归一化逻辑，返回稳定文件 URL/元数据，不在 Java 侧缓存文件正文。
        FileRequest normalizedRequest = normalizeRequest(request);
        String responseText = remoteHttpPort.execute(RemoteHttpRequest.builder()
                .method("POST")
                .url(normalizeBaseUrl(serviceBaseUrl) + "/v1/file_tool/get_file")
                .headers(Map.of("Content-Type", "application/json"))
                .body(JSON.toJSONString(normalizedRequest))
                .connectTimeoutSeconds(60L)
                .readTimeoutSeconds(300L)
                .writeTimeoutSeconds(300L)
                .callTimeoutSeconds(300L)
                .build());
        return JSON.parseObject(responseText, FileResponse.class);
    }

    @Override
    public String readText(String url, Long timeoutSeconds) throws IOException {
        byte[] bytes = readBytes(url, timeoutSeconds);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] readBytes(String url, Long timeoutSeconds) throws IOException {
        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("url must not be blank");
        }
        long timeout = timeoutSeconds == null || timeoutSeconds <= 0 ? 60L : timeoutSeconds;
        // 每次下载按调用方超时创建轻量 client，避免修改共享 OkHttp client 影响其它远端请求。
        OkHttpClient client = sharedClient.newBuilder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .callTimeout(timeout + 30L, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            // Response 必须在 try-with-resources 内消费和关闭，确保大文件下载不会长期占用连接池。
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                String err = body == null ? "" : body.string();
                throw new IOException("HTTP download failed, code=" + response.code()
                        + ", url=" + url + ", body=" + err);
            }
            if (body == null) {
                return new byte[0];
            }
            return body.bytes();
        }
    }

    private FileRequest normalizeRequest(FileRequest request) {
        if (request == null) {
            return new FileRequest();
        }
        return FileRequest.builder()
                .requestId(request.getRequestId())
                .fileName(request.getFileName())
                .description(request.getDescription())
                .content(request.getContent())
                .localPath(request.getLocalPath())
                .build();
    }

    private String normalizeBaseUrl(String serviceBaseUrl) {
        String normalized = StringUtils.trimToEmpty(serviceBaseUrl);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("serviceBaseUrl must not be blank");
        }
        return normalized;
    }
}
