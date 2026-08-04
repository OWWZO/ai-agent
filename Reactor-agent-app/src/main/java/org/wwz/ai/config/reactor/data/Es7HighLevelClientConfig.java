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

/**
 * 数据问数 Elasticsearch 客户端装配。
 *
 * <p>配置 Bean 仅在存在 {@link DataAgentConfig} 时参与装配，并根据 ES 开关和 host
 * 做能力级判断。未启用或配置不完整时返回空 Bean，调用方应依据配置开关决定是否
 * 使用 ES，而不是把缺少客户端当作索引为空。</p>
 */
@Slf4j
@Configuration
@ConditionalOnBean(DataAgentConfig.class)
public class Es7HighLevelClientConfig {

    @Autowired
    DataAgentConfig dataAgentConfig;

    @Bean(name = "dataAgentEsClient")
    public RestHighLevelClient dataAgentEsClient() {
        // 客户端是可选能力，先判断开关和地址，再创建带认证信息的远端连接。
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
