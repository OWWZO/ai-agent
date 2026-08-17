package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillAuthorTool;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPackageService;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeLayout;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Legacy skill_author still works when authoringEnabled. */
public class SkillAuthorToolTest {

    private Path skillRoot;
    private Path workspace;
    private SkillPackageService packageService;
    private SkillAuthorTool authorTool;
    private DefaultSkillRegistry registry;

    @Before
    public void setUp() throws Exception {
        skillRoot = Files.createTempDirectory("skill-lib-");
        workspace = Files.createTempDirectory("skill-ws-");
        SkillRuntimeOptions options = SkillRuntimeOptions.builder()
                .enabled(true)
                .directories(List.of(skillRoot.toString()))
                .authoringEnabled(true)
                .runtimePython("python")
                .build();
        SkillPathGuard guard = new SkillPathGuard();
        registry = new DefaultSkillRegistry(
                options, new SkillMarkdownParser(), new SkillScriptDiscoverer(guard), guard);
        registry.refresh();
        packageService = new SkillPackageService(options, registry);
        SkillRuntimeLayout layout = new SkillRuntimeLayout(options);
        SkillVirtualPaths virtualPaths = new SkillVirtualPaths(options);
        SkillMaterializer materializer = new SkillMaterializer(virtualPaths, layout, options);
        authorTool = new SkillAuthorTool(packageService, materializer);
        authorTool.setAgentContext(AgentContext.builder()
                .requestId("r1")
                .sessionId("s1")
                .workspaceRoot(workspace.toString())
                .query("q")
                .build());
    }

    @Test
    public void shouldUpsertWriteScript() throws Exception {
        Map<String, Object> upsert = new LinkedHashMap<>();
        upsert.put("action", "upsert");
        upsert.put("skill_name", "my-report");
        upsert.put("description", "gen");
        upsert.put("content", "run ${PYTHON} ${SKILL_DIR}/scripts/run.py");
        ToolResultPayload p1 = (ToolResultPayload) authorTool.execute(upsert);
        Assert.assertFalse(Boolean.TRUE.equals(p1.getFailed()));

        Map<String, Object> write = new LinkedHashMap<>();
        write.put("action", "write_file");
        write.put("skill_name", "my-report");
        write.put("path", "scripts/run.py");
        write.put("content", "print('ok')\n");
        ToolResultPayload p2 = (ToolResultPayload) authorTool.execute(write);
        Assert.assertFalse(Boolean.TRUE.equals(p2.getFailed()));

        Assert.assertTrue(Files.isRegularFile(skillRoot.resolve("my-report/scripts/run.py")));
        Assert.assertEquals("print('ok')\n",
                Files.readString(skillRoot.resolve("my-report/scripts/run.py"), StandardCharsets.UTF_8));
        Assert.assertTrue(registry.findSkill("my-report").isPresent());
    }
}
