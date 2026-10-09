package org.wwz.ai.trigger.http.dataagent.vo;

import lombok.Data;

import java.util.List;

@Data
public class ColumnValueRecallRequestVO {
    private String query;
    private List<String> modelCodeList;
    private int limit = 100;
}
