package org.wwz.ai.trigger.http.reactor.support;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.types.enums.ResponseCode;

@RestControllerAdvice
public class AgentStreamExceptionHandler {

    /**
     * 并发流上限 → 429。
     * <p>
     * ⚠️ 必须显式预设 {@code Content-Type: application/json}，不能依赖内容协商。
     * 流端点的映射带 {@code produces = text/event-stream}，客户端也因此发
     * {@code Accept: text/event-stream}；而这里的返回体是 JSON 对象，
     * 内容协商推导不出 {@code text/event-stream}，Spring 会抛
     * {@code HttpMediaTypeNotAcceptableException}。该异常发生在<b>异常处理阶段</b>，
     * 会被 {@code ExceptionHandlerExceptionResolver} 以 WARN 吞掉并返回 null，
     * 于是原始异常继续冒泡 → dispatcherServlet 记 ERROR 并回 <b>500</b>，
     * 客户端只能看到「SSE 请求失败，状态码: 500」，完全不知道是并发上限。
     * <p>
     * 预设 Content-Type 后 {@code AbstractMessageConverterMethodProcessor} 会直接采用它，
     * 跳过协商（isContentTypePreset 分支），429 与响应体才能真的发出去。
     * 回归测试：{@code AgentSessionStreamLimitContractTest}。
     */
    @ExceptionHandler(AgentStreamLimitException.class)
    public ResponseEntity<Response<Void>> handleLimit(AgentStreamLimitException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Response.<Void>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info(e.getMessage())
                        .build());
    }
}
