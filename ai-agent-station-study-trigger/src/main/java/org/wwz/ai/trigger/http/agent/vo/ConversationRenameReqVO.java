package org.wwz.ai.trigger.http.agent.vo;

import lombok.Data;

@Data
public class ConversationRenameReqVO {
    private String sessionId;
    private String title;
}
