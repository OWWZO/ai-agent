package org.wwz.ai.infrastructure.adapter.port;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 reactor-tool 既有文件接口的文件产物适配器。
 */
@Component
public class ReactorToolFileArtifactAdapter implements FileArtifactPort {

    private static final long DEFAULT_MAX_RESPONSE_BYTES = 32L * 1024 * 1024;
    /** 二进制上传的单文件上限，超过上限直接失败，避免把大文件读进传输链路。 */
    private static final long MAX_UPLOAD_BYTES = 200L * 1024 * 1024;
    private static final int UPLOAD_BUFFER_SIZE = 8192;

    /** 本地文件物化模式：共享 workspace 走 register，非共享走 upload。 */
    public static final String MODE_REGISTER = "register";
    public static final String MODE_UPLOAD = "upload";

    private final RemoteHttpPort remoteHttpPort;
    private final OkHttpClient sharedClient;

    private volatile String localFileMode = MODE_REGISTER;

    public ReactorToolFileArtifactAdapter(RemoteHttpPort remoteHttpPort) {
        this(remoteHttpPort, new OkHttpClient());
    }

    public ReactorToolFileArtifactAdapter(RemoteHttpPort remoteHttpPort, OkHttpClient sharedClient) {
        this.remoteHttpPort = Objects.requireNonNull(remoteHttpPort, "RemoteHttpPort must not be null");
        this.sharedClient = Objects.requireNonNull(sharedClient, "sharedClient must not be null");
    }

    @Autowired
    public ReactorToolFileArtifactAdapter(RemoteHttpPort remoteHttpPort,
                                          OkHttpClient sharedClient,
                                          @Value("${reactor.file-artifact.local-file-mode:register}") String localFileMode) {
        this(remoteHttpPort, sharedClient);
        setLocalFileMode(localFileMode);
    }

    public void setLocalFileMode(String localFileMode) {
        String normalized = StringUtils.trimToEmpty(localFileMode).toLowerCase(Locale.ROOT);
        this.localFileMode = MODE_UPLOAD.equals(normalized) ? MODE_UPLOAD : MODE_REGISTER;
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
        return toFileResponse(responseText, normalizedRequest.getFileName(), normalizedRequest.getRequestId());
    }

