package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePaths;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;

import java.nio.file.Files;
import java.nio.file.Path;

public class WorkspacePathsTest {

    @Test
    public void shouldResolveRepoRootContainingReactorTool() {
        Path root = WorkspacePaths.resolveRepoRoot();
        Assert.assertTrue(Files.isDirectory(root.resolve("reactor-tool")));
    }

    @Test
    public void shouldExpandWorkspaceTemplateUnderRepoReactorTool() {
        WorkspaceService service = new WorkspaceService(
                WorkspaceRuntimeOptions.builder()
                        .enabled(true)
                        .rootTemplate("${user.dir}/reactor-tool/skilloutput/{sessionId}")
                        .build(),
                null,
                null,
                null
        );
        Path resolved = service.resolveRoot("session-align-001");
        Path expected = WorkspacePaths.resolveRepoRoot()
                .resolve("reactor-tool")
                .resolve("skilloutput")
                .resolve("session-align-001")
                .normalize();
        Assert.assertEquals(expected, resolved.normalize());
        Assert.assertTrue(Files.isDirectory(WorkspacePaths.resolveRepoRoot().resolve("Reactor-agent-app")));
        Assert.assertTrue(resolved.endsWith(Path.of("reactor-tool", "skilloutput", "session-align-001")));
    }

    @Test
    public void shouldPreferMonorepoWhenParentAlsoHasReactorTool() {
        String previous = System.getProperty("user.dir");
        try {
            Path monorepo = WorkspacePaths.resolveRepoRoot();
            Path parent = monorepo.getParent();
            Assert.assertNotNull(parent);
            System.setProperty("user.dir", parent.toString());
            Path resolved = WorkspacePaths.resolveRepoRoot();
            Assert.assertEquals(monorepo.normalize(), resolved.normalize());
        } finally {
            if (previous == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", previous);
            }
        }
    }

    @Test
    public void shouldIgnoreSpringPreExpandedWrongUserDirSkilloutput() {
        Path monorepo = WorkspacePaths.resolveRepoRoot();
        Path parent = monorepo.getParent();
        Assert.assertNotNull(parent);
        // 模拟 Spring 已把 ${user.dir} 展开成 monorepo 上级目录
        String springExpanded = parent + "/reactor-tool/skilloutput/{sessionId}";
        WorkspaceService service = new WorkspaceService(
                WorkspaceRuntimeOptions.builder()
                        .enabled(true)
                        .rootTemplate(springExpanded)
                        .build(),
                null,
                null,
                null
        );
        Path resolved = service.resolveRoot("session-spring-preexpand");
        Path expected = monorepo.resolve("reactor-tool").resolve("skilloutput")
                .resolve("session-spring-preexpand").normalize();
        Assert.assertEquals(expected, resolved.normalize());
    }
}
