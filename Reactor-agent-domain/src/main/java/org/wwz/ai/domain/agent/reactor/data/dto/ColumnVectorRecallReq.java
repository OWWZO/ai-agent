package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

import java.util.List;

@Data
public class ColumnVectorRecallReq {
    private String query;
    private Integer limit = 100;
    private Float scoreThreshold = 0.5f;
    private Long timeout = 50000L;
    private List<String> modelCodeList;
}
