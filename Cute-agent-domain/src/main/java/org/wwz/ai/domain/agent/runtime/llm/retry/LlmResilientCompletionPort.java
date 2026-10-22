package org.wwz.ai.domain.agent.runtime.llm.retry;

import org.wwz.ai.domain.agent.runtime.llm.LlmCompletionPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequest;
import org.wwz.ai.domain.agent.runtime.llm.LlmResponse;
import org.wwz.ai.domain.agent.runtime.llm.LlmStreamEvent;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolCall;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 韧性装饰器：在 {@link LlmCompletionPort} 边界上补空补全重试。
 *
 * <p>为什么放在端口层而不是调用点：{@code LLM.java} 的所有出站调用（ask / askTool / struct_parse /
 * 空流兜底）都经由这一个端口，装饰一次即全路径生效，不必改动上千行的调用编排代码，
 * 也不会出现「某条路径忘了接」的问题。
 *
 * <h3>空补全重试</h3>
 * 上游返回成功但什么都没产出（无正文、无推理内容、无工具调用）。这类失败不会抛异常，
 * 所以重试链路看不见它；装饰器在流正常结束/非流式响应返回时检查产出，为空则补一次。
 * 判定「有产出」时把推理内容也算进去：一旦有增量推给过客户端，就不再重试，避免重复输出。
 *
 * <h3>配置</h3>
 * {@code REACTOR_LLM_EMPTY_COMPLETION_RETRY_ENABLED}：设为 false 关闭空补全重试。
 */
public final class LlmResilientCompletionPort implements LlmCompletionPort {

    private static final Logger LOG = Logger.getLogger(LlmResilientCompletionPort.class.getName());

    public static final String ENV_EMPTY_COMPLETION_RETRY_ENABLED = "REACTOR_LLM_EMPTY_COMPLETION_RETRY_ENABLED";

    private final LlmCompletionPort delegate;
    private final int emptyCompletionMaxRetries;

    /** 按环境变量/系统属性装配（生产入口）。 */
    public static LlmResilientCompletionPort wrap(LlmCompletionPort delegate) {
        Boolean emptyEnabled = LlmRetryPolicy.readBooleanSetting(ENV_EMPTY_COMPLETION_RETRY_ENABLED);
        int emptyRetries = Boolean.FALSE.equals(emptyEnabled) ? 0 : LlmEmptyCompletion.MAX_RETRIES;
        return new LlmResilientCompletionPort(delegate, emptyRetries);
    }

    public LlmResilientCompletionPort(LlmCompletionPort delegate, int emptyCompletionMaxRetries) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.emptyCompletionMaxRetries = Math.max(0, emptyCompletionMaxRetries);
    }

    public int emptyCompletionMaxRetries() {
        return emptyCompletionMaxRetries;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        LlmResponse response = delegate.complete(request);
        for (int retry = 0; retry < emptyCompletionMaxRetries && isEmptyResponse(response); retry++) {
            LOG.warning(() -> "LLM returned an empty completion, retrying once: " + describe(request));
            response = delegate.complete(request);
        }
        return response;
    }

    @Override
    public StreamCall stream(LlmRequest request) {
        return new ResilientStreamCall(request);
    }

    /**
     * 包装一次流式调用：空补全补发 + 取消传播。
     *
     * <p>注意：重试链路（{@code LlmRequestRetry.callAsync}）在外层为每次尝试重新调用
     * {@code stream(request)}，因此这里只需要管「单次尝试内部」的补发，不与外层重试预算冲突。
     */
    private final class ResilientStreamCall implements StreamCall {

        private final LlmRequest request;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        /** 本次尝试是否已终态（完成或失败）。用于防止重复回调与补发后的重复收口。 */
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicInteger emptyRetries = new AtomicInteger();
        private final AtomicBoolean outputSeen = new AtomicBoolean();

        private volatile StreamCall activeCall;
        private volatile Consumer<LlmStreamEvent> eventConsumer;
        private volatile Consumer<Throwable> errorConsumer;
        private volatile Runnable completeConsumer;

        private ResilientStreamCall(LlmRequest request) {
            this.request = request;
        }

        @Override
        public void subscribe(Consumer<LlmStreamEvent> onEvent,
                              Consumer<Throwable> onError,
                              Runnable onComplete) {
            this.eventConsumer = onEvent;
            this.errorConsumer = onError;
            this.completeConsumer = onComplete;
            openAttempt();
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            StreamCall call = activeCall;
            if (call == null) {
                return;
            }
            try {
                call.cancel();
            } catch (Exception cancelFailure) {
                LOG.log(Level.FINE, "LLM stream cancel failed", cancelFailure);
            }
        }

        private void openAttempt() {
            if (cancelled.get()) {
                return;
            }
            outputSeen.set(false);
            finished.set(false);
            StreamCall call = delegate.stream(request);
            activeCall = call;
            call.subscribe(this::onEvent, this::onError, this::onComplete);
        }

        private void onEvent(LlmStreamEvent event) {
            if (cancelled.get() || finished.get()) {
                return;
            }
            if (isMeaningful(event)) {
                outputSeen.set(true);
            }
            Consumer<LlmStreamEvent> consumer = eventConsumer;
            if (consumer == null) {
                return;
            }
            try {
                consumer.accept(event);
            } catch (Throwable consumerFailure) {
                LOG.log(Level.FINE, "LLM stream event consumer failed", consumerFailure);
            }
        }

        private void onError(Throwable error) {
            if (cancelled.get() || finished.getAndSet(true)) {
                return;
            }
            Consumer<Throwable> consumer = errorConsumer;
            if (consumer != null) {
                consumer.accept(error);
            }
        }

        private void onComplete() {
            if (cancelled.get() || finished.get()) {
                return;
            }
            if (!outputSeen.get() && emptyRetries.get() < emptyCompletionMaxRetries) {
                emptyRetries.incrementAndGet();
                LOG.warning(() -> "LLM stream completed without any output, retrying once: " + describe(request));
                openAttempt();
                return;
            }
            finished.set(true);
            Runnable consumer = completeConsumer;
            if (consumer != null) {
                consumer.run();
            }
        }
    }

    /**
     * 流事件是否算「有产出」。
     *
     * <p>usage / finishReason 单独出现不算产出：只有它们而没有任何正文的回合对用户仍是空的。
     * 推理内容算产出——它可能已经推给客户端，重试会导致重复输出。
     */
    private static boolean isMeaningful(LlmStreamEvent event) {
        if (event == null) {
            return false;
        }
        return isNotBlank(event.getContent())
                || isNotBlank(event.getReasoningContent())
                || hasToolCalls(event.getToolCalls());
    }

    private static boolean isEmptyResponse(LlmResponse response) {
        if (response == null) {
            return true;
        }
        return !isNotBlank(response.getContent())
                && !isNotBlank(response.getReasoningContent())
                && !hasToolCalls(response.getToolCalls());
    }

    private static boolean hasToolCalls(List<LlmToolCall> toolCalls) {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String describe(LlmRequest request) {
        if (request == null) {
            return "model=unknown";
        }
        return "model=" + request.getModel() + ", session=" + request.getSessionId();
    }
}
