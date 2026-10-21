package org.wwz.ai.trigger.http.browser.bsk;

/**
 * bsk 子协议 token → userId 的解析 seam。
 *
 * <p>默认实现委托给现有 {@code BrowserRelayApplicationService.resolveUserId(token)}
 * （配对 token 表），与 Reactor relay 共用同一套配对凭证。
 * 定义独立接口是为了让 {@link BskAuthSubProtocolInterceptor} 的测试
 * 无需构造 case 层服务。</p>
 */
@FunctionalInterface
public interface BskTokenResolver {

    /**
     * @param token 子协议里携带的 {@code bsk-auth.<token>} 凭证
     * @return 归属 userId；无效 / 过期 / 空白返回 null（连接将被拒绝）
     */
    String resolveUserId(String token);
}
