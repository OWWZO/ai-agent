package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单会话历史记忆聚合根。
 * 负责承接同一 session 下多个历史 run 的中间结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionHistoryMemory {

    private String sessionId;

    private String currentRequestId;

    @Builder.Default
    private List<RunHistoryMemory> runs = new ArrayList<>();
}
