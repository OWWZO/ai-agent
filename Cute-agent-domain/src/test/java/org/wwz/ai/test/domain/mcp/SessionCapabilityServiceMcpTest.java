package org.wwz.ai.test.domain.mcp;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.repository.ISessionCapabilityRepository;
import org.wwz.ai.domain.agent.runtime.capability.SessionCapabilityService;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpResourceInfo;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.mcp.port.McpToolExecutor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 会话能力服务只依赖 MCP 领域端口，不依赖注册中心实现。 */
public class SessionCapabilityServiceMcpTest {

    @Test
    public void shouldExposeMcpOverrideThroughTypedExecutorPort() {
        SessionCapabilityService service = new SessionCapabilityService(
                new FakeCapabilityRepository(),
                new DisabledSkillRegistry(),
                new FakeMcpExecutor());

        SessionCapabilityService.SessionCapabilitiesView view = service.capabilities("session-1");

        Assert.assertEquals(1, view.mcpServers().size());
        Assert.assertEquals("mcp-1", view.mcpServers().get(0).refId());
        Assert.assertFalse(view.mcpServers().get(0).enabled());
        Assert.assertTrue(service.loadDisabled("session-1").isMcpDisabled("mcp-1"));
    }

    private static final class FakeCapabilityRepository implements ISessionCapabilityRepository {
        @Override
        public Map<String, Map<String, Boolean>> findOverrides(String sessionId) {
            return Map.of(SessionCapabilityService.KIND_MCP, Map.of("mcp-1", false));
        }

        @Override
        public void upsert(String sessionId, String kind, String refId, boolean enabled) {
        }

        @Override
        public List<SessionCapabilityRow> listBySession(String sessionId) {
            return List.of();
        }
    }

    private static final class DisabledSkillRegistry implements SkillRegistry {
        @Override
        public void refresh() {
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public Collection<SkillDefinition> listSkills() {
            return List.of();
        }

        @Override
        public Optional<SkillDefinition> findSkill(String skillName) {
            return Optional.empty();
        }

        @Override
        public SkillDefinition getRequiredSkill(String skillName) {
            throw new IllegalArgumentException(skillName);
        }

        @Override
        public Path assertPathAllowed(Path candidatePath) {
            return candidatePath;
        }

        @Override
        public String buildSkillDescription() {
            return "";
        }
    }

    private static final class FakeMcpExecutor implements McpToolExecutor {
        @Override
        public List<McpToolInfo> listGlobalEnabledTools() {
            return List.of(McpToolInfo.builder()
                    .mcpId("mcp-1")
                    .serverKey("demo")
                    .name("mcp__demo__search")
                    .originalName("search")
                    .build());
        }

        @Override
        public List<McpToolInfo> listToolsByMcpIds(List<String> mcpIds) {
            return listGlobalEnabledTools();
        }

        @Override
        public List<McpResourceInfo> listGlobalEnabledResources() {
            return List.of();
        }

        @Override
        public List<McpResourceInfo> listResourcesByMcpIds(List<String> mcpIds) {
            return List.of();
        }

        @Override
        public List<McpResourceInfo> listResources(String serverOrMcpId) {
            return List.of();
        }

        @Override
        public boolean hasAnyResources() {
            return false;
        }

        @Override
        public String readResource(String serverOrMcpId, String uri) {
            return "";
        }

        @Override
        public String callTool(String mcpId, String toolName, Object args) {
            return "";
        }

        @Override
        public void reload() {
        }
    }
}
