package org.wwz.ai.trigger.http.dataagent.vo;

import lombok.Data;

@Data
public class DataQueryColumnResponseVO {
    private String col;
    private String agg;
    private String order;
    private String guid;
    private String name;
    private String dataType;
    private String colType;
}
