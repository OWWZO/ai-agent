package org.wwz.ai.domain.agent.reactor.data.model;

import lombok.Data;

import java.util.List;

@Data
public class PageObject<T extends Object> {
    List<T> dataList;
    int totalCount;
}
