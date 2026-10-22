package org.wwz.ai.domain.agent.catalog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 技能与 MCP 能力目录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogCapabilities {

    private List<CatalogSkill> skills;
    private List<CatalogMcp> mcps;
}
