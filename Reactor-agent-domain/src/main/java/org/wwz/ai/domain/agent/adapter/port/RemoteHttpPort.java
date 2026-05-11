package org.wwz.ai.domain.agent.adapter.port;

import java.io.IOException;

/**
 * 通用远端 HTTP 调用端口。
 * 用于把 domain 中的同步 HTTP 技术细节下沉到 infrastructure。
 */
public interface RemoteHttpPort {

    /**
     * 执行一次同步 HTTP 请求并返回文本响应。
     */
    String execute(RemoteHttpRequest request) throws IOException;
}
