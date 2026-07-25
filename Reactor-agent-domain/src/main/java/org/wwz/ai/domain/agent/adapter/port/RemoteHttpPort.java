package org.wwz.ai.domain.agent.adapter.port;

import java.io.IOException;
import java.util.Map;

/**
 * 通用远端 HTTP 调用端口。
 * 用于把 domain 中的同步 HTTP 技术细节下沉到 infrastructure。
 */
public interface RemoteHttpPort {

    /**
     * 执行一次同步 HTTP 请求并返回文本响应（成功时；非 2xx 抛异常）。
     */
    String execute(RemoteHttpRequest request) throws IOException;

    /**
     * 执行一次同步 HTTP 请求，返回状态码/响应头/正文。
     * 不因 3xx 抛错，便于业务自行处理 redirect。
     */
    default RemoteHttpResponse executeDetailed(RemoteHttpRequest request) throws IOException {
        String body = execute(request);
        return RemoteHttpResponse.builder()
                .statusCode(200)
                .statusText("OK")
                .headers(Map.of())
                .body(body)
                .finalUrl(request.getUrl())
                .build();
    }
}
