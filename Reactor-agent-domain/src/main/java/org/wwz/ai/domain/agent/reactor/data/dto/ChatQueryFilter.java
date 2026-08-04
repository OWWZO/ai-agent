package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

import java.util.List;

/**
 * 问数查询过滤条件传输对象。
 */
@Data
public class ChatQueryFilter {
    private String col;
    private String opt;
    private String val;
    private String optName;
    private String name;
    private String dataType;
    //and or
    private String operator;
    private List<ChatQueryFilter> subFilters;
}
