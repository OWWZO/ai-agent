package org.wwz.ai.config.reactor.data;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.EsConfig;
import org.wwz.ai.domain.agent.reactor.util.ESUtil;

@Slf4j
@Configuration
@ConditionalOnBean(DataAgentConfig.class)
public class Es7HighLevelClientConfig {

    @Autowired
    DataAgentConfig dataAgentConfig;

    @Bean(name = "dataAgentEsClient")
    public RestHighLevelClient dataAgentEsClient() {
        EsConfig esConfig = dataAgentConfig.getEsConfig();
        if (!Boolean.TRUE.equals(esConfig.getEnable())) {
            log.info("ES 能力未启用，跳过 dataAgentEsClient 装配");
            return null;
        }
        if (StringUtils.isBlank(esConfig.getHost())) {
            log.warn("ES 能力已启用但 host 为空，跳过 dataAgentEsClient 装配");
            return null;
        }
        return ESUtil.buildRestClient(
                esConfig.getHost(),
                esConfig.getUser(),
                esConfig.getPassword(),
                esConfig.getApiKey(),
                30000,
                esConfig.getScheme()
        );
    }


}
