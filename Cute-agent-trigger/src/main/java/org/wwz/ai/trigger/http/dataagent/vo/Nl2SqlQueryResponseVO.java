package org.wwz.ai.trigger.http.dataagent.vo;

import lombok.Data;

import java.util.List;

@Data
public class Nl2SqlQueryResponseVO {
    private String requestId;
    private String query;
    private List<String> modelCodeList;
    private List<DataQueryModelResponseVO> schemaInfo;
    private String currentDateInfo;
    private String traceId;
    private String recallType;
    private Boolean stream;
    private String userInfo;
    private String dbType;
    private boolean useVector;
    private boolean useElastic;
}
