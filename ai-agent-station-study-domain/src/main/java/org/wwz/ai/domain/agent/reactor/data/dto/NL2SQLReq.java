package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

import java.util.List;

@Data
public class NL2SQLReq {
    private String requestId;
    private String query;
    private List<String> modelCodeList;
    private List<ChatModelInfoDto> schemaInfo;
    private String currentDateInfo = "当前时间信息：%s,%s";
    private String traceId;
    private String recallType = "only_recall";
    private Boolean stream = true;
    private String userInfo = "";
    private String dbType;
    private boolean useVector;
    private boolean useElastic;
}
