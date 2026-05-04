package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 锁定 Agent 上下文收敛第一阶段边界。
 */
public class AgentContextConvergenceBoundaryTest {

    private static final Path PROJECT_ROOT = resolveProjectRoot();

    @Test
    public void shouldIntroduceCaseModuleForAgentOrchestration() {
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-case").resolve("pom.xml")));
    }

    @Test
    public void shouldProvideCaseLevelAgentDispatchContract() {
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-case")
                .resolve("src")
                .resolve("main")
                .resolve("java")
                .resolve("org")
                .resolve("wwz")
                .resolve("ai")
                .resolve("application")
                .resolve("agent")
                .resolve("dispatch")
                .resolve("IAgentDispatchService.java")));
    }

    @Test
    public void shouldKeepTriggerSideSseAdapterOutsideDomain() {
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-trigger")
                .resolve("src")
                .resolve("main")
                .resolve("java")
                .resolve("org")
                .resolve("wwz")
                .resolve("ai")
                .resolve("trigger")
                .resolve("http")
                .resolve("reactor")
                .resolve("support")
                .resolve("SseEmitterAgentSessionStream.java")));
    }

    private static Path resolveProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    && Files.exists(current.resolve("ai-agent-station-study-domain"))
                    && Files.exists(current.resolve("ai-agent-station-study-trigger"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位仓库根目录");
    }
}
