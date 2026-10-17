package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.infrastructure.adapter.port.HostProcessCliAdapter;

import java.util.List;
import java.util.Map;

public class HostProcessCliAdapterTest {

    @Test
    public void shouldCaptureStdoutWithoutShell() {
        HostProcessCliAdapter adapter = new HostProcessCliAdapter();
        Assert.assertTrue(adapter.isResolvable("node"));
        CliResult result = adapter.exec(CliInvocation.builder()
                .tool("node")
                .args(List.of("-e", "process.stdout.write('hello-cli'); process.stderr.write('err')"))
                .timeoutMs(10_000)
                .maxOutputChars(4_000)
                .capture("both")
                .build());
        Assert.assertTrue(result.isOk());
        Assert.assertEquals("hello-cli", result.getStdout());
        Assert.assertEquals("err", result.getStderr());
        Assert.assertFalse(result.isTimedOut());
    }

    @Test
    public void shouldKillOnTimeout() {
        HostProcessCliAdapter adapter = new HostProcessCliAdapter();
        CliResult result = adapter.exec(CliInvocation.builder()
                .tool("node")
                .args(List.of("-e", "Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,5000)"))
                .timeoutMs(400)
                .maxOutputChars(1_000)
                .capture("both")
                .build());
        Assert.assertFalse(result.isOk());
        Assert.assertTrue(result.isTimedOut());
    }

    @Test
    public void shouldMergeEnvAndRefusePs1() {
        HostProcessCliAdapter adapter = new HostProcessCliAdapter();
        CliResult env = adapter.exec(CliInvocation.builder()
                .tool("node")
                .args(List.of("-e", "process.stdout.write(process.env.REACTOR_CLI_TEST || '')"))
                .env(Map.of("REACTOR_CLI_TEST", "merged"))
                .timeoutMs(10_000)
                .maxOutputChars(1_000)
                .capture("both")
                .build());
        Assert.assertEquals("merged", env.getStdout());
        Assert.assertFalse(adapter.isResolvable("definitely-missing-bin-xyz"));
        CliResult ps1 = adapter.exec(CliInvocation.builder()
                .tool("script.ps1")
                .args(List.of())
                .timeoutMs(1_000)
                .maxOutputChars(100)
                .build());
        Assert.assertFalse(ps1.isOk());
        Assert.assertTrue(ps1.getStderr().contains("PowerShell"));
    }

    @Test
    public void shouldParseReportedArtifactCandidates() {
        List<org.wwz.ai.domain.agent.adapter.port.cli.CliArtifact> artifacts =
                org.wwz.ai.infrastructure.adapter.port.CliArtifactCandidateParser.parse(
                        "{\"status\":\"ok\",\"artifacts\":["
                                + "{\"relativePath\":\"xhs/note-1_1.jpg\",\"size\":183421,\"mime\":\"IMAGE/JPEG\"},"
                                // 绝对路径和 .. 逃逸在协议层直接丢弃
                                + "{\"path\":\"/etc/passwd\"},"
                                + "{\"path\":\"../escape.txt\"}]}");
        Assert.assertEquals(1, artifacts.size());
        Assert.assertEquals("xhs/note-1_1.jpg", artifacts.get(0).getPath());
        Assert.assertEquals(Long.valueOf(183421L), artifacts.get(0).getSize());
        Assert.assertEquals("image/jpeg", artifacts.get(0).getMime());
    }

    @Test
    public void shouldIgnoreStdoutWithoutArtifactCandidates() {
        Assert.assertTrue(org.wwz.ai.infrastructure.adapter.port.CliArtifactCandidateParser
                .parse("hello-cli").isEmpty());
        Assert.assertTrue(org.wwz.ai.infrastructure.adapter.port.CliArtifactCandidateParser
                .parse("{\"status\":\"ok\"}").isEmpty());
        Assert.assertTrue(org.wwz.ai.infrastructure.adapter.port.CliArtifactCandidateParser
                .parse(null).isEmpty());
    }

    @Test
    public void shouldParseArtifactEnvelopeInsideJsonArrayAfterLogs() {
        List<org.wwz.ai.domain.agent.adapter.port.cli.CliArtifact> artifacts =
                org.wwz.ai.infrastructure.adapter.port.CliArtifactCandidateParser.parse(
                        "adapter log\n[{\"index\":1,\"status\":\"success\"},"
                                + "{\"artifacts\":[{\"path\":\"downloads\\\\photo..jpg\",\"size\":7}]}]");

        Assert.assertEquals(1, artifacts.size());
        Assert.assertEquals("downloads/photo..jpg", artifacts.get(0).getPath());
        Assert.assertEquals(Long.valueOf(7L), artifacts.get(0).getSize());
    }

    @Test
    public void shouldRejectWindowsAndUncCandidatePaths() {
        Assert.assertTrue(org.wwz.ai.infrastructure.adapter.port.CliArtifactCandidateParser
                .parse("{\"artifacts\":[{\"path\":\"C:\\\\tmp\\\\file.jpg\"}]}").isEmpty());
        Assert.assertTrue(org.wwz.ai.infrastructure.adapter.port.CliArtifactCandidateParser
                .parse("{\"artifacts\":[{\"path\":\"\\\\\\\\server\\\\share\\\\file.jpg\"}]}").isEmpty());
    }

    @Test
    public void shouldReadArtifactCandidatesFromCliStdout() {
        HostProcessCliAdapter adapter = new HostProcessCliAdapter();
        CliResult result = adapter.exec(CliInvocation.builder()
                .tool("node")
                .args(List.of("-e",
                        "process.stdout.write(JSON.stringify({artifacts:[{relativePath:'xhs/a.jpg',size:3}]}))"))
                .timeoutMs(10_000)
                .maxOutputChars(4_000)
                .capture("both")
                .build());
        Assert.assertTrue(result.isOk());
        Assert.assertEquals(1, result.getArtifacts().size());
        Assert.assertEquals("xhs/a.jpg", result.getArtifacts().get(0).getPath());
    }

    @Test
    public void shouldUnsetParentSecretsBeforeChildStart() {
        HostProcessCliAdapter adapter = new HostProcessCliAdapter();
        java.util.HashMap<String, String> environment = new java.util.HashMap<>();
        environment.put("KERNEL_API_KEY", "secret-key");
        environment.put("PATH", "keep-me");
        HostProcessCliAdapter.applyChildEnvironment(environment, CliInvocation.builder()
                .tool("node")
                .env(Map.of("OPENCLI_CDP_ENDPOINT", "wss://example.test/cdp"))
                .unsetEnv(List.of("KERNEL_API_KEY"))
                .timeoutMs(1_000)
                .maxOutputChars(100)
                .build());
        Assert.assertFalse(environment.containsKey("KERNEL_API_KEY"));
        Assert.assertEquals("keep-me", environment.get("PATH"));
        Assert.assertEquals("wss://example.test/cdp", environment.get("OPENCLI_CDP_ENDPOINT"));

        CliResult child = adapter.exec(CliInvocation.builder()
                .tool("node")
                .args(List.of("-e", "process.stdout.write(process.env.KERNEL_API_KEY ? 'leaked' : 'absent')"))
                .unsetEnv(List.of("KERNEL_API_KEY"))
                .timeoutMs(10_000)
                .maxOutputChars(1_000)
                .capture("both")
                .build());
        Assert.assertEquals("absent", child.getStdout());
    }
}
