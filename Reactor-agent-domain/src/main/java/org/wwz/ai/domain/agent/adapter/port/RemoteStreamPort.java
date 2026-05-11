package org.wwz.ai.domain.agent.adapter.port;

import java.io.IOException;

/**
 * 通用远端流式调用端口。
 * 用于把 domain 中依赖 OkHttp SSE / Callback 的实现统一下沉到 infrastructure。
 */
public interface RemoteStreamPort {

    /**
     * 发起一次流式 HTTP 请求，并返回可取消的会话句柄。
     */
    RemoteStreamSession openStream(RemoteStreamRequest request, RemoteStreamListener listener) throws IOException;
}
