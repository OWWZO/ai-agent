package org.wwz.ai.domain.agent.memory.ltm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结构化记忆抽取操作（flush / background review 共用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LtmExtractionOp {
    /** add | replace | remove */
    private String action;
    /** user | curated */
    private String target;
    private String content;
    private String oldText;
}
