package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次工具调用的历史记忆。
 * 保存工具输入、主智能体观察结果和关联文件元信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallMemory {

    private Long toolInvocationId;

    private Long llmInvocationId;

    private String toolCallId;

    private Integer dispatchIndex;

    private String agentName;

    private Integer stepNo;

    private String toolName;

    private String toolProvider;

    private String inputJson;

    private String llmObservation;

    @Builder.Default
    private List<FileArtifactMemory> files = new ArrayList<>();
}
