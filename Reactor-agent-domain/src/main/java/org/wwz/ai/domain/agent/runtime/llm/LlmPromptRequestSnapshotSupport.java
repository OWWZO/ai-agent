package org.wwz.ai.domain.agent.runtime.llm;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * prompt 快照辅助工具。
 * 仅服务观测链路，不改变真正发送给模型的消息列表。
 */
public final class LlmPromptRequestSnapshotSupport {

    private LlmPromptRequestSnapshotSupport() {
    }

    /**
     * ask 链路可能会携带多段 system message。
     * 观测侧只需要一份稳定 system 指纹，因此这里把多段 system 合并成单条快照消息。
     */
    public static Message collapseSystemMessages(List<Message> systemMessages) {
        if (systemMessages == null || systemMessages.isEmpty()) {
            return null;
        }
        List<Message> normalized = new ArrayList<>();
        for (Message systemMessage : systemMessages) {
            if (systemMessage != null) {
                normalized.add(systemMessage);
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() == 1) {
            return normalized.get(0);
        }
        String mergedContent = normalized.stream()
                .map(message -> StringUtils.defaultString(message.getContent()))
                .filter(StringUtils::isNotBlank)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        return Message.systemMessage(mergedContent, null);
    }
}
