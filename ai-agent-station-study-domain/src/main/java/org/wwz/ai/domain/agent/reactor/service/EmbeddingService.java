package org.wwz.ai.domain.agent.reactor.service;


import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import org.wwz.ai.domain.agent.reactor.agent.util.OkHttpUtil;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;


@Slf4j
@Service
public class EmbeddingService {
    @Autowired
    private DataAgentConfig dataAgentConfig;

    public List<List<Float>> getVectorBatch(List<String> text) {
        try {
            JSONObject body = new JSONObject();
            body.put("inputs", text);
            body.put("normalize", true);
            String res = OkHttpUtil.postJsonBody(dataAgentConfig.getQdrantConfig().getEmbeddingUrl(), null, body.toJSONString());
            return JSONObject.parseObject(res, new TypeReference<>() {
            });
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
}
