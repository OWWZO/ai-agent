package org.wwz.ai.domain.agent.genie.model.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAgentChatReq {
    private String content;
    private String traceId;
}
