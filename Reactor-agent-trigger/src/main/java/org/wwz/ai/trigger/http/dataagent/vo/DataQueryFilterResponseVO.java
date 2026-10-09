package org.wwz.ai.trigger.http.dataagent.vo;

import lombok.Data;

import java.util.List;

@Data
public class DataQueryFilterResponseVO {
    private String col;
    private String opt;
    private String val;
    private String optName;
    private String name;
    private String dataType;
    private String operator;
    private List<DataQueryFilterResponseVO> subFilters;
}
