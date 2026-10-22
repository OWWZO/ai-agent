package org.wwz.ai.test.infrastructure;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.infrastructure.http.ProxySupport;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;

/**
 * ProxySupport 代理解析：http/https/socks5 解析、非法输入拒绝与 ProxySelector 包装。
 */
public class ProxySupportTest {

    @Test
    public void blankProxyMeansDirect() {
        Assert.assertNull(ProxySupport.parse(null));
        Assert.assertNull(ProxySupport.parse(""));
        Assert.assertNull(ProxySupport.parse("   "));
    }

    @Test
    public void httpProxyIsParsed() {
        Proxy proxy = ProxySupport.parse("http://127.0.0.1:7890");
        Assert.assertEquals(Proxy.Type.HTTP, proxy.type());
        InetSocketAddress address = (InetSocketAddress) proxy.address();
        Assert.assertEquals("127.0.0.1", address.getHostString());
        Assert.assertEquals(7890, address.getPort());
    }

    @Test
    public void httpsSchemeIsStillHttpProxy() {
        Proxy proxy = ProxySupport.parse("https://127.0.0.1:7890");
        Assert.assertEquals(Proxy.Type.HTTP, proxy.type());
    }

    @Test
    public void socksProxyIsParsed() {
        Proxy proxy = ProxySupport.parse("socks5://127.0.0.1:1080");
        Assert.assertEquals(Proxy.Type.SOCKS, proxy.type());
    }

    @Test
    public void invalidProxyThrows() {
        assertInvalid("127.0.0.1:7890");   // 缺 scheme
        assertInvalid("http://127.0.0.1"); // 缺端口
        assertInvalid("ftp://127.0.0.1:7890"); // 不支持的 scheme
        assertInvalid("http://user:pass@127.0.0.1:7890"); // 携带凭证
        assertInvalid("not a uri");
    }

    private void assertInvalid(String raw) {
        try {
            ProxySupport.parse(raw);
            Assert.fail("expected IllegalArgumentException for: " + raw);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void proxySelectorAlwaysReturnsSameProxy() {
        Proxy proxy = ProxySupport.parse("http://127.0.0.1:7890");
        ProxySelector selector = ProxySupport.proxySelector(proxy);
        List<Proxy> selected = selector.select(URI.create("https://api.openai.com/v1/chat/completions"));
        Assert.assertEquals(1, selected.size());
        Assert.assertSame(proxy, selected.get(0));
        // connectFailed 不抛异常
        selector.connectFailed(URI.create("https://api.openai.com"), null, null);
    }
}
