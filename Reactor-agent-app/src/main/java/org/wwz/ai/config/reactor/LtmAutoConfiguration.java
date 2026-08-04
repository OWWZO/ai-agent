package org.wwz.ai.config.reactor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import jakarta.annotation.PostConstruct;
import org.wwz.ai.domain.agent.memory.ltm.BackgroundReviewService;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;
import org.wwz.ai.domain.agent.memory.ltm.LtmServices;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushService;
import org.wwz.ai.infrastructure.dao.reactor.ILtmCuratedEntryDao;
import org.wwz.ai.infrastructure.memory.BackgroundReviewServiceImpl;
import org.wwz.ai.infrastructure.memory.BuiltinMemoryProvider;
import org.wwz.ai.infrastructure.memory.InMemoryCuratedMemoryStore;
import org.wwz.ai.infrastructure.memory.MemoryFlushServiceImpl;
import org.wwz.ai.infrastructure.memory.MyBatisCuratedMemoryStore;
import org.wwz.ai.infrastructure.memory.holographic.HolographicMemoryProvider;
import org.wwz.ai.infrastructure.memory.openviking.OpenVikingMemoryProvider;

/**
 * LTM 装配：创建期解析 DAO（避免 {@code @ConditionalOnBean} 时序导致误用内存 Store）。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LtmProperties.class)
@ConditionalOnProperty(prefix = "memory.ltm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LtmAutoConfiguration {

    private final LtmProperties properties;
    private final ObjectProvider<MemoryFlushServiceImpl> memoryFlushServiceProvider;
    private final ObjectProvider<BackgroundReviewServiceImpl> backgroundReviewServiceProvider;

    public LtmAutoConfiguration(LtmProperties properties,
                                ObjectProvider<MemoryFlushServiceImpl> memoryFlushServiceProvider,
                                ObjectProvider<BackgroundReviewServiceImpl> backgroundReviewServiceProvider) {
        this.properties = properties;
        this.memoryFlushServiceProvider = memoryFlushServiceProvider;
        this.backgroundReviewServiceProvider = backgroundReviewServiceProvider;
    }

    @PostConstruct
    public void applyRuntimeConfig() {
        MemoryFlushServiceImpl flush = memoryFlushServiceProvider.getIfAvailable();
        if (flush != null) {
            flush.configure(
                    properties.getFlush().isEnabled(),
                    properties.getFlushMinTurns(),
                    properties.getFlush().getMaterialMaxMessages(),
                    properties.getFlush().getMaterialMaxCharsPerMsg(),
                    properties.getFlush().getTimeoutSeconds());
            flush.configureMaxSteps(5);
        }
        BackgroundReviewServiceImpl review = backgroundReviewServiceProvider.getIfAvailable();
        if (review != null) {
            review.configure(
                    properties.getBackgroundReview().isEnabled(),
                    properties.getBackgroundReview().getNudgeInterval(),
                    properties.getBackgroundReview().getTimeoutSeconds(),
                    6);
        }
        // 运行时定位：避免 ReactorRuntimeDependencies 构建过早快照到 null
        LtmServices.bind(
                backgroundReviewServiceProvider.getIfAvailable(),
                memoryFlushServiceProvider.getIfAvailable());
        log.info("LTM flush.enabled={} minTurns={} review.enabled={} interval={} reviewBean={} flushBean={}",
                properties.getFlush().isEnabled(),
                properties.getFlushMinTurns(),
                properties.getBackgroundReview().isEnabled(),
                properties.getBackgroundReview().getNudgeInterval(),
                review != null ? review.getClass().getSimpleName() : "null",
                flush != null ? flush.getClass().getSimpleName() : "null");
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(CuratedMemoryStore.class)
    public CuratedMemoryStore curatedMemoryStore(ObjectProvider<ILtmCuratedEntryDao> curatedEntryDaoProvider,
                                                 LtmProperties properties) {
        ILtmCuratedEntryDao dao = curatedEntryDaoProvider.getIfAvailable();
        int curatedLimit = properties.getCurated().getCharLimit();
        int userLimit = properties.getCurated().getUserCharLimit();
        if (dao != null) {
            log.info("LTM curated store = MyBatisCuratedMemoryStore (persistent table ai_agent_ltm_curated_entry)");
            return new MyBatisCuratedMemoryStore(dao, curatedLimit, userLimit);
        }
        log.warn("LTM curated store = InMemoryCuratedMemoryStore (ILtmCuratedEntryDao missing; "
                + "memory tool success will NOT write MySQL). Check @Mapper scan and table migration.");
        return new InMemoryCuratedMemoryStore(curatedLimit, userLimit);
    }

    @Bean(destroyMethod = "shutdownAll")
    @ConditionalOnMissingBean(LtmManager.class)
    public LtmManager ltmManager(LtmProperties properties, CuratedMemoryStore curatedMemoryStore) {
        log.info("LTM manager store impl = {}", curatedMemoryStore.getClass().getSimpleName());
        LtmManager manager = new LtmManager(properties.getPrefetchTimeoutMs());
        manager.addProvider(new BuiltinMemoryProvider(curatedMemoryStore));
        String provider = properties.getProvider() == null ? "none" : properties.getProvider().trim().toLowerCase();
        if ("holographic".equals(provider)) {
            manager.addProvider(new HolographicMemoryProvider());
        } else if ("openviking".equals(provider)) {
            manager.addProvider(new OpenVikingMemoryProvider(properties.getOpenvikingEndpoint()));
        }
        return manager;
    }
}
