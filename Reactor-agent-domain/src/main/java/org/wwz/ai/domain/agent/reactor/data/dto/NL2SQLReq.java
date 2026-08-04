package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

import java.util.List;

/**
 * 自然语言转 SQL 请求模型，承载查询文本、模型 schema 和召回开关。
 */
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
