package org.wwz.ai.domain.agent.memory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 提示词记忆日志的持久化端口。
 */
public interface IPromptMemoryRepository {

    List<PromptMemoryMessage> loadReadyMessages(PromptMemoryStreamKey key);

    Optional<PromptMemoryLease> acquireLease(PromptMemoryStreamKey key, String requestId,
                                             LocalDateTime now, Duration leaseDuration);

    void releaseLease(PromptMemoryLease lease);

    void publish(PromptMemoryPublishCommand command);
}
