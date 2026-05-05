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
 * DataAgent 刷新链路测试骨架。
 */
public class DataAgentInitRunnerRefreshTest {

    @Test
    public void shouldUseRefreshFlowWhenForceRefreshEnabled() throws Exception {
        DataAgentInitRunner runner = new DataAgentInitRunner();
        DataAgentConfig dataAgentConfig = buildDataAgentConfig(true, true, true);
        QdrantService qdrantService = Mockito.mock(QdrantService.class);
        ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);
        ColumnValueSyncService columnValueSyncService = Mockito.mock(ColumnValueSyncService.class);
        EmbeddingService embeddingService = Mockito.mock(EmbeddingService.class);
        Mockito.when(embeddingService.healthCheck()).thenReturn(true);

        ReflectionTestUtils.setField(runner, "dataAgentConfig", dataAgentConfig);
        ReflectionTestUtils.setField(runner, "qdrantService", qdrantService);
        ReflectionTestUtils.setField(runner, "chatModelInfoService", chatModelInfoService);
        ReflectionTestUtils.setField(runner, "columnValueSyncService", columnValueSyncService);
        ReflectionTestUtils.setField(runner, "embeddingService", embeddingService);

        runner.run();

        Mockito.verify(qdrantService).recreateCosineCollection("reactor_model_schema", 1024);
        Mockito.verify(columnValueSyncService).recreateColumnValueIndex();
        Mockito.verify(chatModelInfoService).refreshModelInfo(dataAgentConfig);
        Mockito.verify(chatModelInfoService, Mockito.never()).initModelInfo(dataAgentConfig);
    }

    @Test
    public void shouldDegradeQdrantOnRegularStartupWhenEmbeddingUnavailable() throws Exception {
        DataAgentInitRunner runner = new DataAgentInitRunner();
        DataAgentConfig dataAgentConfig = buildDataAgentConfig(false, true, false);
        QdrantService qdrantService = Mockito.mock(QdrantService.class);
        ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);
        ColumnValueSyncService columnValueSyncService = Mockito.mock(ColumnValueSyncService.class);
        EmbeddingService embeddingService = Mockito.mock(EmbeddingService.class);
        Mockito.when(embeddingService.healthCheck()).thenReturn(false);

        ReflectionTestUtils.setField(runner, "dataAgentConfig", dataAgentConfig);
        ReflectionTestUtils.setField(runner, "qdrantService", qdrantService);
        ReflectionTestUtils.setField(runner, "chatModelInfoService", chatModelInfoService);
        ReflectionTestUtils.setField(runner, "columnValueSyncService", columnValueSyncService);
        ReflectionTestUtils.setField(runner, "embeddingService", embeddingService);

        runner.run();

        Assert.assertFalse(dataAgentConfig.getQdrantConfig().getEnable());
        Mockito.verify(chatModelInfoService).initModelInfo(dataAgentConfig);
        Mockito.verifyNoInteractions(qdrantService);
        Mockito.verifyNoInteractions(columnValueSyncService);
    }

    @Test
    public void shouldFailFastWhenForceRefreshQdrantCapabilityCheckFails() throws Exception {
        DataAgentInitRunner runner = new DataAgentInitRunner();
        DataAgentConfig dataAgentConfig = buildDataAgentConfig(true, true, false);
        QdrantService qdrantService = Mockito.mock(QdrantService.class);
        ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);
        ColumnValueSyncService columnValueSyncService = Mockito.mock(ColumnValueSyncService.class);
        EmbeddingService embeddingService = Mockito.mock(EmbeddingService.class);
        Mockito.when(embeddingService.healthCheck()).thenReturn(false);

        ReflectionTestUtils.setField(runner, "dataAgentConfig", dataAgentConfig);
        ReflectionTestUtils.setField(runner, "qdrantService", qdrantService);
        ReflectionTestUtils.setField(runner, "chatModelInfoService", chatModelInfoService);
        ReflectionTestUtils.setField(runner, "columnValueSyncService", columnValueSyncService);
        ReflectionTestUtils.setField(runner, "embeddingService", embeddingService);

        IllegalStateException exception = Assert.assertThrows(IllegalStateException.class, runner::run);

        Assert.assertEquals("共享文本向量代理不可用", exception.getMessage());
        Mockito.verifyNoInteractions(chatModelInfoService);
        Mockito.verifyNoInteractions(qdrantService);
        Mockito.verifyNoInteractions(columnValueSyncService);
    }

    @Test
    public void shouldSkipOptionalCapabilitiesWhenNestedConfigsAreAbsent() throws Exception {
        DataAgentInitRunner runner = new DataAgentInitRunner();
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);
        QdrantService qdrantService = Mockito.mock(QdrantService.class);
        ColumnValueSyncService columnValueSyncService = Mockito.mock(ColumnValueSyncService.class);
        EmbeddingService embeddingService = Mockito.mock(EmbeddingService.class);

        ReflectionTestUtils.setField(runner, "dataAgentConfig", dataAgentConfig);
        ReflectionTestUtils.setField(runner, "qdrantService", qdrantService);
        ReflectionTestUtils.setField(runner, "chatModelInfoService", chatModelInfoService);
        ReflectionTestUtils.setField(runner, "columnValueSyncService", columnValueSyncService);
        ReflectionTestUtils.setField(runner, "embeddingService", embeddingService);

        runner.run();

        Mockito.verify(chatModelInfoService).initModelInfo(dataAgentConfig);
        Mockito.verifyNoInteractions(qdrantService);
        Mockito.verifyNoInteractions(columnValueSyncService);
    }

    private DataAgentConfig buildDataAgentConfig(boolean forceRefresh, boolean qdrantEnabled, boolean esEnabled) {
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        QdrantConfig qdrantConfig = new QdrantConfig();
        qdrantConfig.setEnable(qdrantEnabled);
        EsConfig esConfig = new EsConfig();
        esConfig.setEnable(esEnabled);
        dataAgentConfig.setForceRefresh(forceRefresh);
        dataAgentConfig.setQdrantConfig(qdrantConfig);
        dataAgentConfig.setEsConfig(esConfig);
        return dataAgentConfig;
    }
}
