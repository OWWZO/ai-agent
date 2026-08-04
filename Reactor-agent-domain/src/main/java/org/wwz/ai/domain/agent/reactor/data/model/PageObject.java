package org.wwz.ai.domain.agent.reactor.data.model;

import lombok.Data;

import java.util.List;

@Data
/**
 * 延期保留的分页结果容器。
 */
public class PageObject<T extends Object> {
    List<T> dataList;
    int totalCount;
}
