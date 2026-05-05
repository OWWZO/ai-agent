package org.wwz.ai.domain.agent.adapter.port;

/**
 * 协议无关的远端流式监听器。
 * infrastructure 负责把底层 SSE/HTTP 分帧结果转成按行事件回调给 domain。
 */
public interface RemoteStreamListener {

    /**
     * 建立连接成功后的回调。
     */
    default void onOpen() {
    }

    /**
     * 收到一行上游响应后的回调。
     */
    void onLine(String line) throws Exception;

    /**
     * 流正常结束后的回调。
     */
    default void onClosed() {
    }

    /**
     * 网络失败、上游异常状态码或本地解析异常时的回调。
     */
    default void onFailure(Throwable throwable, Integer statusCode, String responseBody) {
    }
}
