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
