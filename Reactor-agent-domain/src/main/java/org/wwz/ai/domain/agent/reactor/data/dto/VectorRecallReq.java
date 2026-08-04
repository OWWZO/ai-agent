package org.wwz.ai.domain.agent.reactor.data.dto;


import lombok.Data;

import java.util.List;
import java.util.Map;


/**
 * 通用向量召回请求模型，承载查询文本、过滤条件和召回数量。
 */
@Data
public class VectorRecallReq {
    private String query;
    private String collectionName;
    private Integer limit = 100;
    private Float scoreThreshold = 0.5f;
    private Long timeout = 50000L;
    private Map<String, Object> keywordFilterMap;
    private List<String> payloads;
    private List<String> vectorIdList;
    private String requestId;
    private String vectorType;
}
