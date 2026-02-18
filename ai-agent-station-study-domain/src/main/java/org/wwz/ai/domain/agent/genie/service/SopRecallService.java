package org.wwz.ai.domain.agent.genie.service;


import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.genie.agent.dto.SopRecallRequest;
import org.wwz.ai.domain.agent.genie.agent.dto.SopRecallResponse;
import org.wwz.ai.domain.agent.genie.config.GenieConfig;
import org.wwz.ai.domain.agent.genie.util.HttpUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * SOP召回服务
 */
@Slf4j
@Service
public class SopRecallService {

    @Autowired
    private GenieConfig genieConfig;

    /**
     * 调用SOP召回服务
     * 
     * @param requestId 请求ID
     * @param query 查询内容
     * @return SOP召回结果
     */
    public SopRecallResponse sopRecall(String requestId, String query) {
        try {
            String SOP_RECALL_URL = genieConfig.getAutoBotsKnowledgeUrl() + "/v1/tool/sopRecall";

            // 构建请求参数
            SopRecallRequest request = SopRecallRequest.builder()
                    .requestId(requestId)
                    .query(query)
                    .build();
            
            // 设置请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            
            // 发送HTTP请求
            String requestBody = JSON.toJSONString(request);
            log.info("{} 发送SOP召回请求：url={}, body={}", requestId, SOP_RECALL_URL, requestBody);
            
            String responseBody = HttpUtils.postReq(SOP_RECALL_URL, headers, requestBody, 3000);
            
            if (StringUtils.isBlank(responseBody)) {
                log.error("{} SOP召回服务返回空响应", requestId);
                return null;
            }
            
            log.info("{} SOP召回服务响应：{}", requestId, responseBody);
            
            // 解析响应
            SopRecallResponse response = JSON.parseObject(responseBody, SopRecallResponse.class);
            
            if (response == null || response.getCode() == null || !response.getCode().equals(200)) {
                log.error("{} SOP召回服务调用失败，响应码：{}", requestId, response != null ? response.getCode() : "null");
                return null;
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("{} 调用SOP召回服务异常：", requestId, e);
            return null;
        }
    }
    
    /**
     * 检查SOP召回结果是否有效
     * 
     * @param response SOP召回响应
     * @return 是否有效
     */
    public boolean isValidSopResult(SopRecallResponse response) {
        return response != null 
                && response.getData() != null
                && StringUtils.isNotBlank(response.getData().getChoosed_sop_string());
    }
}
