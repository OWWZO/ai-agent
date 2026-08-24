package org.wwz.ai.domain.agent.runtime.capability;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.repository.ISessionCapabilityRepository;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话级 skill / MCP 能力清单与差集开关。
 */
@Service
@RequiredArgsConstructor
public class SessionCapabilityService {

    public static final String KIND_SKILL = "skill";
    public static final String KIND_MCP = "mcp";

    private final ISessionCapabilityRepository repository;
    private final SkillRegistry skillRegistry;
    private final McpToolExecutor mcpToolExecutor;

    public SessionCapabilitiesView capabilities(String sessionId) {
        Map<String, Map<String, Boolean>> overrides = repository.findOverrides(sessionId);
        Map<String, Boolean> skillOv = overrides.getOrDefault(KIND_SKILL, Map.of());
        Map<String, Boolean> mcpOv = overrides.getOrDefault(KIND_MCP, Map.of());

        List<CapabilityItem> skills = new ArrayList<>();
        if (skillRegistry != null && skillRegistry.isEnabled()) {
            for (SkillDefinition def : skillRegistry.listSkills()) {
                if (def == null || StringUtils.isBlank(def.getName())) {
                    continue;
                }
                boolean enabled = skillOv.getOrDefault(def.getName(), true);
                skills.add(new CapabilityItem(def.getName(), def.getName(), enabled, "system"));
            }
        }

        List<CapabilityItem> mcps = new ArrayList<>();
        Set<String> seenMcp = new LinkedHashSet<>();
        try {
            List<McpToolInfo> tools = mcpToolExecutor.discoverConfiguredTools();
            for (McpToolInfo t : tools) {
                if (t == null) {
                    continue;
                }
                String mcpId = StringUtils.defaultIfBlank(t.getMcpId(), t.getServerKey());
                if (StringUtils.isBlank(mcpId) || !seenMcp.add(mcpId)) {
                    continue;
                }
                String label = StringUtils.defaultIfBlank(t.getServerKey(), mcpId);
                boolean enabled = mcpOv.getOrDefault(mcpId, true);
                mcps.add(new CapabilityItem(mcpId, label, enabled, "platform"));
            }
        } catch (Exception ignored) {
            // 发现失败返回空 MCP 列表
        }

        return new SessionCapabilitiesView(false, skills, mcps);
    }

    public void setEnabled(String sessionId, String kind, String refId, boolean enabled) {
        if (StringUtils.isAnyBlank(sessionId, kind, refId)) {
            throw new IllegalArgumentException("sessionId/kind/refId 不能为空");
        }
        String k = kind.trim().toLowerCase();
        if (!KIND_SKILL.equals(k) && !KIND_MCP.equals(k)) {
            throw new IllegalArgumentException("kind 仅支持 skill|mcp");
        }
        repository.upsert(sessionId.trim(), k, refId.trim(), enabled);
    }

    /** 返回本会话禁用的 skill 名 / mcpId（未配置 overrides 则空集 = 全开）。 */
    public DisabledSets loadDisabled(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return DisabledSets.empty();
        }
        Map<String, Map<String, Boolean>> overrides = repository.findOverrides(sessionId);
        Set<String> disabledSkills = new HashSet<>();
        Set<String> disabledMcps = new HashSet<>();
        overrides.getOrDefault(KIND_SKILL, Map.of()).forEach((id, en) -> {
            if (Boolean.FALSE.equals(en)) {
                disabledSkills.add(id);
            }
        });
        overrides.getOrDefault(KIND_MCP, Map.of()).forEach((id, en) -> {
            if (Boolean.FALSE.equals(en)) {
                disabledMcps.add(id);
            }
        });
        return new DisabledSets(disabledSkills, disabledMcps);
    }

    public record CapabilityItem(String refId, String name, boolean enabled, String source) {
    }

    public record SessionCapabilitiesView(
            boolean locked,
            List<CapabilityItem> skills,
            List<CapabilityItem> mcpServers
    ) {
    }

    public record DisabledSets(Set<String> skills, Set<String> mcps) {
        public static DisabledSets empty() {
            return new DisabledSets(Set.of(), Set.of());
        }

        public boolean isSkillDisabled(String name) {
            return name != null && skills.contains(name);
        }

        public boolean isMcpDisabled(String mcpId) {
            return mcpId != null && mcps.contains(mcpId);
        }
    }

    /** 供前端 JSON 序列化的 Map 形状 */
    public Map<String, Object> toMap(SessionCapabilitiesView view) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("locked", view.locked());
        root.put("skills", view.skills().stream().map(this::itemMap).toList());
        root.put("mcpServers", view.mcpServers().stream().map(this::itemMap).toList());
        return root;
    }

    private Map<String, Object> itemMap(CapabilityItem item) {
        Map<String, Object> m = new HashMap<>();
        m.put("refId", item.refId());
        m.put("name", item.name());
        m.put("enabled", item.enabled());
        m.put("source", item.source());
        return m;
    }
}
