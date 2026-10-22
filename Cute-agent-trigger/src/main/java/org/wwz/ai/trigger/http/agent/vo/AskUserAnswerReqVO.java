package org.wwz.ai.trigger.http.agent.vo;

import lombok.Data;

import java.util.Map;

/**
 * 用户提交 AskUserQuestion 答案。
 */
@Data
public class AskUserAnswerReqVO {

    /** 必填：问题实例 ID（SSE 卡片里的 questionId） */
    private String questionId;

    /**
     * 必填：question 全文 → 答案文本。
     * 多选时用逗号拼接 label。
     */
    private Map<String, String> answers;
}
