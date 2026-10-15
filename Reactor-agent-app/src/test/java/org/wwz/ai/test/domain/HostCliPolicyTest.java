package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.cli.HostCliPolicy;

import java.util.List;
import java.util.Map;

/**
 * Host CLI 准入策略：白名单、社交 CLI 只读、argv-only、超时与代理环境。
 * <p>
 * {@code host_cli} 工具和 Code Mode 的 host capability 共用这一份实现。
 * </p>
 */
public class HostCliPolicyTest {

    private static final List<String> ALLOW = List.of("jq", "twitter", "rdt");

    @Test
    public void onlyWhitelistedToolsAreAllowed() {
        Assert.assertTrue(HostCliPolicy.isAllowed("jq", ALLOW));
        Assert.assertFalse(HostCliPolicy.isAllowed("curl", ALLOW));
        Assert.assertFalse(HostCliPolicy.isAllowed("  ", ALLOW));
        Assert.assertFalse(HostCliPolicy.isAllowed("jq", List.of()));
    }

    @Test
    public void socialClisAreReadOnly() {
        Assert.assertEquals("post", HostCliPolicy.deniedSocialCommand("twitter", List.of("post", "hello")));
        Assert.assertEquals("upvote", HostCliPolicy.deniedSocialCommand("rdt", List.of("upvote", "1")));
        Assert.assertNull(HostCliPolicy.deniedSocialCommand("twitter", List.of("search", "手机")));
        Assert.assertNull(HostCliPolicy.deniedSocialCommand("rdt", List.of("search", "x")));
        Assert.assertNull(HostCliPolicy.deniedSocialCommand("jq", List.of("post", ".")));
    }

    @Test
    public void optionsDoNotCountAsSubcommands() {
        Assert.assertNull(HostCliPolicy.deniedSocialCommand("twitter", List.of("-f", "json", "search", "x")));
    }

    @Test
    public void argvMustBeStringArray() {
        Assert.assertEquals(List.of("a", "b"), HostCliPolicy.stringList(List.of("a", "b")));
        Assert.assertTrue(HostCliPolicy.stringList("a b c").isEmpty());
        Assert.assertTrue(HostCliPolicy.stringList(List.of(1, 2)).isEmpty());
    }

    @Test
    public void timeoutIsClamped() {
        Assert.assertEquals(120_000L, HostCliPolicy.resolveTimeout(null, 120_000L, 300_000L));
        Assert.assertEquals(300_000L, HostCliPolicy.resolveTimeout(999_999L, 120_000L, 300_000L));
        Assert.assertEquals(5_000L, HostCliPolicy.resolveTimeout(5_000, 120_000L, 300_000L));
    }

    @Test
    public void proxyEnvironmentIsOnlyInjectedWhenConfigured() {
        Assert.assertTrue(HostCliPolicy.proxyEnvironment(null).isEmpty());
        Map<String, String> env = HostCliPolicy.proxyEnvironment("http://127.0.0.1:7890");
        Assert.assertEquals("http://127.0.0.1:7890", env.get("HTTP_PROXY"));
        Assert.assertEquals("http://127.0.0.1:7890", env.get("https_proxy"));
        Assert.assertTrue(HostCliPolicy.proxyUnsetVariables(null).contains("HTTP_PROXY"));
        Assert.assertTrue(HostCliPolicy.proxyUnsetVariables("http://p:1").isEmpty());
    }
}
