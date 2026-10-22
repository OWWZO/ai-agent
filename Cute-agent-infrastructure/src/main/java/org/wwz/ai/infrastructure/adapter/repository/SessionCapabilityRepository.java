package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.adapter.repository.ISessionCapabilityRepository;
import org.wwz.ai.infrastructure.dao.IAiAgentSessionCapabilityDao;
import org.wwz.ai.infrastructure.dao.po.AiAgentSessionCapability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class SessionCapabilityRepository implements ISessionCapabilityRepository {

    private final IAiAgentSessionCapabilityDao dao;

    @Override
    public Map<String, Map<String, Boolean>> findOverrides(String sessionId) {
        Map<String, Map<String, Boolean>> root = new HashMap<>();
        if (sessionId == null || sessionId.isBlank()) {
            return root;
        }
        List<AiAgentSessionCapability> rows = dao.listBySessionId(sessionId);
        if (rows == null) {
            return root;
        }
        for (AiAgentSessionCapability row : rows) {
            if (row == null || row.getKind() == null || row.getRefId() == null) {
                continue;
            }
            root.computeIfAbsent(row.getKind(), k -> new HashMap<>())
                    .put(row.getRefId(), row.getEnabled() != null && row.getEnabled() == 1);
        }
        return root;
    }

    @Override
    public void upsert(String sessionId, String kind, String refId, boolean enabled) {
        dao.upsert(AiAgentSessionCapability.builder()
                .sessionId(sessionId)
                .kind(kind)
                .refId(refId)
                .enabled(enabled ? 1 : 0)
                .build());
    }

    @Override
    public List<SessionCapabilityRow> listBySession(String sessionId) {
        List<AiAgentSessionCapability> rows = dao.listBySessionId(sessionId);
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .map(r -> new SessionCapabilityRow(
                        r.getKind(),
                        r.getRefId(),
                        r.getEnabled() != null && r.getEnabled() == 1))
                .toList();
    }
}
