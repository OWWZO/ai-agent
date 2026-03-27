package org.wwz.ai.domain.agent.reactor.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileRequest {
    private String requestId;
    private String fileName;
    private String description;
    private String content;
}
