package org.wwz.ai.infrastructure.dataquery.jdbc.connection;



import lombok.Data;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 连接池运行时容器。
 *
 * <p>除数据源外同时保存匹配的方言、元数据目录和刷新时间，使连接池缓存命中后仍能
 * 复用同一套 SQL/元数据策略。它是基础设施内部对象，不作为 HTTP 或领域契约暴露。</p>
 */
@Data
public class DatasourceWrapper implements AutoCloseable {

    private DataSource dataSource;

    private JdbcDialect jdbcDialect;

    private JdbcCatalog catalog;

    private Long freshTime;

    private final AtomicInteger leases = new AtomicInteger();
    private final AtomicBoolean retired = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean closeWhenReleased;

    void closeWhenReleased() {
        closeWhenReleased = true;
    }

    void acquire() {
        if (retired.get() || closed.get()) {
            throw new IllegalStateException("Datasource wrapper is no longer available");
        }
        leases.incrementAndGet();
        if (retired.get() || closed.get()) {
            release();
            throw new IllegalStateException("Datasource wrapper is no longer available");
        }
    }

    void retire() {
        retired.set(true);
        closeIfUnused();
    }

    @Override
    public void close() {
        if (closed.get()) {
            return;
        }
        if (closeWhenReleased) {
            retired.set(true);
        }
        if (leases.get() == 0) {
            // 直接由工厂创建、尚未交给连接池管理的 wrapper 也必须支持安全关闭。
            retired.set(true);
            closeIfUnused();
            return;
        }
        release();
    }

    private void release() {
        int remaining = leases.decrementAndGet();
        if (remaining < 0) {
            leases.incrementAndGet();
            throw new IllegalStateException("Datasource wrapper closed more than once");
        }
        closeIfUnused();
    }

    private void closeIfUnused() {
        if (retired.get() && leases.get() == 0 && closed.compareAndSet(false, true)) {
            if (dataSource instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    throw new IllegalStateException("关闭 JDBC 数据源失败", e);
                }
            }
        }
    }
}
