package org.wwz.ai.domain.agent.adapter.repository;

import java.util.List;
import java.util.Map;

/**
 * 会话能力差集：只存用户显式改过的 skill/mcp 开关。
 */
public interface ISessionCapabilityRepository {

    /**
     * @return kind → (refId → enabled)
     */
    Map<String, Map<String, Boolean>> findOverrides(String sessionId);

    void upsert(String sessionId, String kind, String refId, boolean enabled);

    List<SessionCapabilityRow> listBySession(String sessionId);

    record SessionCapabilityRow(String kind, String refId, boolean enabled) {
    }
}
