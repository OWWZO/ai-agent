package org.wwz.ai.infrastructure.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

/**
 * OkHttp 下游容量观测。
 */
public final class ReactorHttpClientMetrics {

    public ReactorHttpClientMetrics(MeterRegistry registry,
                                     Dispatcher dispatcher,
                                     ConnectionPool connectionPool) {
        Tags tags = Tags.of("client", "reactor-http");
        Gauge.builder("reactor.http.calls.running", dispatcher,
                        value -> value.runningCallsCount())
                .tags(tags)
                .description("OkHttp 当前运行中的同步和异步调用数")
                .register(registry);
        Gauge.builder("reactor.http.calls.queued", dispatcher,
                        value -> value.queuedCallsCount())
                .tags(tags)
                .description("OkHttp 等待 Dispatcher 调度的调用数")
                .register(registry);
        Gauge.builder("reactor.http.calls.max", dispatcher,
                        value -> value.getMaxRequests())
                .tags(tags)
                .description("OkHttp 全局最大运行调用数")
                .register(registry);
        Gauge.builder("reactor.http.calls.max_per_host", dispatcher,
                        value -> value.getMaxRequestsPerHost())
                .tags(tags)
                .description("OkHttp 单主机最大运行调用数")
                .register(registry);
        Gauge.builder("reactor.http.connections.total", connectionPool,
                        value -> value.connectionCount())
                .tags(tags)
                .description("OkHttp 连接池连接总数")
                .register(registry);
        Gauge.builder("reactor.http.connections.idle", connectionPool,
                        value -> value.idleConnectionCount())
                .tags(tags)
                .description("OkHttp 连接池空闲连接数")
                .register(registry);
    }
}
