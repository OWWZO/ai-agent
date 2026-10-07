package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 技能包与 MCP 连接器的安全目录。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CatalogCapabilitiesResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<CatalogSkillResponseDTO> skills;

    private List<CatalogMcpResponseDTO> mcps;
}
