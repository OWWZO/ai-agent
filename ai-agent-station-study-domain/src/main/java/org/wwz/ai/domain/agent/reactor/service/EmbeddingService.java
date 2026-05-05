package org.wwz.ai.domain.agent.reactor.service;


import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
public class EmbeddingService {
    @Autowired
    private DataAgentConfig dataAgentConfig;
    @Autowired
    private RemoteHttpPort remoteHttpPort;

    public String resolveEmbeddingUrl() {
        if (dataAgentConfig.getQdrantConfig() != null && StringUtils.isNotBlank(dataAgentConfig.getQdrantConfig().getEmbeddingUrl())) {
            return dataAgentConfig.getQdrantConfig().getEmbeddingUrl();
        }
        if (StringUtils.isBlank(dataAgentConfig.getAgentUrl())) {
            return null;
        }
        return StringUtils.removeEnd(dataAgentConfig.getAgentUrl(), "/") + "/v1/tool/embedding/text";
    }

    public List<List<Float>> parseEmbeddingResponse(String res) {
        if (StringUtils.isBlank(res)) {
            return null;
        }
        String normalized = res.trim();
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

    public List<List<Float>> getVectorBatch(List<String> text) {
        try {
            String embeddingUrl = resolveEmbeddingUrl();
            if (StringUtils.isBlank(embeddingUrl)) {
                throw new IllegalStateException("embeddingUrl is blank");
            }
            JSONObject body = new JSONObject();
            body.put("inputs", text);
            body.put("normalize", true);
            String res = remoteHttpPort.execute(RemoteHttpRequest.builder()
                    .method("POST")
                    .url(embeddingUrl)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(body.toJSONString())
                    .build());
            return parseEmbeddingResponse(res);
        } catch (Exception e) {
            log.error("embedding failed, error:{}", e.getMessage(), e);
            return null;
        }
    }

    public List<Float> getVector(String text) {
        List<List<Float>> vectorBatch = getVectorBatch(Collections.singletonList(text));
        if (CollectionUtils.isNotEmpty(vectorBatch)) {
            return vectorBatch.get(0);
        }
        return null;
    }

    public boolean healthCheck() {
        List<Float> vector = getVector("health_check");
        return CollectionUtils.isNotEmpty(vector);
    }
}
