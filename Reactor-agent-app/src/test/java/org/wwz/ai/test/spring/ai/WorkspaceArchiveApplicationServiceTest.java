package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.application.agent.file.WorkspaceArchiveApplicationService;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WorkspaceArchiveApplicationServiceTest {

    @Test
    public void shouldZipNestedWorkspaceFilesWithRelativePaths() throws Exception {
        Path workspaceRoot = Files.createTempDirectory("reactor-workspace-archive");
        Files.createDirectories(workspaceRoot.resolve("site/css"));
        Files.writeString(workspaceRoot.resolve("site/index.html"), "<html></html>", StandardCharsets.UTF_8);
        Files.writeString(workspaceRoot.resolve("site/css/style.css"), "body{}", StandardCharsets.UTF_8);

        WorkspaceRuntimeOptions options = WorkspaceRuntimeOptions.builder()
                .enabled(true)
                .rootTemplate(workspaceRoot.toString())
                .build();
        WorkspaceService workspaceService = new WorkspaceService(
                options,
                new WorkspacePathGuard(),
                null,
                new SkillVirtualPaths(SkillRuntimeOptions.builder().enabled(false).build()));
        ConversationSessionOwnershipApplicationService ownership =
                Mockito.mock(ConversationSessionOwnershipApplicationService.class);
        Mockito.when(ownership.ensureExistingSessionAccessible("visitor-1", "session-archive-1"))
                .thenReturn(DialogueSession.builder().sessionId("session-archive-1").build());

        WorkspaceArchiveApplicationService service =
                new WorkspaceArchiveApplicationService(ownership, workspaceService);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeArchive("visitor-1", "session-archive-1", output);

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName().replace('\\', '/'));
            }
        }
        Assert.assertTrue(entries.contains("site/index.html"));
        Assert.assertTrue(entries.contains("site/css/style.css"));
    }
}
