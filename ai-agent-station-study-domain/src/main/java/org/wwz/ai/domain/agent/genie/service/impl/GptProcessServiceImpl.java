package org.wwz.ai.domain.agent.genie.service.impl;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.genie.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.genie.service.IGptProcessService;
import org.wwz.ai.domain.agent.genie.service.IMultiAgentService;
import org.wwz.ai.domain.agent.genie.util.ChateiUtils;
import org.wwz.ai.domain.agent.genie.util.SseUtil;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GptProcessServiceImpl implements IGptProcessService {
    @Autowired
    private IMultiAgentService multiAgentService;

    @Override
    public SseEmitter queryMultiAgentIncrStream(GptQueryReq req) {
        long timeoutMillis = TimeUnit.HOURS.toMillis(1);
        req.setUser("genie");
        req.setDeepThink(req.getDeepThink() == null ? 0: req.getDeepThink());
        String traceId = ChateiUtils.getRequestId(req);
        req.setTraceId(traceId);
        final SseEmitter emitter = SseUtil.build(timeoutMillis, req.getTraceId());
        multiAgentService.searchForAgentRequest(req, emitter);
        log.info("queryMultiAgentIncrStream GptQueryReq request:{}", req);
        return emitter;
    }
}
