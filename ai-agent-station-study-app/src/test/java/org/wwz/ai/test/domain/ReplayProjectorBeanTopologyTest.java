package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.reactor.config.ReplayProjectorAutoConfiguration;
import org.wwz.ai.domain.agent.reactor.mapper.IArtifactLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.IDialogueRunLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.IDialogueSessionLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.ILlmInvocationLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolInvocationLedgerDao;
import org.wwz.ai.domain.agent.reactor.service.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.reactor.service.impl.ExecutionLedgerQueryServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.replay.ConversationHistoryReplayService;
import org.wwz.ai.domain.agent.reactor.service.tooloutput.ToolOutputReader;
import org.wwz.ai.trigger.http.agent.AgentConversationHistoryController;

/**
 * 验证历史回放 Bean 装配不会形成循环依赖。
 */
public class ReplayProjectorBeanTopologyTest {

    @Test
    public void shouldWireHistoryBeansWithoutCircularDependency() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(ReplayProjectorAutoConfiguration.class);
        context.register(ExecutionLedgerQueryServiceImpl.class);
        context.register(AgentConversationHistoryController.class);
        context.register(TestDependencyConfiguration.class);

        try {
            context.refresh();

            Assert.assertNotNull(context.getBean(ExecutionLedgerQueryService.class));
            Assert.assertNotNull(context.getBean(ConversationHistoryReplayService.class));
            Assert.assertNotNull(context.getBean(AgentConversationHistoryController.class));
        } finally {
            context.close();
        }
    }

    @Configuration
    static class TestDependencyConfiguration {

        @Bean
        public IDialogueRunLedgerDao dialogueRunLedgerDao() {
            return Mockito.mock(IDialogueRunLedgerDao.class);
        }

        @Bean
        public IDialogueSessionLedgerDao dialogueSessionLedgerDao() {
            return Mockito.mock(IDialogueSessionLedgerDao.class);
        }

        @Bean
        public ILlmInvocationLedgerDao llmInvocationLedgerDao() {
            return Mockito.mock(ILlmInvocationLedgerDao.class);
        }

        @Bean
        public IToolInvocationLedgerDao toolInvocationLedgerDao() {
            return Mockito.mock(IToolInvocationLedgerDao.class);
        }

        @Bean
        public IArtifactLedgerDao artifactLedgerDao() {
            return Mockito.mock(IArtifactLedgerDao.class);
        }

        @Bean
        public ToolOutputReader toolOutputReader() {
            return Mockito.mock(ToolOutputReader.class);
        }
    }
}
