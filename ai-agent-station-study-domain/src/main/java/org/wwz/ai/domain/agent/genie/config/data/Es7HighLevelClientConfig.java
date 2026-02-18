package org.wwz.ai.domain.agent.genie.config.data;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.genie.util.ESUtil;

@Slf4j
@Configuration
public class Es7HighLevelClientConfig {

    @Autowired
    DataAgentConfig dataAgentConfig;

    @Bean(name = "dataAgentEsClient")
    public RestHighLevelClient dataAgentEsClient() {
        EsConfig esConfig = dataAgentConfig.getEsConfig();
        return ESUtil.buildRestClient(esConfig.getHost(), esConfig.getUser(), esConfig.getPassword(), 30000);
    }


}
