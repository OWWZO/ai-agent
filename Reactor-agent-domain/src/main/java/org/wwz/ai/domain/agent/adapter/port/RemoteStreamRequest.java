package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * 远端流式请求描述。
 * 统一承接 SSE 与“按行返回 data: ...”的长连接响应。
 */
@Value
@Builder
public class RemoteStreamRequest {

    /**
     * HTTP 方法，当前主链路统一使用 POST。
     */
    String method;

    /**
     * 完整请求地址。
     */
    String url;

    /**
     * 请求头。
     */
    Map<String, String> headers;

    /**
     * 文本请求体，通常为 JSON。
     */
    String body;

    /**
     * 连接超时时间，单位秒；为空时由适配器使用默认值。
     */
    Long connectTimeoutSeconds;

    /**
     * 读取超时时间，单位秒；为空时由适配器使用默认值。
     */
    Long readTimeoutSeconds;

    /**
     * 写入超时时间，单位秒；为空时由适配器使用默认值。
     */
    Long writeTimeoutSeconds;

    /**
     * 整体调用超时时间，单位秒；为空时由适配器使用默认值。
     */
    Long callTimeoutSeconds;
}
