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

    @Builder.Default
    private List<FileArtifactMemory> sessionInputFiles = new ArrayList<>();

    @Builder.Default
    private List<ReactCycleMemory> reactCycles = new ArrayList<>();
}
