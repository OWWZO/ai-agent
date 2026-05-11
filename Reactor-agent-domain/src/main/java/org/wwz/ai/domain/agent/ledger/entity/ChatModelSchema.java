package org.wwz.ai.domain.agent.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

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
