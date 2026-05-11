package org.wwz.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库订单
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiRagOrderVO {

    /**
     * 知识库名称
     */
    private String ragName;

    /**
     * 知识标签
     */
    private String knowledgeTag;

}
