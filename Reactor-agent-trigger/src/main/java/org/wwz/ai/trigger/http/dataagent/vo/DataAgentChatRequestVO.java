package org.wwz.ai.trigger.http.dataagent.vo;

import lombok.Data;

@Data
public class DataAgentChatRequestVO {
    private String content;
    private String traceId;
}
