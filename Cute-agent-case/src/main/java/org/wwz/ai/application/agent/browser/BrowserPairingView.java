package org.wwz.ai.application.agent.browser;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BrowserPairingView {

    String token;
    String code;
    String relayUrl;
    long expiresAt;

    /**
     * bsk 扩展的配对链接（021）：{@code ws(s)://host/api/agent/browser/bsk-relay#token}。
     * 扩展侧粘贴/接收后自动剥 fragment，token 经 WS 子协议头 {@code bsk-auth.<token>} 发送。
     * 为 {@code null} 表示本次配对未生成 bsk 链接（网关未启用）。
     */
    String bskPairingUrl;
}