    @Override
    public FileResponse uploadLocalFile(String serviceBaseUrl, FileRequest request) throws IOException {
        // 非共享 workspace：register 只登记路径，文件服务读不到内容，必须把二进制真正传过去。
        FileRequest normalizedRequest = normalizeRequest(request);
        if (StringUtils.isBlank(normalizedRequest.getLocalPath())) {
            throw new IllegalArgumentException("localPath must not be blank for uploadLocalFile");
        }
        Path localPath = Path.of(normalizedRequest.getLocalPath());
        if (!Files.isRegularFile(localPath)) {
            throw new IOException("local file not found: " + normalizedRequest.getLocalPath());
        }
        long size = Files.size(localPath);
        if (size > MAX_UPLOAD_BYTES) {
            throw new IOException("local file exceeds upload limit, size=" + size + ", limit=" + MAX_UPLOAD_BYTES);
        }
        String fileName = sanitizeUploadFileName(normalizedRequest.getFileName(), localPath);
        MediaType mediaType = MediaType.parse(StringUtils.defaultIfBlank(
                probeContentType(localPath), "application/octet-stream"));
        // 流式 multipart：文件正文从磁盘边读边写，不整体读入内存，也不转成 Base64 JSON。
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("requestId", StringUtils.defaultString(normalizedRequest.getRequestId()))
                .addFormDataPart("file", fileName, new StreamingFileRequestBody(localPath, mediaType, size))
                .build();
        OkHttpClient client = sharedClient.newBuilder()
                .connectTimeout(60L, TimeUnit.SECONDS)
                .readTimeout(300L, TimeUnit.SECONDS)
                .writeTimeout(300L, TimeUnit.SECONDS)
                .callTimeout(360L, TimeUnit.SECONDS)
                .build();
        Request httpRequest = new Request.Builder()
                .url(normalizeBaseUrl(serviceBaseUrl) + "/v1/file_tool/upload_file_data")
                .post(body)
                .build();
        try (Response response = client.newCall(httpRequest).execute()) {
            String responseText = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("file upload failed, code=" + response.code()
                        + ", url=" + httpRequest.url() + ", body=" + responseText);
            }
            return toFileResponse(responseText, fileName, normalizedRequest.getRequestId());
        }
    }

    @Override
    public FileResponse materializeLocalFile(String serviceBaseUrl, FileRequest request) throws IOException {
        // 部署拓扑由配置决定：共享卷零拷贝 register，非共享走二进制 upload。工具层不感知差异。
        return MODE_UPLOAD.equals(localFileMode)
                ? uploadLocalFile(serviceBaseUrl, request)
                : register(serviceBaseUrl, request);
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
        return readBytes(url, timeoutSeconds, DEFAULT_MAX_RESPONSE_BYTES);
    }

    @Override
    public byte[] readBytes(String url, Long timeoutSeconds, long maxBytes) throws IOException {
        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be greater than zero");
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
                String err;
                try {
                    err = body == null ? "" : new String(readBody(body, maxBytes), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IOException("HTTP download failed, code=" + response.code()
                            + ", url=" + url + ", body=" + e.getMessage(), e);
                }
                throw new IOException("HTTP download failed, code=" + response.code()
                        + ", url=" + url + ", body=" + err);
            }
            if (body == null) {
                return new byte[0];
            }
            return readBody(body, maxBytes);
        }
    }

    private byte[] readBody(ResponseBody body, long maxBytes) throws IOException {
        long contentLength = body.contentLength();
        if (contentLength > maxBytes) {
            throw new IOException("HTTP response body exceeds limit, limit=" + maxBytes);
        }
        try (InputStream input = body.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("HTTP response body exceeds limit, limit=" + maxBytes);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    /**
     * 文件服务两个接口返回字段名不同：register 用 ossUrl，upload_file_data 用 downloadUrl。
     * 这里统一收敛成 domain 的 FileResponse，避免调用方各自判断协议分支。
     */
    private FileResponse toFileResponse(String responseText, String fallbackFileName, String requestId) {
        if (StringUtils.isBlank(responseText)) {
            return null;
        }
        JSONObject json;
        try {
            json = JSON.parseObject(responseText);
        } catch (RuntimeException e) {
            return null;
        }
        if (json == null) {
            return null;
        }
        String downloadUrl = firstNonBlank(json.getString("ossUrl"), json.getString("downloadUrl"));
        FileResponse response = new FileResponse();
        response.setOssUrl(downloadUrl);
        response.setDomainUrl(firstNonBlank(json.getString("domainUrl"), json.getString("previewUrl")));
        response.setFileName(firstNonBlank(json.getString("fileName"), fallbackFileName));
        response.setRequestId(firstNonBlank(json.getString("requestId"), requestId));
        Integer fileSize = json.getInteger("fileSize");
        response.setFileSize(fileSize);
        return response;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String sanitizeUploadFileName(String fileName, Path localPath) {
        String raw = StringUtils.isNotBlank(fileName) ? fileName.trim() : localPath.getFileName().toString();
        String baseName = Path.of(raw.replace('\\', '/')).getFileName().toString();
        String cleaned = baseName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return StringUtils.isBlank(cleaned) ? "upload-file" : cleaned;
    }

    private String probeContentType(Path localPath) {
        try {
            return Files.probeContentType(localPath);
        } catch (IOException | RuntimeException e) {
            return null;
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

    /**
     * 本地文件流式请求体：multipart 上传时边读边写，避免大文件整体驻留内存。
     */
    private static final class StreamingFileRequestBody extends RequestBody {

        private final Path path;
        private final MediaType mediaType;
        private final long contentLength;

        private StreamingFileRequestBody(Path path, MediaType mediaType, long contentLength) {
            this.path = path;
            this.mediaType = mediaType;
            this.contentLength = contentLength;
        }

        @Override
        public MediaType contentType() {
            return mediaType;
        }

        @Override
        public long contentLength() {
            return contentLength >= 0 ? contentLength : -1L;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[UPLOAD_BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    sink.write(buffer, 0, read);
                }
            }
        }
    }
}
