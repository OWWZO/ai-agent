package org.wwz.ai.domain.agent.reactor.model.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话记忆中的结构化事实
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMemoryFact {

    /**
     * 事实类别，例如 goal/constraint/conclusion/pending
     */
    private String category;

    /**
     * 事实内容
     */
    private String content;
}
