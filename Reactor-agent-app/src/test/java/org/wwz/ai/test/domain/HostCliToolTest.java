package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.cli.HostCliTool;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;
import org.wwz.ai.types.agent.config.HostCliProperties;

import java.util.List;
import java.util.Map;

public class HostCliToolTest {

    @Test
    public void shouldRejectToolsOutsideAllowlist() {
        HostCliTool tool = new HostCliTool(List.of("git"));
        tool.setAgentContext(contextWith("git"));
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "tool", "rm",
                "args", List.of("-rf", "/")
        ));
        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertEquals("host_cli_denied", payload.getErrorMsg());
    }

    @Test
    public void shouldExecAllowlistedTool() {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.isResolvable("git")).thenReturn(true);
        Mockito.when(cli.exec(Mockito.any())).thenReturn(CliResult.builder()
                .ok(true)
                .tool("git")
                .exitCode(0)
                .stdout("ok")
                .stderr("")
                .durationMs(3)
                .build());
        HostCliTool tool = new HostCliTool(List.of("git"));
        tool.setAgentContext(contextWith(cli));
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "tool", "git",
                "args", List.of("status")
        ));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(String.valueOf(payload.getLlmData()).contains("ok"));
    }

    @Test
    public void shouldRejectWriteCommandsForSocialCli() {
        HostCliTool tool = new HostCliTool(List.of("twitter", "rdt"));
        tool.setAgentContext(contextWith("git"));

        ToolResultPayload twitterPayload = (ToolResultPayload) tool.execute(Map.of(
                "tool", "twitter",
                "args", List.of("post", "hello")
        ));
        ToolResultPayload redditPayload = (ToolResultPayload) tool.execute(Map.of(
                "tool", "rdt",
                "args", List.of("upvote", "abc")
        ));

        Assert.assertTrue(Boolean.TRUE.equals(twitterPayload.getFailed()));
        Assert.assertEquals("host_cli_denied", twitterPayload.getErrorMsg());
        Assert.assertTrue(Boolean.TRUE.equals(redditPayload.getFailed()));
        Assert.assertEquals("host_cli_denied", redditPayload.getErrorMsg());
    }

    private AgentContext contextWith(String ignored) {
        return contextWith(Mockito.mock(CliExecutionPort.class));
    }

    private AgentContext contextWith(CliExecutionPort cli) {
        HostCliProperties properties = new HostCliProperties();
        properties.setEnabled(true);
        properties.setAllow(List.of("git"));
        return AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .workspaceRoot(System.getProperty("java.io.tmpdir"))
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig())
                        .toBuilder()
                        .cliExecutionPort(cli)
                        .hostCliProperties(properties)
                        .build())
                .build();
    }
}
