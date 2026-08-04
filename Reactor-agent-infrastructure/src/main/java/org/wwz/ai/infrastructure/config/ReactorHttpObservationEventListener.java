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
        // 一个 listener 实例只对应一次 OkHttp Call，Timer.Sample 从 callStart 开始贯穿成功和失败路径。
        sample = Timer.start(registry);
    }

    @Override
    public void responseHeadersEnd(Call call, Response response) {
        // 429 在收到响应头时即可计数，不等待响应体读取完成，避免限流指标被慢 body 延迟。
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
        // InterruptedIOException 通常代表超时/中断，单独标记 outcome 便于与连接失败区分。
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
        // 成功和失败都只停止一次 timer；清空 sample 防止异常回调重复记时。
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
