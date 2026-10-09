package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.application.agent.dataquery.initialization.DataAgentInitializationApplicationService;
import org.wwz.ai.domain.agent.rag.port.DataAgentInitializerPort;
import org.wwz.ai.domain.agent.rag.service.ChatModelInfoService;
import org.wwz.ai.domain.agent.rag.model.config.ColumnValueRecallSettings;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.domain.agent.rag.model.config.VectorRecallSettings;

import java.util.Optional;

public class DataAgentInitializationApplicationServiceTest {

    @Test
    public void shouldRefreshBothIndexesAndModelMetadataWhenForced() throws Exception {
        DataQuerySettings config = buildConfig(true, true, true);
        DataAgentInitializerPort initializerPort = Mockito.mock(DataAgentInitializerPort.class);
        ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);

        service(config, initializerPort, chatModelInfoService).initialize(768);

        Mockito.verify(initializerPort).initializeVectorIndex(true, 768);
        Mockito.verify(initializerPort).initializeColumnValueIndex(true);
        Mockito.verify(chatModelInfoService).refreshModelInfo(config);
        Mockito.verify(chatModelInfoService, Mockito.never()).initModelInfo(config);
    }

    @Test
    public void shouldDisableUnavailableVectorCapabilityAndContinueRegularStartup() throws Exception {
        DataQuerySettings config = buildConfig(false, true, true);
        DataAgentInitializerPort initializerPort = Mockito.mock(DataAgentInitializerPort.class);
        Mockito.doThrow(new IllegalStateException("vector unavailable"))
                .when(initializerPort).initializeVectorIndex(false, 1024);
        ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);

        service(config, initializerPort, chatModelInfoService).initialize(1024);

        Assert.assertFalse(config.getQdrantConfig().getEnable());
        Assert.assertTrue(config.getEsConfig().getEnable());
        Mockito.verify(initializerPort).initializeColumnValueIndex(false);
        Mockito.verify(chatModelInfoService).initModelInfo(config);
    }

    @Test
    public void shouldDisableUnavailableColumnValueCapabilityAndContinueRegularStartup() throws Exception {
        DataQuerySettings config = buildConfig(false, false, true);
        DataAgentInitializerPort initializerPort = Mockito.mock(DataAgentInitializerPort.class);
        Mockito.doThrow(new IllegalStateException("index unavailable"))
                .when(initializerPort).initializeColumnValueIndex(false);
        ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);

        service(config, initializerPort, chatModelInfoService).initialize(1024);

        Assert.assertFalse(config.getEsConfig().getEnable());
        Mockito.verify(chatModelInfoService).initModelInfo(config);
    }

    @Test
    public void shouldPropagateForcedVectorInitializationFailure() throws Exception {
        DataQuerySettings config = buildConfig(true, true, true);
        DataAgentInitializerPort initializerPort = Mockito.mock(DataAgentInitializerPort.class);
        IllegalStateException expected = new IllegalStateException("vector refresh failed");
        Mockito.doThrow(expected).when(initializerPort).initializeVectorIndex(true, 1024);
        ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);

        IllegalStateException actual = Assert.assertThrows(IllegalStateException.class,
                () -> service(config, initializerPort, chatModelInfoService).initialize(1024));

        Assert.assertSame(expected, actual);
        Assert.assertFalse(config.getQdrantConfig().getEnable());
        Mockito.verify(initializerPort, Mockito.never()).initializeColumnValueIndex(true);
        Mockito.verifyNoInteractions(chatModelInfoService);
    }

    @Test
    public void shouldPropagateForcedModelMetadataRefreshFailure() throws Exception {
        DataQuerySettings config = buildConfig(true, false, false);
        DataAgentInitializerPort initializerPort = Mockito.mock(DataAgentInitializerPort.class);
        ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);
        IllegalStateException expected = new IllegalStateException("metadata refresh failed");
        Mockito.doThrow(expected).when(chatModelInfoService).refreshModelInfo(config);

        IllegalStateException actual = Assert.assertThrows(IllegalStateException.class,
                () -> service(config, initializerPort, chatModelInfoService).initialize(1024));

        Assert.assertSame(expected, actual);
        Mockito.verify(chatModelInfoService, Mockito.never()).initModelInfo(config);
    }

    private DataAgentInitializationApplicationService service(
            DataQuerySettings config,
            DataAgentInitializerPort initializerPort,
            ChatModelInfoService chatModelInfoService) {
        return new DataAgentInitializationApplicationService(
                config, initializerPort, chatModelInfoService, Optional.empty());
    }

    private DataQuerySettings buildConfig(boolean forceRefresh, boolean qdrantEnabled, boolean esEnabled) {
        DataQuerySettings config = new DataQuerySettings();
        config.setForceRefresh(forceRefresh);
        VectorRecallSettings qdrantConfig = new VectorRecallSettings();
        qdrantConfig.setEnable(qdrantEnabled);
        config.setQdrantConfig(qdrantConfig);
        ColumnValueRecallSettings esConfig = new ColumnValueRecallSettings();
        esConfig.setEnable(esEnabled);
        config.setEsConfig(esConfig);
        return config;
    }
}
