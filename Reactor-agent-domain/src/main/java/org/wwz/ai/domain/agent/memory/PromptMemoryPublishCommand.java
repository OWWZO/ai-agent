package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一次请求完成后要原子发布的提示词记忆增量。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptMemoryPublishCommand {

    private PromptMemoryLease lease;

    private Long runId;

    private List<PromptMemoryMessage> deltaMessages;
}
