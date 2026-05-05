package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * ArmoryApi 装配请求 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArmoryApiRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * API配置ID
     */
    private String apiId;

}
