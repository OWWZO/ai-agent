package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.config.reactor.ReplayProjectorAutoConfiguration;
import org.wwz.ai.config.reactor.DataAgentInitRunner;
import org.wwz.ai.config.reactor.startup.H2SchemaBootstrap;
import org.wwz.ai.config.reactor.data.Es7HighLevelClientConfig;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.impl.ExecutionLedgerQueryServiceImpl;
import org.wwz.ai.domain.agent.ledger.replay.ConversationHistoryReplayService;
import org.wwz.ai.domain.agent.ledger.tooloutput.ToolOutputReader;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.IDesktopControlRepository;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.infrastructure.adapter.repository.ExecutionLedgerReadRepository;
import org.wwz.ai.infrastructure.dao.reactor.IArtifactLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IDialogueRunLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IDialogueSessionLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.ILlmInvocationLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolInvocationLedgerDao;
import org.wwz.ai.application.agent.authorization.ConversationSessionAuthorizationService;
import org.wwz.ai.application.agent.dataquery.initialization.DataAgentInitializationApplicationService;
import org.wwz.ai.trigger.http.agent.AgentConversationHistoryController;

import java.lang.reflect.Field;

/**
 * 验证 Phase 1 迁出的 Reactor 装配仍能在 app 层稳定提供 Bean。
 */
public class ReplayProjectorBeanTopologyTest {

    @Test
    public void shouldWireHistoryBeansFromAppOwnedConfiguration() {
        Assert.assertTrue(ReplayProjectorAutoConfiguration.class.getPackageName().startsWith("org.wwz.ai.config"));
        Assert.assertTrue(Es7HighLevelClientConfig.class.getPackageName().startsWith("org.wwz.ai.config"));
        Assert.assertTrue(DataAgentInitRunner.class.getPackageName().startsWith("org.wwz.ai.config"));
        Assert.assertTrue(ReactorConfig.class.getPackageName().startsWith("org.wwz.ai.domain.agent.reactor.config"));

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(ReplayProjectorAutoConfiguration.class);
        context.register(ExecutionLedgerQueryServiceImpl.class);
        context.register(ExecutionLedgerReadRepository.class);
        context.register(AgentConversationHistoryController.class);
        context.register(TestDependencyConfiguration.class);

        try {
            context.refresh();

            Assert.assertNotNull(context.getBean(ExecutionLedgerQueryService.class));
            Assert.assertNotNull(context.getBean(IExecutionLedgerReadRepository.class));
            Assert.assertNotNull(context.getBean(ConversationHistoryReplayService.class));
            Assert.assertNotNull(context.getBean(AgentConversationHistoryController.class));
        } finally {
            context.close();
        }
    }

    @Test
    public void shouldKeepDataAgentRunnerOnStartupAndApplicationSeams() {
        assertDeclaredFieldTypes(DataAgentInitRunner.class,
                H2SchemaBootstrap.class.getName(),
                DataAgentInitializationApplicationService.class.getName());
        for (Field field : DataAgentInitRunner.class.getDeclaredFields()) {
            String packageName = field.getType().getPackageName();
            Assert.assertFalse("Runner 不应注入 Domain 技术 facade: " + field.getType().getName(),
                    packageName.equals("org.wwz.ai.domain.agent.reactor.service"));
            Assert.assertFalse("Runner 不应直接注入 Infrastructure: " + field.getType().getName(),
                    packageName.startsWith("org.wwz.ai.infrastructure"));
        }
        assertDeclaredFieldTypes(Es7HighLevelClientConfig.class,
                "org.wwz.ai.infrastructure.dataquery.elasticsearch.ElasticsearchProperties");
    }

    private void assertDeclaredFieldTypes(Class<?> type, String... expectedTypes) {
        java.util.List<String> fieldTypes = java.util.Arrays.stream(type.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .toList();
        for (String expectedType : expectedTypes) {
            Assert.assertTrue(type.getSimpleName() + " 应显式登记延期 legacy 契约: " + expectedType,
                    fieldTypes.contains(expectedType));
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

        @Bean
        public IUserQuestionRepository userQuestionRepository() {
            return Mockito.mock(IUserQuestionRepository.class);
        }

        @Bean
        public IDesktopControlRepository desktopControlRepository() {
            return Mockito.mock(IDesktopControlRepository.class);
        }

        @Bean
        public LlmModelCatalog llmModelCatalog() {
            return Mockito.mock(LlmModelCatalog.class);
        }

        @Bean
        public ConversationSessionAuthorizationService conversationSessionAuthorizationService() {
            return Mockito.mock(ConversationSessionAuthorizationService.class);
        }

        @Bean
        public org.wwz.ai.application.agent.stream.AgentStreamFrameMapper agentStreamFrameMapper() {
            return Mockito.mock(org.wwz.ai.application.agent.stream.AgentStreamFrameMapper.class);
        }

        @Bean
        public org.wwz.ai.trigger.http.agent.mapper.AgentStreamResponseMapper agentStreamResponseMapper() {
            return Mockito.mock(org.wwz.ai.trigger.http.agent.mapper.AgentStreamResponseMapper.class);
        }
    }
}
