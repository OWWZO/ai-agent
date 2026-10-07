package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 登录用户创建子 Agent 时使用的请求 DTO。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CatalogSubAgentCreateRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String agentKey;
    private String displayName;
    private String whenToUse;
    private String systemPrompt;
    private List<String> allowedTools;
    private List<String> disallowedTools;
    private String toolPolicyMode;
    private List<String> deferredTools;
    private Integer maxSteps;
    private Integer status;
}
