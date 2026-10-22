package org.wwz.ai.infrastructure.dao.po.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Catalog 上下文 API 配置持久化对象。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClientApiPO implements Serializable {
    private Long id;
    private String apiId;
    private String baseUrl;
    private String apiKey;
    private String completionsPath;
    private String embeddingsPath;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
