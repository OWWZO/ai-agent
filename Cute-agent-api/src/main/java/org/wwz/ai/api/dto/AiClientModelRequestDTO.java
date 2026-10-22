package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI客户端模型配置请求 DTO
 * @description AI客户端模型配置请求数据传输对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientModelRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 自增主键ID（更新时使用）
     */
    private Long id;

    /**
     * 模型引用标识；允许多条配置共用，更新时使用 id 区分具体配置行
     */
    private String modelId;

    /**
     * 关联的API配置ID
     */
    private String apiId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型类型：openai、deepseek、claude
     */
    private String modelType;

    /**
     * 模型用途
     */
    private String modelUsage;

    /** 是否支持深度思考：0/1 */
    private Integer supportsThinking;

    /** 上下文窗口 token */
    private Integer contextWindow;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

}
