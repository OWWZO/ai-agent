package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class NL2SQLResult {
    private Integer code;
    private String request_id;
    private String nl2sql_think;
    private String status;
    private List<NL2SQLData> data;
    private String err_msg;
    private String rootQuery;
    private String erp;
    private String overwriteError;
    private Map<String,Object> cost_time;

    @Data
    public static class NL2SQLData {
        private String query;
        private String nl2sql;
    }
}
