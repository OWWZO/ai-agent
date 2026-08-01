package org.wwz.ai.domain.agent.adapter.port;

import org.wwz.ai.domain.agent.model.valobj.ConversationUploadedFile;

import java.io.InputStream;

/**
 * 会话附件上传端口。
 * domain/case 只表达上传语义，具体转发到 reactor-tool 文件服务由 infrastructure 承接。
 */
public interface ConversationFilePort {

    /**
     * 上传会话附件，返回稳定预览/下载地址与资源键。
     */
    ConversationUploadedFile upload(String sessionId,
                                    String originalFileName,
                                    String contentType,
                                    long size,
                                    InputStream content);
}
