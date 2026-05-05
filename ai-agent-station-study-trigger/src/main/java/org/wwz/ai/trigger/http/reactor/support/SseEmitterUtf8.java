package org.wwz.ai.trigger.http.reactor.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

/**
 * 统一保证 SSE 响应头使用 UTF-8，避免中文流式内容出现编码歧义。
 */
public class SseEmitterUtf8 extends SseEmitter {

    public SseEmitterUtf8(Long timeout) {
        super(timeout);
    }

    @Override
    protected void extendResponse(ServerHttpResponse outputMessage) {
        HttpHeaders headers = outputMessage.getHeaders();
        if (headers.getContentType() == null) {
            headers.setContentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8));
        }
    }
}
