package org.wwz.ai.domain.agent.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 问数场景的聊天模型元数据实体。
 * <p>该对象描述模型名称、业务提示词和用途，供 RAG/问数装配链读取。</p>
 */
@Data
@TableName("chat_model_info")
public class ChatModelInfo implements Serializable {
    private static final long serialVersionUID = 8763697882256572393L;
    private Long id;
    private String code;
    private String type;
    private String content;
    private String name;
    private String usePrompt;
    private String businessPrompt;
    @TableLogic(value = "1", delval = "0")
    private Integer yn;
}
