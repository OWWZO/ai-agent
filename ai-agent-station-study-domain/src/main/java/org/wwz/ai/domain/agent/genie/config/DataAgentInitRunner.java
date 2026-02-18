package org.wwz.ai.domain.agent.genie.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.genie.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.genie.config.data.DataAgentConstants;
import org.wwz.ai.domain.agent.genie.config.data.EsConfig;
import org.wwz.ai.domain.agent.genie.config.data.QdrantConfig;
import org.wwz.ai.domain.agent.genie.service.ChatModelInfoService;
import org.wwz.ai.domain.agent.genie.service.ColumnValueSyncService;
import org.wwz.ai.domain.agent.genie.service.QdrantService;

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


    @Override
    public void run(String... args) throws Exception {
        log.info("dataAgent config:{}", dataAgentConfig);
        
        // H2数据库初始化：如果配置为H2且存在初始化脚本，则执行初始化
        /*
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
        */

        QdrantConfig qdrantConfig = dataAgentConfig.getQdrantConfig();
        if (qdrantConfig.getEnable()) {
            qdrantService.createCosineCollection(DataAgentConstants.SCHEMA_COLLECTION_NAME, 1024);
            log.info("qdrant collection init success");
        }
        EsConfig esConfig = dataAgentConfig.getEsConfig();
        if (esConfig.getEnable()) {
            columnValueSyncService.initColumnValueIndex();
            log.info("column value es index init success");
        }
        // chatModelInfoService.initModelInfo(dataAgentConfig);
    }
}
