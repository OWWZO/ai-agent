package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

import java.util.List;

/**
 * Elasticsearch 列值召回请求模型。
 */
@Data
public class ColumnEsRecallReq {
    private String query;
    private List<String> modelCodeList;
    private int limit = 100;
}
