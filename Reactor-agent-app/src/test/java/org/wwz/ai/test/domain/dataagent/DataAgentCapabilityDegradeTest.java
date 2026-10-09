package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.application.agent.dataquery.initialization.DataAgentInitializationApplicationService;
import org.wwz.ai.application.agent.dataquery.DataAgentApplicationService;
import org.wwz.ai.application.agent.dataquery.IDataAgentApplicationService;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.rag.port.DataAgentInitializerPort;
import org.wwz.ai.domain.agent.rag.model.config.ColumnValueRecallSettings;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.domain.agent.rag.service.ChatModelInfoService;
import org.wwz.ai.infrastructure.adapter.port.OkHttpRemoteHttpAdapter;
import org.wwz.ai.infrastructure.adapter.port.OkHttpRemoteStreamAdapter;
import org.wwz.ai.infrastructure.adapter.port.ReactorToolFileArtifactAdapter;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;
import org.wwz.ai.trigger.http.dataagent.DataAgentController;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 能力降级测试。
 */
public class DataAgentCapabilityDegradeTest {

    @Test
    public void shouldInjectCaseSeamIntoDataAgentController() {
        List<String> fieldTypes = Arrays.stream(DataAgentController.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toList());

        Assert.assertTrue(fieldTypes.contains(IDataAgentApplicationService.class.getName()));
        Assert.assertFalse(fieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.DataAgentService"));
    }

    @Test
    public void shouldInjectStableDomainSeamIntoDataAgentApplicationService() {
        List<String> fieldTypes = Arrays.stream(DataAgentApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toList());

        Assert.assertTrue(fieldTypes.contains("org.wwz.ai.domain.agent.rag.DataAgentQueryService"));
        Assert.assertFalse(fieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.DataAgentService"));
        Assert.assertFalse(fieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.Nl2SqlService"));
        Assert.assertFalse(fieldTypes.contains("org.wwz.ai.domain.agent.rag.service.ChatModelInfoService"));
        Assert.assertFalse(fieldTypes.contains("org.wwz.ai.domain.agent.rag.service.SchemaRecallService"));
    }

    @Test
    public void shouldAssembleInfrastructureOwnedRuntimeAdapters() {
        ReactorRuntimeDependencies dependencies = ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig());

        Assert.assertTrue(dependencies.requireRemoteHttpPort() instanceof OkHttpRemoteHttpAdapter);
        Assert.assertTrue(dependencies.requireRemoteStreamPort() instanceof OkHttpRemoteStreamAdapter);
        Assert.assertTrue(dependencies.requireFileArtifactPort() instanceof ReactorToolFileArtifactAdapter);
    }

    @Test
    public void shouldDisableEsWhenRegularApplicationInitializationFails() throws Exception {
        DataQuerySettings dataQuerySettings = new DataQuerySettings();
        ColumnValueRecallSettings esConfig = new ColumnValueRecallSettings();
        esConfig.setEnable(true);
        dataQuerySettings.setEsConfig(esConfig);
        dataQuerySettings.setForceRefresh(false);

        DataAgentInitializerPort initializerPort = Mockito.mock(DataAgentInitializerPort.class);
        Mockito.doThrow(new IllegalStateException("es init failed"))
                .when(initializerPort).initializeColumnValueIndex(false);
        DataAgentInitializationApplicationService service = new DataAgentInitializationApplicationService(
                dataQuerySettings, initializerPort, Mockito.mock(ChatModelInfoService.class), Optional.empty());

        service.initialize(1024);

        Assert.assertFalse(dataQuerySettings.getEsConfig().getEnable());
    }

    @Test
    public void shouldKeepInitializationApplicationServiceIndependentOfInfrastructureClasses() {
        List<String> fieldTypes = Arrays.stream(DataAgentInitializationApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .toList();

        Assert.assertTrue(fieldTypes.contains(DataAgentInitializerPort.class.getName()));
        Assert.assertTrue(fieldTypes.stream().noneMatch(type -> type.startsWith("org.wwz.ai.infrastructure.")));
    }
}
