package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.application.agent.dataquery.DataAgentApplicationService;
import org.wwz.ai.application.agent.dataquery.command.DataAgentChatCommand;
import org.wwz.ai.application.agent.dataquery.mapper.DataQueryMapper;
import org.wwz.ai.application.agent.dataquery.result.DataAgentStreamResult;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.rag.DataAgentQueryService;
import org.wwz.ai.domain.agent.rag.model.query.DataAgentChatQuery;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryStreamEvent;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

public class DataAgentApplicationServiceTest {

    @Test
    public void mapsCaseChatCommandToDomainQueryAndProjectsOrderedStreamEvents() throws Exception {
        DataAgentQueryService domainService = Mockito.mock(DataAgentQueryService.class);
        DataAgentApplicationService applicationService = new DataAgentApplicationService(domainService, new DataQueryMapper());
        RecordingStream stream = new RecordingStream();
        doAnswer(invocation -> {
            AgentMessageStream domainStream = invocation.getArgument(1);
            domainStream.send(new DataQueryStreamEvent("DEBUG", "request-1"));
            domainStream.send(new DataQueryStreamEvent("THINK", "thinking"));
            domainStream.send(new DataQueryStreamEvent("finished_stream", "finished_stream"));
            domainStream.complete();
            return null;
        }).when(domainService).chatQuery(any(), any());

        applicationService.chatQuery(new DataAgentChatCommand("revenue", "trace-1"), stream);

        verify(domainService).chatQuery(eq(new DataAgentChatQuery("revenue", "trace-1")), any());
        Assert.assertEquals(List.of(
                new DataAgentStreamResult("DEBUG", "request-1"),
                new DataAgentStreamResult("THINK", "thinking"),
                new DataAgentStreamResult("finished_stream", "finished_stream")), stream.events);
        Assert.assertTrue(stream.completed);
    }

    private static final class RecordingStream implements AgentSessionStream {
        private final List<Object> events = new ArrayList<>();
        private boolean completed;

        @Override
        public void send(Object payload) {
            events.add(payload);
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void completeWithError(Throwable throwable) {
            throw new AssertionError(throwable);
        }
    }
}
