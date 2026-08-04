package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 问数查询配置传输对象，承载模型、数据源和查询限制。
 */
@Data
public class ChatQueryConfig {
    private String projectCode;
    private String modelCode;
    private List<ChatQueryColumn> cols;
    private List<ChatQueryFilter> filters;
    private Map<String, Object> variableMap;
    private int limit;
    private boolean groupWithoutAgg = true;
    private String question;
}
