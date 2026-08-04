package org.wwz.ai.domain.agent.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 问数模型字段元数据实体。
 * <p>字段注释、同义词、类型和召回开关共同构成 schema RAG 的可检索知识。</p>
 */
@Data
@TableName("chat_model_schema")
public class ChatModelSchema implements Serializable {
    private static final long serialVersionUID = -6284827149526794290L;
    private Long id;
    private String modelCode;
    private String columnId;
    private String columnName;
    private String columnComment;
    private String fewShot;
    private String dataType;
    private String synonyms;
    private String vectorUuid;
    private int defaultRecall;
    private int analyzeSuggest;
    @TableLogic(value = "1", delval = "0")
    private Integer yn;

}
