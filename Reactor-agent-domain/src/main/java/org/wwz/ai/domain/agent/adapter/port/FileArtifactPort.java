package org.wwz.ai.domain.agent.adapter.port;

import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;

import java.io.IOException;

/**
 * 文件产物端口。
 * domain 只表达“上传文件 / 查询文件 / 读取文件内容”，由 infrastructure 承接具体文件服务协议。
 */
public interface FileArtifactPort {

    /**
     * 上传文本文件到远端文件服务。
     */
    FileResponse upload(String serviceBaseUrl, FileRequest request) throws IOException;

    /**
     * 按文件名获取远端文件信息。
     */
    FileResponse get(String serviceBaseUrl, FileRequest request) throws IOException;

    /**
     * 读取指定 URL 的文本内容。
     */
    String readText(String url, Long timeoutSeconds) throws IOException;
}
