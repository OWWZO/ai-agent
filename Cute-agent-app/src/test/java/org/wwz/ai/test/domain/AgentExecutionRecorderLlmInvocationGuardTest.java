package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerWriteRepository;
import org.wwz.ai.domain.agent.ledger.impl.AgentExecutionRecorderImpl;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.ledger.tooloutput.ToolOutputWriter;

public class AgentExecutionRecorderLlmInvocationGuardTest {

    @Test
    public void createLlmInvocationDoesNotInsertWhenAgentNameBlank() {
        IExecutionLedgerWriteRepository repository = Mockito.mock(IExecutionLedgerWriteRepository.class);
        ToolOutputWriter toolOutputWriter = Mockito.mock(ToolOutputWriter.class);
        AgentExecutionRecorderImpl recorder = new AgentExecutionRecorderImpl(repository, toolOutputWriter);

        Long invocationId = recorder.createLlmInvocation(LlmInvocationStartRecord.builder()
                .runId(1L)
                .requestId("req-blank-agent")
                .agentName(null)
                .stepNo(3)
                .build());

        Assert.assertNull(invocationId);
        Mockito.verify(repository, Mockito.never()).insertLlmInvocation(Mockito.any());
    }
}
