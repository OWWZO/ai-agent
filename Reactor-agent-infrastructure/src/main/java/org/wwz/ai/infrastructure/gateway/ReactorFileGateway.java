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

        String baseUrl = reactorConfig.getCodeInterpreterUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("autobots.autoagent.code_interpreter_url 未配置");
        }

        String originalFileName = StringUtils.hasText(file.getOriginalFilename())
                ? Objects.requireNonNull(file.getOriginalFilename()).trim()
                : "uploaded-file";
        String uploadUrl = trimTrailingSlash(baseUrl) + "/v1/file_tool/upload_file_data";

        try {
            MediaType parsedMediaType = StringUtils.hasText(file.getContentType())
                    ? MediaType.parse(file.getContentType())
                    : DEFAULT_MEDIA_TYPE;
            MediaType mediaType = parsedMediaType == null ? DEFAULT_MEDIA_TYPE : parsedMediaType;
            StreamingMultipartFileRequestBody fileBody = new StreamingMultipartFileRequestBody(file, mediaType);

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
                    log.error("对话附件上传失败 sessionId={}, fileName={}, code={}, body={}",
                            sessionId, originalFileName, response.code(), responseText);
                    throw new IllegalStateException(resolveUploadFailureMessage(response.code()));
                }
                if (!StringUtils.hasText(responseText)) {
                    throw new IllegalStateException("文件服务返回为空");
                }

                JSONObject result = JSON.parseObject(responseText);
                String previewUrl = firstText(result.getString("domainUrl"), result.getString("downloadUrl"));
                String downloadUrl = firstText(result.getString("downloadUrl"), result.getString("domainUrl"));
                String resourceKey = buildStableResourceKey(sessionId, originalFileName, file.getSize(), fileBody.getSha256Hex());

                return ConversationUploadFileDTO.builder()
                        .name(originalFileName)
                        .url(previewUrl)
                        .type(resolveFileExtension(originalFileName))
                        .size(result.getLong("fileSize"))
                        .downloadUrl(downloadUrl)
                        .previewUrl(previewUrl)
                        .resourceKey(resourceKey)
                        .mimeType(file.getContentType())
                        .originFileName(originalFileName)
                        .build();
            }
        } catch (IOException e) {
            log.error("对话附件上传异常 sessionId={}, fileName={}", sessionId, originalFileName, e);
            throw new IllegalStateException("文件服务调用失败", e);
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
