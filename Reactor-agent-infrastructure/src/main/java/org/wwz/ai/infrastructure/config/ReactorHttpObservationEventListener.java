package org.wwz.ai.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Response;

import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * 记录远端调用耗时、连接失败、超时和供应商 429。
 */
final class ReactorHttpObservationEventListener extends EventListener {

    private final MeterRegistry registry;
    private Timer.Sample sample;

    private ReactorHttpObservationEventListener(MeterRegistry registry) {
        this.registry = registry;
    }

    static EventListener.Factory factory(MeterRegistry registry) {
        return call -> new ReactorHttpObservationEventListener(registry);
    }

    @Override
    public void callStart(Call call) {
        sample = Timer.start(registry);
    }

    @Override
    public void responseHeadersEnd(Call call, Response response) {
        if (response.code() == 429) {
            Counter.builder("reactor.http.rate_limit")
                    .tag("host", host(call))
                    .register(registry)
                    .increment();
        }
    }

    @Override
    public void callEnd(Call call) {
        recordDuration(call, "success");
    }

    @Override
    public void callFailed(Call call, IOException exception) {
        String outcome = exception instanceof InterruptedIOException ? "timeout" : "failure";
        Counter.builder("reactor.http.failures")
                .tag("host", host(call))
                .tag("outcome", outcome)
                .register(registry)
                .increment();
        recordDuration(call, outcome);
    }

    private void recordDuration(Call call, String outcome) {
        if (sample == null) {
            return;
        }
        sample.stop(Timer.builder("reactor.http.request.duration")
                .tag("host", host(call))
                .tag("outcome", outcome)
                .register(registry));
        sample = null;
    }

    private String host(Call call) {
        return call.request().url().host();
    }
}
