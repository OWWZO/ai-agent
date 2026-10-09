package org.wwz.ai.infrastructure.dao.po.reactor.chatmodel;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 问数模型信息持久化对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_model_info")
public class ChatModelInfoPO implements Serializable {

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
