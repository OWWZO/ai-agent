package org.wwz.ai.application.agent.file;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.domain.agent.adapter.port.ConversationFilePort;
import org.wwz.ai.domain.agent.model.valobj.ConversationUploadedFile;

import java.io.InputStream;

/**
 * 会话附件上传应用服务。
 * Trigger 只依赖本 seam，不直连 infrastructure 网关。
 */
@Service
@RequiredArgsConstructor
public class ConversationFileApplicationService {

    private final ConversationSessionOwnershipApplicationService conversationSessionOwnershipApplicationService;
    private final ConversationFilePort conversationFilePort;

    public ConversationUploadedFile upload(String visitorId,
                                           String sessionId,
                                           String originalFileName,
                                           String contentType,
                                           long size,
                                           InputStream content) {
        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        if (content == null || size <= 0) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        conversationSessionOwnershipApplicationService.ensureSessionAccessible(visitorId, sessionId, null);
        return conversationFilePort.upload(sessionId, originalFileName, contentType, size, content);
    }
}
