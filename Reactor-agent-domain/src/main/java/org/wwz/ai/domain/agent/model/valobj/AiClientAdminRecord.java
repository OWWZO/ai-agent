package org.wwz.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 客户端配置管理记录（Admin 管理面领域对象）。
 * 命名刻意避开 infrastructure.dao.po.AiClientConfig。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClientAdminRecord {
    private Long id;
    private String clientId;
    private String clientName;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
