package org.wwz.ai.trigger.http.reactor.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * 客户端主动断开 SSE 后，Tomcat/Spring 仍可能在 dispatcher 退出阶段再次抛 IOException。
 * 这里统一识别并吞掉这类异常，避免刷出无意义的错误栈。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SseClientDisconnectExceptionResolver implements HandlerExceptionResolver {

    @Override
    public ModelAndView resolveException(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Object handler,
                                         Exception ex) {
        if (SseClientDisconnectDetector.isClientDisconnected(ex)) {
            return new ModelAndView();
        }
        return null;
    }
}
