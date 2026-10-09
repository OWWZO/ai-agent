package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.wwz.ai.application.agent.dataquery.initialization.DataAgentInitializationApplicationService;
import org.wwz.ai.config.reactor.DataAgentInitRunner;
import org.wwz.ai.config.reactor.startup.H2SchemaBootstrap;

/**
 * DataAgent startup ordering adapter tests.
 */
public class DataAgentInitRunnerRefreshTest {

    @Test
    public void shouldBootstrapH2BeforeCallingInitializationUseCase() throws Exception {
        H2SchemaBootstrap h2SchemaBootstrap = Mockito.mock(H2SchemaBootstrap.class);
        DataAgentInitializationApplicationService initializationService =
                Mockito.mock(DataAgentInitializationApplicationService.class);
        DataAgentInitRunner runner = new DataAgentInitRunner(h2SchemaBootstrap, initializationService);

        runner.run();

        InOrder order = Mockito.inOrder(h2SchemaBootstrap, initializationService);
        order.verify(h2SchemaBootstrap).initializeIfConfigured();
        order.verify(initializationService).initialize(Mockito.anyInt());
    }

    @Test
    public void shouldPropagateForceRefreshFailureFromInitializationUseCase() throws Exception {
        H2SchemaBootstrap h2SchemaBootstrap = Mockito.mock(H2SchemaBootstrap.class);
        DataAgentInitializationApplicationService initializationService =
                Mockito.mock(DataAgentInitializationApplicationService.class);
        IllegalStateException expected = new IllegalStateException("refresh failed");
        Mockito.doThrow(expected).when(initializationService).initialize(Mockito.anyInt());
        DataAgentInitRunner runner = new DataAgentInitRunner(h2SchemaBootstrap, initializationService);

        IllegalStateException actual = Assert.assertThrows(IllegalStateException.class, runner::run);

        Assert.assertSame(expected, actual);
        Mockito.verify(h2SchemaBootstrap).initializeIfConfigured();
    }
}
