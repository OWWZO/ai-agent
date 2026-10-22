package org.wwz.ai.domain.agent.memory.ltm;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 可插拔长期记忆后端。
 */
public interface MemoryProvider {

    String name();

    /**
     * 外部深度 Provider 占用唯一外部槽位。
     */
    default boolean isExternal() {
        return true;
    }

    default boolean isAvailable() {
        return true;
    }

    default void initialize(String sessionId, LtmOwner owner, Map<String, Object> context) {
    }

    default String systemPromptBlock() {
        return "";
    }

    default String prefetch(String query, String sessionId) {
        return "";
    }

    default void queuePrefetch(String query, String sessionId) {
    }

    default void syncTurn(String userContent, String assistantContent, String sessionId, List<Map<String, Object>> messages) {
    }

    default void onSessionEnd(List<Map<String, Object>> messages) {
    }

    default void onSessionSwitch(String newSessionId, String parentSessionId, boolean reset, boolean rewound) {
    }

    default String onPreCompress(List<Map<String, Object>> messages) {
        return "";
    }

    default void onMemoryWrite(String action, String target, String content, Map<String, Object> metadata) {
    }

    default List<Map<String, Object>> getToolSchemas() {
        return Collections.emptyList();
    }

    default String handleToolCall(String toolName, Map<String, Object> args) {
        throw new UnsupportedOperationException("Provider " + name() + " does not handle tool " + toolName);
    }

    default void shutdown() {
    }
}
