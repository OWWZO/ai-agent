package org.wwz.ai.trigger.http.reactor.support;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.types.enums.ResponseCode;

@RestControllerAdvice
public class AgentStreamExceptionHandler {

    @ExceptionHandler(AgentStreamLimitException.class)
    public ResponseEntity<Response<Void>> handleLimit(AgentStreamLimitException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Response.<Void>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(e.getMessage())
                .build());
    }
}
