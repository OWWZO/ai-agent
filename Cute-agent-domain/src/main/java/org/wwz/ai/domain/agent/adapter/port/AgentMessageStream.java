package org.wwz.ai.domain.agent.adapter.port;

/**
 * 协议无关的流式输出端口。
 * domain 只依赖该端口发送增量事件，具体采用 SSE、WebSocket 还是其他协议由 trigger/case 侧适配。
 */
public interface AgentMessageStream {

    void send(Object payload) throws Exception;

    void complete();

    void completeWithError(Throwable throwable);

    /**
     * 注册下游异常中断后的回调。
     * 典型场景是浏览器主动断开 SSE，此时调用方可以解绑观察流；是否取消后台 run
     * 由调用方根据业务语义决定，客户端断开本身不等于用户主动停止。
     */
    default void onAbort(Runnable abortHandler) {
    }

    /**
     * 当前下游输出链路是否已经异常中断。
     */
    default boolean isAborted() {
        return false;
    }
}
