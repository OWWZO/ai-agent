package org.wwz.ai.config.reactor;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConstants;
import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.config.data.EsConfig;
import org.wwz.ai.domain.agent.reactor.config.data.QdrantConfig;
import org.wwz.ai.domain.agent.reactor.service.ChatModelInfoService;
import org.wwz.ai.domain.agent.reactor.service.ColumnValueSyncService;
import org.wwz.ai.domain.agent.reactor.service.EmbeddingService;
import org.wwz.ai.domain.agent.reactor.service.QdrantService;
import org.wwz.ai.infrastructure.dataquery.jdbc.connection.JdbcConnectionFactory;
import org.wwz.ai.infrastructure.dataquery.util.JdbcUtils;

import java.sql.Connection;

@Slf4j
@Component
public class DataAgentInitRunner implements CommandLineRunner {

    @Autowired
    private DataAgentConfig dataAgentConfig;
    @Autowired
    private QdrantService qdrantService;
    @Autowired
    private ChatModelInfoService chatModelInfoService;
    @Autowired
    private ColumnValueSyncService columnValueSyncService;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired(required = false)
    private SkillRegistry skillRegistry;


    @Override
    public void run(String... args) throws Exception {
        log.info("dataAgent config:{}", dataAgentConfig);
        boolean forceRefresh = Boolean.TRUE.equals(dataAgentConfig.getForceRefresh());
        
        // H2数据库初始化：如果配置为H2且存在初始化脚本，则执行初始化
        DbConfig dbConfig = dataAgentConfig.getDbConfig();
        if (dbConfig != null && "h2".equalsIgnoreCase(dbConfig.getType())) {
            try (Connection connection = JdbcConnectionFactory.getConnection(JdbcUtils.parseJdbcConnectionConfig(dbConfig)).getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
                // 尝试执行data.sql，如果文件不存在或出错不影响启动
                try {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/data.sql"));
                } catch (Exception e) {
                   log.warn("Execute data.sql failed or file not found, skipping data init: {}", e.getMessage());
                }
                log.info("H2 database initialized with schema.sql");
            } catch (Exception e) {
                log.error("Failed to initialize H2 database", e);
                // 不抛出异常，避免影响主流程，但可能会导致后续查询失败
            }
        }

        prepareQdrantCapability(forceRefresh);
        prepareEsCapability(forceRefresh);

        try {
            if (forceRefresh) {
                chatModelInfoService.refreshModelInfo(dataAgentConfig);
            } else {
                chatModelInfoService.initModelInfo(dataAgentConfig);
            }
        } catch (Exception e) {
            if (forceRefresh) {
                log.error("强制刷新失败，终止启动流程", e);
                throw e;
            }
            log.error("Failed to init model info", e);
        }

        if (skillRegistry != null) {
            try {
                skillRegistry.refresh();
                log.info("skill registry init success, loaded skills={}", skillRegistry.listSkills().size());
            } catch (Exception e) {
                log.error("Failed to init skill registry", e);
            }
        }
    }

    private void prepareQdrantCapability(boolean forceRefresh) throws Exception {
        QdrantConfig qdrantConfig = dataAgentConfig.getQdrantConfig();
        if (!Boolean.TRUE.equals(qdrantConfig.getEnable())) {
            return;
        }
        try {
            if (!embeddingService.healthCheck()) {
                throw new IllegalStateException("共享文本向量代理不可用");
            }
            int dimension = resolveEmbeddingDimension();
            if (forceRefresh) {
                qdrantService.recreateCosineCollection(DataAgentConstants.SCHEMA_COLLECTION_NAME, dimension);
            } else {
                qdrantService.createCosineCollection(DataAgentConstants.SCHEMA_COLLECTION_NAME, dimension);
            }
            log.info("qdrant collection init success");
        } catch (Exception e) {
            handleCapabilityFailure("qdrant", forceRefresh, e);
            qdrantConfig.setEnable(false);
            if (forceRefresh) {
                throw e;
            }
        }
    }

    private void prepareEsCapability(boolean forceRefresh) throws Exception {
        EsConfig esConfig = dataAgentConfig.getEsConfig();
        if (!Boolean.TRUE.equals(esConfig.getEnable())) {
            return;
        }
        try {
            if (forceRefresh) {
                columnValueSyncService.recreateColumnValueIndex();
            } else {
                columnValueSyncService.initColumnValueIndex();
            }
            log.info("column value es index init success");
        } catch (Exception e) {
            handleCapabilityFailure("es", forceRefresh, e);
            esConfig.setEnable(false);
            if (forceRefresh) {
                throw e;
            }
        }
    }

    private void handleCapabilityFailure(String capability, boolean forceRefresh, Exception e) {
        if (forceRefresh) {
            log.error("{} capability force-refresh failed", capability, e);
            return;
        }
        log.warn("{} capability degraded and disabled: {}", capability, e.getMessage(), e);
    }

    private int resolveEmbeddingDimension() {
        String dimension = System.getenv("TEXT_EMBEDDING_DIMENSION");
        if (StringUtils.isBlank(dimension)) {
            return 1024;
        }
        try {
            return Integer.parseInt(dimension);
        } catch (NumberFormatException e) {
            log.warn("TEXT_EMBEDDING_DIMENSION 非法，回退默认值 1024: {}", dimension);
            return 1024;
        }
    }
}
