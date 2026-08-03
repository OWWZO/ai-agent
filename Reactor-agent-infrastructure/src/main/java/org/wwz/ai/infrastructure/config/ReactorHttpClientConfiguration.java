package org.wwz.ai.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 统一装配基础设施侧 OkHttp 资源。
 *
 * <p>各适配器可以基于共享客户端创建带独立超时的轻量配置副本，但连接池和
 * Dispatcher 始终复用，避免虚拟线程扩容时为每个请求创建连接资源。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ReactorHttpClientConfiguration {

    @Bean(destroyMethod = "evictAll")
    public ConnectionPool reactorHttpConnectionPool(
            @Value("${autobots.http.max-idle-connections:32}") int maxIdleConnections,
            @Value("${autobots.http.keep-alive-minutes:5}") long keepAliveMinutes) {
        return new ConnectionPool(maxIdleConnections, keepAliveMinutes, TimeUnit.MINUTES);
    }

    @Bean
    public Dispatcher reactorHttpDispatcher(
            @Value("${autobots.http.max-requests:128}") int maxRequests,
            @Value("${autobots.http.max-requests-per-host:32}") int maxRequestsPerHost) {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(Math.max(1, maxRequests));
        dispatcher.setMaxRequestsPerHost(Math.max(1, maxRequestsPerHost));
        return dispatcher;
    }

    @Bean
    public OkHttpClient reactorHttpClient(ConnectionPool connectionPool,
                                          Dispatcher dispatcher,
                                          ObjectProvider<MeterRegistry> meterRegistryProvider) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectionPool(connectionPool)
                .dispatcher(dispatcher);
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            builder.eventListenerFactory(ReactorHttpObservationEventListener.factory(meterRegistry));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public ReactorHttpClientLifecycle reactorHttpClientLifecycle(Dispatcher dispatcher,
                                                                 ConnectionPool connectionPool) {
        return new ReactorHttpClientLifecycle(dispatcher, connectionPool);
    }

    /** 暴露 Dispatcher 和连接池的容量状态，供 Actuator/Micrometer 采集。 */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public ReactorHttpClientMetrics reactorHttpClientMetrics(MeterRegistry meterRegistry,
                                                              Dispatcher dispatcher,
                                                              ConnectionPool connectionPool) {
        return new ReactorHttpClientMetrics(meterRegistry, dispatcher, connectionPool);
    }
}
