package org.wwz.ai.domain.agent.genie.service.impl;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.genie.service.AgentHandlerService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentHandlerFactory {

    private final Map<String, AgentHandlerService> handlerMap = new ConcurrentHashMap<>();

    // 构造函数注入所有DataHandler实现
    @Autowired
    public AgentHandlerFactory(List<AgentHandlerService> handlers) {
        // 初始化处理器映射
        for (AgentHandlerService handler : handlers) {
            // 可根据Handler的supports方法或自定义注解来注册
            handlerMap.put(handler.getClass().getSimpleName().toLowerCase(), handler);
        }
    }

    // 根据类型获取处理器
    public AgentHandlerService getHandler(AgentContext context, AgentRequest request) {
        if (Objects.isNull(context) || Objects.isNull(request)) {
            return null;
        }

        // 方法1：通过supports方法匹配
        for (AgentHandlerService handler : handlerMap.values()) {
            if (handler.support(context, request)) {
                return handler;
            }
        }

        return null;
    }
}