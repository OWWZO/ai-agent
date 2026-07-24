package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 run 的历史记忆。
 * 负责承接 run 级输入文件和该 run 下的 ReAct 循环列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunHistoryMemory {

    private Long runId;

    private String requestId;

    private String sessionId;

    private String entryAgent;

    /** 该 run 的用户原始 query */
    private String queryText;

    /** 该 run 最终摘要（可选） */
    private String finalSummaryText;

    @Builder.Default
    private List<FileArtifactMemory> sessionInputFiles = new ArrayList<>();

    @Builder.Default
    private List<ReactCycleMemory> reactCycles = new ArrayList<>();
}
