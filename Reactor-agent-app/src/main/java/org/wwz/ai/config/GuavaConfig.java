package org.wwz.ai.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/** 应用级轻量缓存装配。 */
@Configuration
public class GuavaConfig {

    @Bean(name = "cache")
    public Cache<String, String> cache() {
        // 该缓存按写入后三秒过期，只适合短时去重/临时结果，不承担持久化或一致性职责。
        return CacheBuilder.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .build();
    }

}
