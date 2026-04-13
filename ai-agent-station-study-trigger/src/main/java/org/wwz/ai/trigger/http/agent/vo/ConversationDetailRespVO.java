package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConversationDetailRespVO {
    private ConversationListRespVO conversation;
    private List<ConversationTurnRespVO> turns;
}
