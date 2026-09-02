package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;
import org.wwz.ai.domain.agent.memory.ltm.LtmServices;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushService;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryCompactionDao;
import org.wwz.ai.infrastructure.reactor.service.impl.SessionContextCompactionServiceImpl;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class SubAgentLtmIsolationTest {

    @Test
    public void subAgentCompactionSkipsLongTermMemoryHooks() {
        MemoryFlushService flushService = mock(MemoryFlushService.class);
        LtmManager ltmManager = mock(LtmManager.class);
        ReactorRuntimeDependencies dependencies = mock(ReactorRuntimeDependencies.class);
        ObjectProvider<ReactorRuntimeDependencies> dependenciesProvider = mock(ObjectProvider.class);
        when(dependenciesProvider.getObject()).thenReturn(dependencies);
        when(dependencies.requireReactorConfig()).thenReturn(mock(ReactorConfig.class));
        when(dependencies.resolveLlmSettings(anyString())).thenReturn(
                LLMSettings.builder().maxInputTokens(20).maxTokens(1).build());
        when(dependencies.getOptionalMemoryFlushService()).thenReturn(flushService);
        when(dependencies.getOptionalLtmManager()).thenReturn(ltmManager);

        SessionContextCompactionServiceImpl compaction = new SessionContextCompactionServiceImpl(
                dependenciesProvider,
                mock(SessionWorkingMemoryService.class),
                mock(IWorkingMemoryCompactionDao.class),
                true,
                false,
                false,
                false,
                0.50d,
                1,
                1,
                10,
                1,
                0.2d,
                4000,
                1,
                100,
                false,
                false,
                false,
                true);

        LtmServices.bind(null, flushService);
        try {
            List<Message> compacted = compaction.applyIfNeeded(
                    "session-1",
                    "sub:agent-1",
                    "request-1",
                    List.of(Message.userMessage("x".repeat(200), null)));

            Assert.assertNotNull(compacted);
        } finally {
            LtmServices.bind(null, null);
        }

        verifyNoInteractions(flushService, ltmManager);
    }
}
