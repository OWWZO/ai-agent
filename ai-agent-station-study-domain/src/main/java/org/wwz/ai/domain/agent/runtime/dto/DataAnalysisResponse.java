package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自动分析响应类
 * 用于处理自动分析API的流式返回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAnalysisResponse {
    
    /**
     * 请求ID
     */
    private String requestId;
    
    /**
     * 响应数据
     */
    private Object data;
    
    /**
     * 是否为最终结果
     */
    private Boolean isFinal;

    /**
     * 文件列表
     */
    private List<CodeInterpreterResponse.FileInfo> fileInfo;

    /**
     * task
     */
    private String task;

    /**
     * 当前可视化结果所属的 toolCallId，用于前端折叠实时占位卡片。
     */
    private String toolCallId;
}
