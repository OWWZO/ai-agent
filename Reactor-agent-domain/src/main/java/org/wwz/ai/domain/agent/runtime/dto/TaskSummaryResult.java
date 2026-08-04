package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务摘要解析结果，包含最终文本和模型勾选的 artifact 文件。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskSummaryResult {
    //llm最终输出
    private String taskSummary;
    //附带当前对话请求的最终产生的交付给用户的文件 非中间文件
    private List<File> files;
}
