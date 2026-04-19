package org.wwz.ai.domain.agent.reactor.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent流式对话+持久化服务
 * 封装现有的SSE流程，增加消息累积和持久化回调
 */
public interface IAgentStreamPersistService {

    /**
     * 发送消息并流式返回，流结束后自动持久化
     *
     * @param sessionId   会话ID
     * @param requestId   请求ID
     * @param deviceId    设备ID
     * @param query       用户问题
     * @param deepThink   深度思考标志
     * @param outputStyle 产品形态
     * @param filesJson   文件列表JSON
     * @return SseEmitter
     */
    SseEmitter sendAndPersist(String sessionId, String requestId, String deviceId,
                              String query, Integer deepThink, String outputStyle, String filesJson,
                              String aiAgentId);

    /**
     * 停止指定请求的流式执行
     */
    boolean stop(String requestId);
}
