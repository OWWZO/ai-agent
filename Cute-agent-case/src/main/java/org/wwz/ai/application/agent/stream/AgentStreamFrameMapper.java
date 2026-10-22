package org.wwz.ai.application.agent.stream;

import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;

/**
 * Domain runtime stream result 到 Case frame 的唯一映射点。
 */
@Component
public class AgentStreamFrameMapper {

    public AgentSessionStreamFrame toFrame(AgentStreamResult result) {
        if (result == null) {
            return null;
        }
        return AgentSessionStreamFrame.builder()
                .status(result.getStatus())
                .response(result.getContentDelta())
                .responseAll(result.getContent())
                .finished(result.isComplete())
                .useTimes(result.getElapsedMillis())
                .useTokens(result.getTokenCount())
                .resultMap(result.getEventData())
                .responseType(result.getContentType())
                .traceId(result.getTraceId())
                .reqId(result.getRequestId())
                .encrypted(result.isEncrypted())
                .query(result.getQuery())
                .messages(result.getMessages())
                .packageType(result.getFrameType())
                .errorMsg(result.getErrorMessage())
                .eventSeq(result.getSequence())
                .retryMs(result.getRetryAfterMs())
                .build();
    }
}
