package org.wwz.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentSessionCapability {
    private Long id;
    private String sessionId;
    private String kind;
    private String refId;
    private Integer enabled;
    private LocalDateTime updateTime;
}
