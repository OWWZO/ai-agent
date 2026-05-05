package org.wwz.ai.domain.agent.reactor.util;


import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.enums.AutoBotsResultStatus;
import org.wwz.ai.domain.agent.reactor.model.dto.AutoBotsResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;

public class ChateiUtils {
    public static final String SOURCE_MOBILE = "mobile";
    public static final String SOURCE_PC = "pc";
    private static final String NO_ANSWER = "哎呀，超出我的知识领域了，换个问题试试吧";

    public static String getRequestId(GptQueryReq request) {
        return getRequestId(request.getUser(), request.getSessionId(), request.getRequestId());
    }

    public static String getRequestId(String erp, String traceId, String reqId) {
        erp = StringUtils.isNotEmpty(erp) ? erp.toLowerCase() : erp;
        if (ChineseCharacterCounter.hasChineseCharacters(erp)) {
            return traceId + ":" + reqId;
        } else {
            return erp + traceId + ":" + reqId;
        }
    }
    public static AutoBotsResult toAutoBotsResult(AgentRequest request, String status) {
        AutoBotsResult result = new AutoBotsResult();
        result.setTraceId(request.getRequestId());
        result.setReqId(request.getRequestId());
        result.setStatus(status);
        if (AutoBotsResultStatus.no.name().equals(status)) {
            result.setFinished(true);
            result.setResponse(NO_ANSWER);
            result.setResponseAll(NO_ANSWER);
        }
        return result;
    }
}
