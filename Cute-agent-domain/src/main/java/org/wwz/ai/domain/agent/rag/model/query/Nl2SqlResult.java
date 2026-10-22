package org.wwz.ai.domain.agent.rag.model.query;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Generated SQL alternatives and progress information returned by NL2SQL. */
@Data
public class Nl2SqlResult {
    private Integer code;
    @JSONField(name = "request_id")
    private String requestId;
    @JSONField(name = "nl2sql_think")
    private String nl2sqlThink;
    private String status;
    private List<GeneratedQuery> data;
    @JSONField(name = "err_msg")
    private String errorMessage;
    private String rootQuery;
    private String erp;
    private String overwriteError;
    @JSONField(name = "cost_time")
    private Map<String, Object> costTime;

    @Data
    public static class GeneratedQuery {
        private String query;
        private String nl2sql;
    }
}
