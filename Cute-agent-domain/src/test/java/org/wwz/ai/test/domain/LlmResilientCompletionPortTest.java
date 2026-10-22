package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.llm.LlmCompletionPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequest;
import org.wwz.ai.domain.agent.runtime.llm.LlmResponse;
import org.wwz.ai.domain.agent.runtime.llm.LlmStreamEvent;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmResilientCompletionPort;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 端口层韧性装饰器验证：空补全重试 + 取消传播。
 */
public class LlmResilientCompletionPortTest {

    @Test
    public void shouldRetryStreamOnceWhenNoOutputProduced() {
        FakePort delegate = new FakePort();
        delegate.streamEvents = List.of();
        LlmResilientCompletionPort port = new LlmResilientCompletionPort(delegate, 1);

        AtomicInteger completions = new AtomicInteger();
        port.stream(request()).subscribe(event -> {
        }, failure -> Assert.fail("不应失败：" + failure), completions::incrementAndGet);

        Assert.assertEquals("空补全应重开一次流", 2, delegate.streamOpens.get());
        Assert.assertEquals("补发成功后只应回调一次完成", 1, completions.get());
    }

    @Test
    public void shouldNotRetryStreamWhenContentArrived() {
        FakePort delegate = new FakePort();
        delegate.streamEvents = List.of(LlmStreamEvent.builder().content("hello").build());
        LlmResilientCompletionPort port = new LlmResilientCompletionPort(delegate, 1);

        AtomicInteger completions = new AtomicInteger();
        port.stream(request()).subscribe(event -> {
        }, failure -> Assert.fail("不应失败：" + failure), completions::incrementAndGet);

        Assert.assertEquals("有产出就不应补发", 1, delegate.streamOpens.get());
        Assert.assertEquals(1, completions.get());
    }

    @Test
    public void shouldNotRetryStreamWhenOnlyReasoningArrived() {
        FakePort delegate = new FakePort();
        delegate.streamEvents = List.of(LlmStreamEvent.builder().reasoningContent("thinking...").build());
        LlmResilientCompletionPort port = new LlmResilientCompletionPort(delegate, 1);

        port.stream(request()).subscribe(event -> {
        }, failure -> Assert.fail("不应失败：" + failure), () -> {
        });

        // 推理内容可能已推给客户端，重试会导致重复输出，因此不补发。
        Assert.assertEquals(1, delegate.streamOpens.get());
    }

    @Test
    public void shouldNotRetryWhenOnlyFinishReasonArrived() {
        FakePort delegate = new FakePort();
        delegate.streamEvents = List.of(LlmStreamEvent.builder().finishReason("stop").build());
        LlmResilientCompletionPort port = new LlmResilientCompletionPort(delegate, 1);

        port.stream(request()).subscribe(event -> {
        }, failure -> Assert.fail("不应失败：" + failure), () -> {
        });

        Assert.assertEquals("只有 finishReason 不算产出，应补发一次", 2, delegate.streamOpens.get());
    }

    @Test
    public void shouldStopAfterConfiguredEmptyRetries() {
        FakePort delegate = new FakePort();
        delegate.streamEvents = List.of();
        LlmResilientCompletionPort port = new LlmResilientCompletionPort(delegate, 2);

        port.stream(request()).subscribe(event -> {
        }, failure -> Assert.fail("不应失败：" + failure), () -> {
        });

        Assert.assertEquals("上限为 2 时最多开 3 次流（首次 + 2 次补发）", 3, delegate.streamOpens.get());
    }

    @Test
    public void shouldNotRetryNorCompleteAfterCancel() throws Exception {
        FakePort delegate = new FakePort();
        delegate.holdStreamOpen = true;
        LlmResilientCompletionPort port = new LlmResilientCompletionPort(delegate, 1);

        AtomicInteger completions = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();
        LlmCompletionPort.StreamCall call = port.stream(request());
        call.subscribe(event -> {
        }, error::set, completions::incrementAndGet);
        call.cancel();

        Thread.sleep(50L);
        Assert.assertEquals("取消后底层流应被取消", 1, delegate.cancels.get());
        Assert.assertEquals("取消后不应再开新流", 1, delegate.streamOpens.get());
        Assert.assertEquals("取消后不应回调完成", 0, completions.get());
        Assert.assertNull("取消后不应报错", error.get());
    }

    @Test
    public void shouldRetryEmptyNonStreamingCompletionOnce() {
        FakePort delegate = new FakePort();
        delegate.completeReturnsEmpty = true;
        LlmResilientCompletionPort port = new LlmResilientCompletionPort(delegate, 1);

        LlmResponse response = port.complete(request());

        Assert.assertEquals("空响应应补发一次", 2, delegate.completeCalls.get());
        Assert.assertNull(response.getContent());
    }

    @Test
    public void shouldNotRetryNonEmptyCompletion() {
        FakePort delegate = new FakePort();
        LlmResilientCompletionPort port = new LlmResilientCompletionPort(delegate, 1);

        LlmResponse response = port.complete(request());

        Assert.assertEquals(1, delegate.completeCalls.get());
        Assert.assertEquals("hello", response.getContent());
    }

    @Test
    public void shouldDisableEmptyRetryWhenConfiguredToZero() {
        FakePort delegate = new FakePort();
        delegate.streamEvents = List.of();
        LlmResilientCompletionPort port = new LlmResilientCompletionPort(delegate, 0);

        port.stream(request()).subscribe(event -> {
        }, failure -> Assert.fail("不应失败：" + failure), () -> {
        });

        Assert.assertEquals("上限为 0 时不应补发", 1, delegate.streamOpens.get());
    }

    private static LlmRequest request() {
        return LlmRequest.builder().model("gpt-6-luna").sessionId("session-test").build();
    }

    /** 可编程的假端口：控制是否产出事件、是否挂住不结束、是否返回空响应。 */
    private static final class FakePort implements LlmCompletionPort {

        final AtomicInteger streamOpens = new AtomicInteger();
        final AtomicInteger completeCalls = new AtomicInteger();
        final AtomicInteger cancels = new AtomicInteger();
        volatile List<LlmStreamEvent> streamEvents = List.of();
        volatile boolean holdStreamOpen = false;
        volatile boolean completeReturnsEmpty = false;

        @Override
        public LlmResponse complete(LlmRequest request) {
            completeCalls.incrementAndGet();
            return completeReturnsEmpty
                    ? LlmResponse.builder().build()
                    : LlmResponse.builder().content("hello").build();
        }

        @Override
        public StreamCall stream(LlmRequest request) {
            streamOpens.incrementAndGet();
            List<LlmStreamEvent> events = streamEvents;
            boolean hold = holdStreamOpen;
            return new StreamCall() {
                private final AtomicBoolean cancelled = new AtomicBoolean();

                @Override
                public void subscribe(Consumer<LlmStreamEvent> onEvent,
                                      Consumer<Throwable> onError,
                                      Runnable onComplete) {
                    for (LlmStreamEvent event : events) {
                        if (cancelled.get()) {
                            return;
                        }
                        onEvent.accept(event);
                    }
                    if (!hold && !cancelled.get()) {
                        onComplete.run();
                    }
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                    cancels.incrementAndGet();
                }
            };
        }
    }
}
