package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.config.reactor.DataAgentInitRunner;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.EsConfig;
import org.wwz.ai.domain.agent.reactor.config.data.QdrantConfig;
import org.wwz.ai.domain.agent.reactor.service.ChatModelInfoService;
import org.wwz.ai.domain.agent.reactor.service.ColumnValueSyncService;
import org.wwz.ai.domain.agent.reactor.service.EmbeddingService;
import org.wwz.ai.domain.agent.reactor.service.QdrantService;

/**
 * 能力降级测试。
 */
public class DataAgentCapabilityDegradeTest {

    @Test
    public void shouldDisableEsWhenRegularStartupInitFails() throws Exception {
        DataAgentInitRunner runner = new DataAgentInitRunner();
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        QdrantConfig qdrantConfig = new QdrantConfig();
        qdrantConfig.setEnable(false);
        EsConfig esConfig = new EsConfig();
        esConfig.setEnable(true);
        dataAgentConfig.setQdrantConfig(qdrantConfig);
        dataAgentConfig.setEsConfig(esConfig);
        dataAgentConfig.setForceRefresh(false);

        ColumnValueSyncService columnValueSyncService = Mockito.mock(ColumnValueSyncService.class);
        Mockito.doThrow(new IllegalStateException("es init failed")).when(columnValueSyncService).initColumnValueIndex();

        ReflectionTestUtils.setField(runner, "dataAgentConfig", dataAgentConfig);
        ReflectionTestUtils.setField(runner, "qdrantService", Mockito.mock(QdrantService.class));
        ReflectionTestUtils.setField(runner, "chatModelInfoService", Mockito.mock(ChatModelInfoService.class));
        ReflectionTestUtils.setField(runner, "columnValueSyncService", columnValueSyncService);
        ReflectionTestUtils.setField(runner, "embeddingService", Mockito.mock(EmbeddingService.class));

        runner.run();

        Assert.assertFalse(dataAgentConfig.getEsConfig().getEnable());
    }
}
