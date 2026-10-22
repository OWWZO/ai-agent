package org.wwz.ai.domain.agent.runtime.llm;

import java.util.function.Consumer;

/**
 * LLM 出站能力端口。
 * <p>
 * Domain 只负责请求编排和流式语义；供应商 SDK、消息映射和网络实现由 Adapter 承担。
 */
public interface LlmCompletionPort {

    /**
     * 执行一次非流式 completion。
     */
    LlmResponse complete(LlmRequest request);

    /**
     * 打开一次惰性流式 completion。调用 subscribe 前不得连接供应商。
     */
    StreamCall stream(LlmRequest request);

    interface StreamCall {
        void subscribe(Consumer<LlmStreamEvent> onEvent,
                       Consumer<Throwable> onError,
                       Runnable onComplete);

        void cancel();
    }
}
