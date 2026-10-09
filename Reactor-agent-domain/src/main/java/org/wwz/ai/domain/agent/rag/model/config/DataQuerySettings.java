package org.wwz.ai.domain.agent.rag.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** Data Query business settings. JDBC credentials and search-client endpoints stay in Infrastructure. */
@Data
@Component
@ConfigurationProperties(prefix = "autobots.data-agent")
public class DataQuerySettings {

    public static final String SCHEMA_COLLECTION_NAME = "reactor_model_schema";

    private String agentUrl;
    private Boolean forceRefresh = false;
    private List<DataQueryModelConfig> modelList;
    private VectorRecallSettings qdrantConfig = new VectorRecallSettings();
    private ColumnValueRecallSettings esConfig = new ColumnValueRecallSettings();
    private DataQueryDatabaseSettings dbConfig = new DataQueryDatabaseSettings();

    public List<DataQueryModelConfig> getModelList() {
        if (modelList != null) {
            for (DataQueryModelConfig model : modelList) {
                if (model != null && (model.getBusinessPrompt() == null || model.getBusinessPrompt().isBlank())) {
                    String prompt = DataQueryModelPrompts.businessPromptFor(model.getId());
                    if (prompt != null) {
                        model.setBusinessPrompt(prompt);
                    }
                }
            }
        }
        return modelList;
    }

    public VectorRecallSettings getQdrantConfig() {
        if (qdrantConfig == null) {
            qdrantConfig = new VectorRecallSettings();
        }
        return qdrantConfig;
    }

    public ColumnValueRecallSettings getEsConfig() {
        if (esConfig == null) {
            esConfig = new ColumnValueRecallSettings();
        }
        return esConfig;
    }

    public DataQueryDatabaseSettings getDbConfig() {
        if (dbConfig == null) {
            dbConfig = new DataQueryDatabaseSettings();
        }
        return dbConfig;
    }
}
