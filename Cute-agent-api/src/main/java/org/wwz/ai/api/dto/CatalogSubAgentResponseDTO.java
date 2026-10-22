package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 登录用户可见的子 Agent 安全元数据。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CatalogSubAgentResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String agentKey;

    private String displayName;

    private String whenToUse;

    /** 工具权限摘要，不包含内部提示词。 */
    private List<String> toolSummary;

    private String toolPolicyMode;

    private Integer maxSteps;

    private Integer status;
}
