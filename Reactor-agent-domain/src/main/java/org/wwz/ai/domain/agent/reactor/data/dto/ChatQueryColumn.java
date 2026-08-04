package org.wwz.ai.domain.agent.reactor.data.dto;

import lombok.Data;

/**
 * 问数查询列传输对象，描述列名、类型和展示信息。
 */
@Data
public class ChatQueryColumn {
    private String col;
    private String agg;
    private String order;

    private String guid;
    private String name;
    private String dataType;
    private String colType;
}
