package org.wwz.ai.infrastructure.gateway;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.infrastructure.gateway.dto.ConversationUploadFileDTO;
import okio.BufferedSink;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * Reactor Tool 文件服务网关。
 */
@Slf4j
@Component
public class ReactorFileGateway {

    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.parse("application/octet-stream");
    private static final int STREAM_BUFFER_SIZE = 8 * 1024;

    private final OkHttpClient uploadClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build();

    @Resource
    private ReactorConfig reactorConfig;

    /**
     * 把前端上传的二进制附件转发到 reactor-tool，获取稳定访问地址。
     */
    public ConversationUploadFileDTO uploadConversationFile(String sessionId, MultipartFile file) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFileName = StringUtils.hasText(file.getOriginalFilename())
                ? Objects.requireNonNull(file.getOriginalFilename()).trim()
                : "uploaded-file";
        try {
            return uploadConversationFile(
                    sessionId,
                    originalFileName,
                    file.getContentType(),
                    file.getSize(),
                    file.getInputStream()
            );
        } catch (IOException e) {
            log.error("对话附件上传异常 sessionId={}, fileName={}", sessionId, originalFileName, e);
            throw new IllegalStateException("文件服务调用失败", e);
        }
    }

    /**
     * 协议无关的会话附件上传入口（供 ConversationFilePort 适配）。
     */
    public ConversationUploadFileDTO uploadConversationFile(String sessionId,
                                                            String originalFileName,
                                                            String contentType,
                                                            long size,
                                                            InputStream content) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (content == null) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (!StringUtils.hasText(reactorConfig.getCodeInterpreterUrl())) {
            throw new IllegalStateException("autobots.autoagent.code_interpreter_url 未配置");
        }

        String resolvedName = StringUtils.hasText(originalFileName) ? originalFileName.trim() : "uploaded-file";
        try {
            MediaType parsedMediaType = StringUtils.hasText(contentType)
                    ? MediaType.parse(contentType)
                    : DEFAULT_MEDIA_TYPE;
            MediaType mediaType = parsedMediaType == null ? DEFAULT_MEDIA_TYPE : parsedMediaType;
            StreamingInputStreamRequestBody fileBody = new StreamingInputStreamRequestBody(content, mediaType, size);
            return uploadBinary(sessionId, resolvedName, mediaType, fileBody, size, contentType);
        } catch (IOException e) {
            log.error("对话附件上传异常 sessionId={}, fileName={}", sessionId, resolvedName, e);
            throw new IllegalStateException("文件服务调用失败", e);
        }
    }

    /**
     * 上传内存中的二进制产物（如 Java 端直接生成的图片）到 reactor-tool 文件服务。
     */
    public ConversationUploadFileDTO uploadBinaryFile(String sessionId,
                                                      String fileName,
                                                      byte[] content,
                                                      String contentType) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String originalFileName = StringUtils.hasText(fileName) ? fileName.trim() : "uploaded-file";
        MediaType parsedMediaType = StringUtils.hasText(contentType)
                ? MediaType.parse(contentType)
                : DEFAULT_MEDIA_TYPE;
        MediaType mediaType = parsedMediaType == null ? DEFAULT_MEDIA_TYPE : parsedMediaType;
        try {
            return uploadBinary(
                    sessionId,
                    originalFileName,
                    mediaType,
                    RequestBody.create(mediaType, content),
                    content.length,
                    contentType
            );
        } catch (IOException e) {
            log.error("二进制产物上传异常 sessionId={}, fileName={}", sessionId, originalFileName, e);
            throw new IllegalStateException("文件服务调用失败", e);
        }
    }

    private ConversationUploadFileDTO uploadBinary(String sessionId,
                                                   String originalFileName,
                                                   MediaType mediaType,
                                                   RequestBody fileBody,
                                                   long fileSize,
                                                   String contentType) throws IOException {
        String baseUrl = reactorConfig.getCodeInterpreterUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("autobots.autoagent.code_interpreter_url 未配置");
        }
        String uploadUrl = trimTrailingSlash(baseUrl) + "/v1/file_tool/upload_file_data";
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("requestId", sessionId)
                .addFormDataPart("file", originalFileName, fileBody)
                .build();
        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build();
        try (Response response = uploadClient.newCall(request).execute()) {
            String responseText = response.body() == null ? null : response.body().string();
            if (!response.isSuccessful()) {
                log.error("文件上传失败 sessionId={}, fileName={}, code={}, body={}",
                        sessionId, originalFileName, response.code(), responseText);
                throw new IllegalStateException(resolveUploadFailureMessage(response.code()));
            }
            if (!StringUtils.hasText(responseText)) {
                throw new IllegalStateException("文件服务返回为空");
            }
            JSONObject result = JSON.parseObject(responseText);
            String previewUrl = firstText(result.getString("domainUrl"), result.getString("downloadUrl"));
            String downloadUrl = firstText(result.getString("downloadUrl"), result.getString("domainUrl"));
            String sha256Hex = null;
            if (fileBody instanceof StreamingMultipartFileRequestBody streamingBody) {
                sha256Hex = streamingBody.getSha256Hex();
            } else if (fileBody instanceof StreamingInputStreamRequestBody streamingBody) {
                sha256Hex = streamingBody.getSha256Hex();
            }
            String resourceKey = buildStableResourceKey(sessionId, originalFileName, fileSize, sha256Hex);
            Long responseSize = result.getLong("fileSize");
            return ConversationUploadFileDTO.builder()
                    .name(originalFileName)
                    .url(previewUrl)
                    .type(resolveFileExtension(originalFileName))
                    .size(responseSize == null ? fileSize : responseSize)
                    .downloadUrl(downloadUrl)
                    .previewUrl(previewUrl)
                    .resourceKey(resourceKey)
                    .mimeType(contentType)
                    .originFileName(originalFileName)
                    .build();
        }
    }

    private String trimTrailingSlash(String value) {
        String target = value == null ? "" : value.trim();
        while (target.endsWith("/")) {
            target = target.substring(0, target.length() - 1);
        }
        return target;
    }

    /**
     * resourceKey 用于会话内文件去重，不能依赖可能变化的外部 URL。
     */
    private String buildStableResourceKey(String sessionId, String originalFileName, long fileSize, String sha256Hex) {
        String stableSuffix = StringUtils.hasText(sha256Hex) ? sha256Hex : String.valueOf(fileSize);
        return sessionId + ":" + originalFileName + ":" + stableSuffix;
    }

    private String resolveUploadFailureMessage(int statusCode) {
        if (statusCode == 404 || statusCode == 405) {
            return "文件服务未开启 multipart 上传接口 /v1/file_tool/upload_file_data";
        }
        return "文件服务上传失败";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String resolveFileExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 通过流式方式把 InputStream 写入下游。
     */
    private static final class StreamingInputStreamRequestBody extends RequestBody {

        private final InputStream inputStream;
        private final MediaType mediaType;
        private final long contentLength;
        private volatile String sha256Hex;

        private StreamingInputStreamRequestBody(InputStream inputStream, MediaType mediaType, long contentLength) {
            this.inputStream = inputStream;
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
            MessageDigest messageDigest = newSha256Digest();
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int readLength;
            while ((readLength = inputStream.read(buffer)) != -1) {
                messageDigest.update(buffer, 0, readLength);
                sink.write(buffer, 0, readLength);
            }
            this.sha256Hex = toHex(messageDigest.digest());
        }

        private String getSha256Hex() {
            return sha256Hex;
        }

        private MessageDigest newSha256Digest() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("JVM 不支持 SHA-256", e);
            }
        }

        private String toHex(byte[] value) {
            StringBuilder builder = new StringBuilder(value.length * 2);
            for (byte current : value) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        }
    }

    /**
     * 通过流式方式把附件写入下游，避免 file.getBytes() 把大文件一次性读入堆内存。
     */
    private static final class StreamingMultipartFileRequestBody extends RequestBody {

        private final MultipartFile file;
        private final MediaType mediaType;
        private volatile String sha256Hex;

        private StreamingMultipartFileRequestBody(MultipartFile file, MediaType mediaType) {
            this.file = file;
            this.mediaType = mediaType;
        }

        @Override
        public MediaType contentType() {
            return mediaType;
        }

        @Override
        public long contentLength() {
            return file.getSize();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            MessageDigest messageDigest = newSha256Digest();
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            try (InputStream inputStream = file.getInputStream()) {
                int readLength;
                while ((readLength = inputStream.read(buffer)) != -1) {
                    messageDigest.update(buffer, 0, readLength);
                    sink.write(buffer, 0, readLength);
                }
            }
            this.sha256Hex = toHex(messageDigest.digest());
        }

        private String getSha256Hex() {
            return sha256Hex;
        }

        private MessageDigest newSha256Digest() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("JVM 不支持 SHA-256", e);
            }
        }

        private String toHex(byte[] value) {
            StringBuilder builder = new StringBuilder(value.length * 2);
            for (byte current : value) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        }
    }
}
