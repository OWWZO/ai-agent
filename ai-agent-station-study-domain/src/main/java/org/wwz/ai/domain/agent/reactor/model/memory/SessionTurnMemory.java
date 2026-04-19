package org.wwz.ai.domain.agent.reactor.model.memory;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 最近窗口中的单轮记忆
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionTurnMemory {

    private Long messageId;
    private String requestId;
    private Integer sortOrder;
    private String userMessage;
    private String assistantMessage;

    @Builder.Default
    private List<JSONObject> artifactRefs = new ArrayList<>();
}
