package org.wwz.ai.trigger.http.browser.bsk;

/**
 * bsk 帧出口。抽象一条"能发文本帧、能被关闭"的连接，
 * 让 {@link BskFrameRelay} 的测试无需 Spring WebSocket。
 */
public interface BskSink {

    /** 发送一条文本帧；失败抛任意异常，由调用方决定是否关闭。 */
    void send(String json) throws Exception;

    /** 关闭连接。幂等。 */
    void close();
}
