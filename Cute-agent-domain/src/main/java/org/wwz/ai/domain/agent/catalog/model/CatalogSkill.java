package org.wwz.ai.domain.agent.catalog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 技能目录项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogSkill {

    private String name;
    private String description;
    private String sourceSummary;
    private Integer status;
}
