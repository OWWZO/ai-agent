package org.wwz.ai.infrastructure.config;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

/**
 * Spring 停止时关闭 OkHttp Dispatcher，避免连接池线程阻止应用退出。
 */
public final class ReactorHttpClientLifecycle implements AutoCloseable {

    private final Dispatcher dispatcher;
    private final ConnectionPool connectionPool;

    public ReactorHttpClientLifecycle(Dispatcher dispatcher, ConnectionPool connectionPool) {
        this.dispatcher = dispatcher;
        this.connectionPool = connectionPool;
    }

    @Override
    public void close() {
        dispatcher.cancelAll();
        dispatcher.executorService().shutdown();
        connectionPool.evictAll();
    }
}
