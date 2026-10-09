package org.wwz.ai.infrastructure.dataquery.embedding;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingRequest;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;

import java.util.List;
import java.util.Map;

/**
 * 共享文本 embedding HTTP 代理适配器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpTextEmbeddingAdapter implements TextEmbeddingPort {

    private final DataQueryEmbeddingProperties properties;
    private final RemoteHttpPort remoteHttpPort;

    @Override
    public TextEmbeddingResult embed(TextEmbeddingRequest request) {
        try {
            String embeddingUrl = resolveEmbeddingUrl();
            if (StringUtils.isBlank(embeddingUrl) || request == null || request.getTexts() == null) {
                return TextEmbeddingResult.unavailable();
            }
            JSONObject body = new JSONObject();
            body.put("inputs", request.getTexts());
            body.put("normalize", request.isNormalize());
            String response = remoteHttpPort.execute(RemoteHttpRequest.builder()
                    .method("POST")
                    .url(embeddingUrl)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(body.toJSONString())
                    .build());
            List<List<Float>> vectors = parseEmbeddingResponse(response);
            return vectors == null ? TextEmbeddingResult.unavailable() : TextEmbeddingResult.available(vectors);
        } catch (Exception e) {
            log.warn("embedding proxy unavailable: {}", e.getMessage(), e);
            return TextEmbeddingResult.unavailable();
        }
    }

    private String resolveEmbeddingUrl() {
        if (properties.getQdrantConfig() != null
                && StringUtils.isNotBlank(properties.getQdrantConfig().getEmbeddingUrl())) {
            return properties.getQdrantConfig().getEmbeddingUrl();
        }
        if (StringUtils.isBlank(properties.getAgentUrl())) {
            return null;
        }
        return StringUtils.removeEnd(properties.getAgentUrl(), "/") + "/v1/tool/embedding/text";
    }

    private List<List<Float>> parseEmbeddingResponse(String response) {
        if (StringUtils.isBlank(response)) {
            return null;
        }
        String normalized = response.trim();
        if (normalized.startsWith("[")) {
            return JSONObject.parseObject(normalized, new TypeReference<>() {
            });
        }
        Map<String, Object> body = JSONObject.parseObject(normalized, new TypeReference<>() {
        });
        Object vectors = body.get("vectors");
        if (vectors == null) {
            return null;
        }
        return JSONObject.parseObject(JSONObject.toJSONString(vectors), new TypeReference<>() {
        });
    }
}
