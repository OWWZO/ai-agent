package org.wwz.ai.domain.agent.genie.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class SseUtil {
    public static SseEmitter build(Long timeout, String requestId) {
        SseEmitter sseEmitter = new SseEmitterUTF8(timeout);
        sseEmitter.onError((err)-> {
            log.error("SseSession Error, msg: {}, requestId: {}", err.getMessage(), requestId);
            sseEmitter.completeWithError(err);
        });

        sseEmitter.onTimeout(() -> {
            log.info("SseSession Timeout, requestId : {}", requestId);
            sseEmitter.complete();
        });

        sseEmitter.onCompletion(() -> {
            log.info("SseSession Completion, requestId : {}", requestId);
        });

        return sseEmitter;
    }
}
