package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据分析请求类
 * 用于员工数据分析，包括人员变化趋势分析和预测
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAnalysisRequest {
    
    /**
     * 请求ID，用于追踪请求
     */
    private String request_id;
    
    /**
     * 用户ERP账号
     */
    private String erp;
    
    /**
     * 分析任务描述
     */
    private String task;
    
    /**
     * 使用的模型代码列表
     */
    private List<String> modelCodeList;
    
    /**
     * 业务知识和规则
     */
    private String businessKnowledge;
    
    /**
     * 最大执行步数，默认20
     */
    @Builder.Default
    private Integer max_steps = 20;
    
    /**
     * 是否流式返回结果，默认true
     */
    @Builder.Default
    private Boolean stream = true;
}
