package org.wwz.ai.infrastructure.dataquery.jdbc.connection;


import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcConnectionConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 按连接配置缓存 JDBC 数据源，并在配置时间戳变化时刷新。
 *
 * <p>读锁覆盖常规命中路径，写锁保护创建/替换路径，避免并发请求重复建立同一连接池。</p>
 */
@Slf4j
public class JdbcConnectionPools {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    private static JdbcConnectionPoolFactory jdbcConnectionPoolFactory;
    private final Map<String, DatasourceWrapper> pools;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private JdbcConnectionPools() {
        this.pools = new ConcurrentHashMap<>(DEFAULT_INITIAL_CAPACITY);
    }

    private static class SingletonHolder {
        private static final JdbcConnectionPools INSTANCE;

        static {
            try {
                jdbcConnectionPoolFactory = new JdbcConnectionPoolFactory();
                INSTANCE = new JdbcConnectionPools();
                log.info("JdbcConnectionPools 初始化完成");
            } catch (Exception e) {
                log.error("JdbcConnectionPools 初始化失败", e);
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    public static JdbcConnectionPools getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public DatasourceWrapper getOrCreateConnectionPool(JdbcConnectionConfig config) {

        if (!config.isCachePools()) {
            // 明确关闭缓存时每次创建独立数据源，调用方应承担其生命周期；缓存路径才进入读写锁保护。
            log.info("创建无缓存的数据源连接池");
            return createNewDatasource(config);
        }

        String poolId = config.getKey();

        // 先尝试读锁获取，连接池未变化时不阻塞其它读请求。
        lock.readLock().lock();
        try {
            DatasourceWrapper existingWrapper = pools.get(poolId);
            if (existingWrapper != null && noNeedsRefresh(existingWrapper, config.getFreshTimestamp())) {
                log.info("从缓存获取连接池 poolId {}", poolId);
                return existingWrapper;
            }
        } finally {
            lock.readLock().unlock();
        }

        // 读锁未命中后升级为写锁；再次检查是必要的，防止等待期间已有线程完成创建。
        lock.writeLock().lock();
        try {
            // 双重检查避免多个线程在读锁 miss 后重复创建同一个连接池。
            DatasourceWrapper existingWrapper = pools.get(poolId);
            if (existingWrapper != null && noNeedsRefresh(existingWrapper, config.getFreshTimestamp())) {
                log.info("再次从缓存获取连接池 poolId {}", poolId);
                return existingWrapper;
            }

            log.info("连接池需要刷新或创建，poolId: {}", poolId);
            return refreshDatasource(config);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean noNeedsRefresh(DatasourceWrapper wrapper, Long freshTimestamp) {
        if (freshTimestamp == null) {
            return true;
        }

        Long oldFreshTime = wrapper.getFreshTime();
        boolean needRefresh = oldFreshTime == null || oldFreshTime < freshTimestamp;

        if (needRefresh) {
            log.info("数据连接需要更新. old-time: {}, new-time: {}", oldFreshTime, freshTimestamp);
        }

        return !needRefresh;
    }


    private DatasourceWrapper refreshDatasource(JdbcConnectionConfig config) {
        String poolId = config.getKey();

        // 创建新的数据源
        DatasourceWrapper newWrapper = createNewDatasource(config);
        pools.put(poolId, newWrapper);

        log.info("数据源刷新完成 poolId {}", poolId);
        return newWrapper;
    }

    private DatasourceWrapper createNewDatasource(JdbcConnectionConfig config) {
        try {
            return jdbcConnectionPoolFactory.createPooledDatasource(config);
        } catch (Exception e) {
            log.error("创建数据源失败, config: {}", config, e);
            throw new RuntimeException("创建数据源失败", e);
        }
    }
}
