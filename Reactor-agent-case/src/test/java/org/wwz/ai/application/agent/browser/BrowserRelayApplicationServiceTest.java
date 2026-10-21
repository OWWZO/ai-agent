package org.wwz.ai.application.agent.browser;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.BrowserBskConnectionPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelaySocketPort;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

/**
 * 配对凭证的签发/领取/换发语义。
 *
 * <p>021：bsk 扩展的人工配对路径允许直接填 6 位配对码作为初始凭证，
 * 配对成功后扩展用 {@code /authorize} 换发自生成的 43 位强 token。</p>
 */
public class BrowserRelayApplicationServiceTest {

    @Test
    public void shouldIssueAndClaimPairingCode() {
        BrowserRelayApplicationService service = newService();

        BrowserPairingView issued = service.createPairing("user-1", "ws://localhost:8100/api/agent/browser/relay");
        Assert.assertEquals(6, issued.getCode().length());
        Assert.assertEquals("user-1", service.resolveUserId(issued.getToken()));

        BrowserPairingView claimed = service.claim(issued.getCode());
        Assert.assertEquals(issued.getToken(), claimed.getToken());
        Assert.assertEquals(issued.getRelayUrl(), claimed.getRelayUrl());
        Assert.assertEquals(issued.getToken(), service.claim(issued.getCode()).getToken());
        Assert.assertEquals("user-1", service.resolveUserId(issued.getToken()));
    }

    @Test
    public void shouldAuthorizeWithSixDigitPairingCode() {
        BrowserRelayApplicationService service = newService();

        BrowserPairingView issued = service.createPairing("user-1", "ws://localhost:8100/api/agent/browser/relay");
        String nextToken = "a".repeat(43);

        String rotated = service.authorizeBrowser(issued.getCode(), nextToken, "Chrome · BrowserSkill");
        Assert.assertEquals(nextToken, rotated);
        // 新 token 可解析到同一用户
        Assert.assertEquals("user-1", service.resolveUserId(nextToken));
        // 走码换发不能动原 token：前端一次配对把同一个 token 同时发给老扩展和
        // bsk 扩展，老扩展正靠它连老 relay，撤掉会连带打断老扩展。
        Assert.assertEquals("user-1", service.resolveUserId(issued.getToken()));
        // 6 位码仍指向原 token（老扩展弹窗路径不受影响）
        Assert.assertEquals(issued.getToken(), service.claim(issued.getCode()).getToken());
    }

    @Test
    public void shouldRotateTokenWhenAuthorizedWithToken() {
        BrowserRelayApplicationService service = newService();

        BrowserPairingView issued = service.createPairing("user-1", "ws://localhost:8100/api/agent/browser/relay");
        String nextToken = "c".repeat(43);

        Assert.assertEquals(nextToken, service.authorizeBrowser(issued.getToken(), nextToken, "Chrome · BrowserSkill"));
        // 拿旧 token 换新 token 才是真正的轮换：旧 token 立即失效
        Assert.assertNull(service.resolveUserId(issued.getToken()));
        Assert.assertEquals("user-1", service.resolveUserId(nextToken));
    }

    @Test
    public void shouldRejectAuthorizeWithUnknownCredential() {
        BrowserRelayApplicationService service = newService();
        try {
            service.authorizeBrowser("999999", "b".repeat(43), "Chrome · BrowserSkill");
            Assert.fail("未知凭证必须被拒绝");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("无效"));
        }
    }

    /**
     * 021：bsk 扩展连的是 bsk 网关，老 relay 的在线表里没有它。
     * 两条链路任一在线即视为「浏览器已连接」，前端按钮才能显示已连接。
     */
    @Test
    public void statusReportsConnectedWhenOnlyBskIsOnline() {
        BrowserBskConnectionPort bskPort = new BrowserBskConnectionPort() {
            @Override
            public boolean isOnline(String userId) {
                return "user-1".equals(userId);
            }

            @Override
            public void disconnect(String userId) {
                // 记录调用即可
            }
        };
        BrowserRelayApplicationService service = new BrowserRelayApplicationService(
                new BrowserRelayProperties(), null, bskPort);

        Assert.assertTrue(service.status("user-1").isConnected());
        Assert.assertFalse(service.status("user-2").isConnected());
    }

    @Test
    public void statusIsOfflineWhenNoLinkIsOnline() {
        BrowserBskConnectionPort bskPort = new BrowserBskConnectionPort() {
            @Override
            public boolean isOnline(String userId) {
                return false;
            }

            @Override
            public void disconnect(String userId) {
                // 记录调用即可
            }
        };
        BrowserRelayApplicationService service = new BrowserRelayApplicationService(
                new BrowserRelayProperties(), null, bskPort);

        Assert.assertFalse(service.status("user-1").isConnected());
    }

    private static BrowserRelayApplicationService newService() {
        return new BrowserRelayApplicationService(
                new BrowserRelayProperties(),
                (BrowserRelaySocketPort) null
        );
    }
}
