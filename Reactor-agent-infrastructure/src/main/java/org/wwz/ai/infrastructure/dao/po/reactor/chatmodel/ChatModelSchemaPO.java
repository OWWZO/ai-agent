package org.wwz.ai.infrastructure.dao.po.reactor.chatmodel;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 问数模型字段持久化对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_model_schema")
public class ChatModelSchemaPO implements Serializable {

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
    private Integer defaultRecall;
    private Integer analyzeSuggest;

    @TableLogic(value = "1", delval = "0")
    private Integer yn;
}
