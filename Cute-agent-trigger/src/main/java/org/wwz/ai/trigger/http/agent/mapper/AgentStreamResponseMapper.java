package org.wwz.ai.trigger.http.agent.mapper;

import org.springframework.stereotype.Component;
import org.wwz.ai.application.agent.stream.AgentSessionStreamFrame;
import org.wwz.ai.trigger.http.agent.vo.AgentStreamResponseVO;

/**
 * Case frame 到 HTTP/history response VO 的显式映射。
 */
@Component
public class AgentStreamResponseMapper {

    public AgentStreamResponseVO toResponse(AgentSessionStreamFrame frame) {
        if (frame == null) {
            return null;
        }
        return AgentStreamResponseVO.builder()
                .status(frame.getStatus())
                .response(frame.getResponse())
                .responseAll(frame.getResponseAll())
                .finished(frame.isFinished())
                .useTimes(frame.getUseTimes())
                .useTokens(frame.getUseTokens())
                .resultMap(frame.getResultMap())
                .responseType(frame.getResponseType())
                .traceId(frame.getTraceId())
                .reqId(frame.getReqId())
                .encrypted(frame.isEncrypted())
                .query(frame.getQuery())
                .messages(frame.getMessages())
                .packageType(frame.getPackageType())
                .errorMsg(frame.getErrorMsg())
                .eventSeq(frame.getEventSeq())
                .retryMs(frame.getRetryMs())
                .build();
    }
}
