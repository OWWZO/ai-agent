package org.wwz.ai.domain.agent.runtime.llm;

import java.util.concurrent.CancellationException;

/**
 * 用户停止导致的模型调用中止。携带已可见的部分正文，供记忆保留，且不得当成失败重试。
 */
public class LlmCancelledException extends CancellationException {

    private final String partialContent;

    public LlmCancelledException(String reason, String partialContent) {
        super("LLM call aborted: " + (reason == null || reason.isBlank() ? "user_stop" : reason));
        this.partialContent = partialContent == null || partialContent.isBlank() ? null : partialContent;
    }

    public String getPartialContent() {
        return partialContent;
    }

    public static boolean isCancellation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CancellationException || current instanceof InterruptedException) {
                return true;
            }
            Throwable next = current.getCause();
            current = next == current ? null : next;
        }
        return false;
    }

    public static String partialContentOf(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof LlmCancelledException cancelled && cancelled.partialContent != null) {
                return cancelled.partialContent;
            }
            Throwable next = current.getCause();
            current = next == current ? null : next;
        }
        return null;
    }
}
