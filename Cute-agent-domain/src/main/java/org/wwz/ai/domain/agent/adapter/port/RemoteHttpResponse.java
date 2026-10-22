package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * 远端 HTTP 响应（含状态码与响应头，供 WebFetch 等需要判断 redirect 的场景）。
 */
@Value
@Builder
public class RemoteHttpResponse {

    int statusCode;

    String statusText;

    Map<String, String> headers;

    /**
     * 文本响应体；二进制场景可为空。
     */
    String body;

    /**
     * 最终请求 URL（若发生同主机 redirect，可能与原始 URL 不同）。
     */
    String finalUrl;
}
