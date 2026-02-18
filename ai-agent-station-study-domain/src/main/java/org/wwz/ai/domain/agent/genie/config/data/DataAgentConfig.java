package org.wwz.ai.domain.agent.genie.config.data;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "autobots.data-agent")
public class DataAgentConfig {
    private String agentUrl;
    private List<DataAgentModelConfig> modelList;
    private QdrantConfig qdrantConfig;
    private DbConfig dbConfig;
    private EsConfig esConfig;
}
