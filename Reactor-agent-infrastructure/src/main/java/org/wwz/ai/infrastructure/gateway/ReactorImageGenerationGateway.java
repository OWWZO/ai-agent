package org.wwz.ai.infrastructure.gateway;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.gateway.IReactorImageGenerationGateway;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayRequest;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayResponse;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reactor Tool 图片生成网关。
 */
@Slf4j
@Component
public class ReactorImageGenerationGateway implements IReactorImageGenerationGateway {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final long DEFAULT_TIMEOUT_SECONDS = 1080L;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();

    @Resource
    private ReactorConfig reactorConfig;

    /**
     * 调用 Python 生图服务，返回统一归一化后的结果。
     */
    @Override
    public ImageGenerationGatewayResponse generate(ImageGenerationGatewayRequest requestDTO) {
        String baseUrl = reactorConfig.getImageGenerationUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("autobots.autoagent.image_generation_url 未配置");
        }

        String url = trimTrailingSlash(baseUrl) + "/v1/tool/image_generation";
        RequestBody requestBody = RequestBody.create(
                JSON_MEDIA_TYPE,
                JSON.toJSONString(requestDTO)
        );

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseText = response.body() == null ? null : response.body().string();
            if (!response.isSuccessful()) {
                log.error("调用 Python 生图服务失败 requestId={}, code={}, body={}",
                        requestDTO.getRequestId(), response.code(), responseText);
                throw new IllegalStateException(resolveFailureMessage(responseText));
            }
            if (!StringUtils.hasText(responseText)) {
                throw new IllegalStateException("Python 生图服务返回为空");
            }

            ImageGenerationGatewayResponse responseDTO =
                    JSON.parseObject(responseText, ImageGenerationGatewayResponse.class);
            if (responseDTO == null) {
                throw new IllegalStateException("Python 生图服务返回格式非法");
            }

            responseDTO.setRequestId(StringUtil.firstNonBlank(responseDTO.getRequestId(), requestDTO.getRequestId()));
            responseDTO.setRawResponse(JSON.parse(responseText));
            responseDTO.setFileInfo(normalizeFileInfo(responseDTO.getFileInfo()));
            return responseDTO;
        } catch (IOException e) {
            log.error("调用 Python 生图服务异常 requestId={}", requestDTO.getRequestId(), e);
            throw new IllegalStateException("调用 Python 生图服务失败", e);
        }
    }

    private List<ImageGenerationGatewayFile> normalizeFileInfo(List<ImageGenerationGatewayFile> fileInfoList) {
        if (fileInfoList == null || fileInfoList.isEmpty()) {
            return Collections.emptyList();
        }

        for (ImageGenerationGatewayFile fileInfo : fileInfoList) {
            if (fileInfo == null) {
                continue;
            }
            // 按优先级取第一个非空值作为下载链接：downloadUrl > ossUrl > domainUrl
            String downloadUrl = StringUtil.firstNonBlank(
                    fileInfo.getDownloadUrl(),
                    fileInfo.getOssUrl(),
                    fileInfo.getDomainUrl()
            );
            String previewUrl = StringUtil.firstNonBlank(
                    fileInfo.getPreviewUrl(),
                    fileInfo.getDomainUrl(),
                    downloadUrl,
                    fileInfo.getOssUrl()
            );
            fileInfo.setDownloadUrl(downloadUrl);
            fileInfo.setPreviewUrl(previewUrl);
            if (!StringUtils.hasText(fileInfo.getOssUrl())) {
                fileInfo.setOssUrl(downloadUrl);
            }
            if (!StringUtils.hasText(fileInfo.getDomainUrl())) {
                fileInfo.setDomainUrl(previewUrl);
            }
        }
        return fileInfoList;
    }

    private String resolveFailureMessage(String responseText) {
        if (!StringUtils.hasText(responseText)) {
            return "Python 生图服务调用失败";
        }

        try {
            com.alibaba.fastjson.JSONObject jsonObject = JSON.parseObject(responseText);
            String message = StringUtil.firstNonBlank(
                    jsonObject.getString("message"),
                    jsonObject.getString("detail"),
                    jsonObject.getString("data")
            );
            if (StringUtils.hasText(message)) {
                return message;
            }
        } catch (Exception ignored) {
            // 响应不是 JSON 时退回原始文本。
        }
        return responseText;
    }

    private String trimTrailingSlash(String value) {
        String target = value == null ? "" : value.trim();
        while (target.endsWith("/")) {
            target = target.substring(0, target.length() - 1);
        }
        return target;
    }
}
