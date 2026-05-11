package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * 远端 HTTP 请求描述。
 * domain 只表达请求语义与超时约束，不关心底层使用哪种 HTTP 客户端实现。
 */
@Value
@Builder
public class RemoteHttpRequest {

    /**
     * HTTP 方法，例如 GET / POST / PUT / DELETE。
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
