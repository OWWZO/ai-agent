package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

import java.util.List;

@Data
public class TableRagResult {
    private Integer code;
    private List<TableRagData> data;
    private String request_id;


    @Data
    public static class TableRagData {
        private String modelCode;
        private List<ChatSchemaDto> schemaList;
        private Float score;
    }
}
