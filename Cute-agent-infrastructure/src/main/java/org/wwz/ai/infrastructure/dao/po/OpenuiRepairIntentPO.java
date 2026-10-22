package org.wwz.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * openui 修复编辑意图表（plans/038 §10.4，重放用）。
 * @description ai_agent_openui_repair_intent PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpenuiRepairIntentPO {

    /** 自增主键ID */
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 消息身份（沿用会话的 requestId） */
    private String messageId;

    /** 原文规范化后的 SHA-256；重放时校验原文是否未变 */
    private String programHash;

    /** 同一 key 内的应用顺序（0..n），多个块之间有顺序依赖 */
    private Integer intentIndex;

    /** SEARCH 原文片段（须在原文中唯一） */
    private String searchText;

    /** REPLACE 替换文本；空串表示删除 */
    private String replaceText;

    /** 产出意图的模型名，便于按批次清理 */
    private String model;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
