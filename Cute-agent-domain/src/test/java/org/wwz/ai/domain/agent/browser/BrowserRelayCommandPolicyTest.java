package org.wwz.ai.domain.agent.browser;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.browser.model.BrowserCommand;
import org.wwz.ai.domain.agent.browser.model.BrowserCommandResult;
import org.wwz.ai.domain.agent.browser.policy.BrowserRelayCommandPolicy;

import java.time.Duration;
import java.util.Map;

public class BrowserRelayCommandPolicyTest {

    private final BrowserRelayCommandPolicy policy = new BrowserRelayCommandPolicy();

    @Test
    public void shouldAllowConfiguredActionsAndRejectUnknownActions() {
        Assert.assertNull(policy.rejection(BrowserCommand.of(
                "user-1", "cookies", Map.of(), Duration.ofSeconds(1))));

        BrowserCommandResult rejection = policy.rejection(BrowserCommand.of(
                "user-1", "eval", Map.of(), Duration.ofSeconds(1)));
        Assert.assertEquals("unsupported_action", rejection.getErrorCode());
    }

    @Test
    public void shouldHandleLeaseReleaseWithoutRelayConnection() {
        BrowserCommand command = BrowserCommand.of(
                "user-1", "lease-release", Map.of(), Duration.ofSeconds(1));
        Assert.assertTrue(policy.isLeaseRelease(command));
        Assert.assertNull(policy.rejection(command));
    }

    @Test
    public void shouldApplyActionTimeoutCapsAndDeadline() {
        BrowserCommand screenshot = BrowserCommand.of(
                "user-1", "screenshot", Map.of(), Duration.ofMinutes(2));
        Assert.assertEquals(30_000L, policy.withEffectiveTimeout(
                screenshot, Duration.ofSeconds(60), System.currentTimeMillis()).timeout().toMillis());

        BrowserCommand download = BrowserCommand.of(
                "user-1", "wait-download", Map.of("timeoutMs", 120_000), Duration.ofMinutes(5));
        Assert.assertEquals(120_000L, policy.withEffectiveTimeout(
                download, Duration.ofSeconds(60), System.currentTimeMillis()).timeout().toMillis());

        BrowserCommand downloadWithoutExplicitTimeout = BrowserCommand.of(
                "user-1", "wait-download", Map.of("timeoutMs", 120_000), null);
        Assert.assertEquals(120_000L, policy.withEffectiveTimeout(
                downloadWithoutExplicitTimeout, Duration.ofSeconds(60), System.currentTimeMillis())
                .timeout().toMillis());

        long now = System.currentTimeMillis();
        BrowserCommand deadline = BrowserCommand.of(
                "user-1", "exec", Map.of(), Duration.ofSeconds(60)).withDeadlineAt(now + 200);
        Assert.assertTrue(policy.withEffectiveTimeout(deadline, Duration.ofSeconds(60), now)
                .timeout().toMillis() <= 200);
    }
}
