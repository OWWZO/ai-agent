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
    // 终答原文，保留 $$$ 点名段；展示剥离由前端做
    private String taskSummary;
    // 不再由后端填充；交付文件由前端按 artifactKeys 映射会话文件
    private List<File> files;
    // $$$ 点名的工作区相对路径/文件名；无分隔符时为空
    private List<String> artifactKeys;
}
