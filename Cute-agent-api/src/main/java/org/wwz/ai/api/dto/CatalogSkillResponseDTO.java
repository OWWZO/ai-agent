package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录用户可见的技能安全元数据。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CatalogSkillResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;

    private String description;

    private String sourceSummary;

    /** 1 表示当前已注册并可用。 */
    private Integer status;
}
