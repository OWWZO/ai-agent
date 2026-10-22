package org.wwz.ai.trigger.http.dataagent.mapper;

import lombok.RequiredArgsConstructor;
import org.wwz.ai.application.agent.dataquery.result.DataAgentStreamResult;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.trigger.http.dataagent.vo.DataAgentChatMessageResponseVO;

/** Maps Case stream results to the stable DataAgent SSE payload shape. */
@RequiredArgsConstructor
public class DataAgentSseResponseStream implements AgentSessionStream {
    private final AgentSessionStream delegate;
    private final DataAgentResponseMapper responseMapper;

    @Override
    public void send(Object payload) throws Exception {
        if (!(payload instanceof DataAgentStreamResult event)) {
            throw new IllegalArgumentException("Unexpected Case DataAgent stream result type");
        }
        DataAgentChatMessageResponseVO response = responseMapper.toResponse(event);
        delegate.send(response);
    }

    @Override
    public void complete() {
        delegate.complete();
    }

    @Override
    public void completeWithError(Throwable throwable) {
        delegate.completeWithError(throwable);
    }

    @Override
    public void onAbort(Runnable abortHandler) {
        delegate.onAbort(abortHandler);
    }

    @Override
    public boolean isAborted() {
        return delegate.isAborted();
    }
}
