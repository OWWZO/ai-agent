package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.config.SkillDirectoryResolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Skill 目录解析测试。
 */
public class SkillDirectoryResolverTest {

    @Test
    public void shouldFallbackToProjectRootRuntimeSkillsWhenStartedFromModuleDirectory() throws Exception {
        Path projectRoot = createProjectLayout();
        Path moduleDirectory = Files.createDirectories(projectRoot.resolve("Reactor-agent-app"));
        Path runtimeSkillsDirectory = Files.createDirectories(projectRoot.resolve("runtime").resolve("skills"));

        SkillDirectoryResolver resolver = new SkillDirectoryResolver(moduleDirectory);

        List<String> resolvedDirectories = resolver.resolve(List.of(moduleDirectory.resolve("runtime").resolve("skills").toString()));

        Assert.assertEquals(List.of(runtimeSkillsDirectory.toString()), resolvedDirectories);
    }

    @Test
    public void shouldFallbackToProjectRootRuntimeSkillsWhenConfiguredPathContainsLegacyProjectName() throws Exception {
        Path projectRoot = createProjectLayout();
        Path moduleDirectory = Files.createDirectories(projectRoot.resolve("Reactor-agent-app"));
        Path runtimeSkillsDirectory = Files.createDirectories(projectRoot.resolve("runtime").resolve("skills"));

        SkillDirectoryResolver resolver = new SkillDirectoryResolver(moduleDirectory);

        List<String> resolvedDirectories = resolver.resolve(List.of(moduleDirectory
                .resolve("ai-agent-station-study")
                .resolve("runtime")
                .resolve("skills")
                .toString()));

        Assert.assertEquals(List.of(runtimeSkillsDirectory.toString()), resolvedDirectories);
    }

    @Test
    public void shouldFallbackFromClassesDirectoryWhenWorkingDirectoryIsOutsideRepository() throws Exception {
        Path projectRoot = createProjectLayout();
        Path runtimeSkillsDirectory = Files.createDirectories(projectRoot.resolve("runtime").resolve("skills"));
        Path moduleClassesDirectory = Files.createDirectories(projectRoot
                .resolve("Reactor-agent-app")
                .resolve("target")
                .resolve("classes"));
        Path externalWorkingDirectory = Files.createTempDirectory("skill-directory-external-working-dir");

        SkillDirectoryResolver resolver = new SkillDirectoryResolver(
                externalWorkingDirectory,
                List.of(moduleClassesDirectory)
        );

        List<String> resolvedDirectories = resolver.resolve(List.of(externalWorkingDirectory.resolve("runtime").resolve("skills").toString()));

        Assert.assertEquals(List.of(runtimeSkillsDirectory.toString()), resolvedDirectories);
    }

    private Path createProjectLayout() throws Exception {
        Path projectRoot = Files.createTempDirectory("skill-directory-resolver");
        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);
        Files.createDirectories(projectRoot.resolve("Reactor-agent-domain"));
        return projectRoot;
    }
}
