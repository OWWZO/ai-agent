package org.wwz.ai.trigger.http.agent.mapper;

import org.springframework.stereotype.Component;
import org.wwz.ai.application.agent.query.GptQueryCommand;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionFile;
import org.wwz.ai.trigger.http.agent.vo.GptQueryRequestVO;

import java.util.List;

/**
 * HTTP request VO 到 GPT query use-case command 的显式映射。
 */
@Component
public class GptQueryRequestMapper {

    public GptQueryCommand toCommand(GptQueryRequestVO request) {
        if (request == null) {
            return null;
        }
        return GptQueryCommand.builder()
                .query(request.getQuery())
                .sessionId(request.getSessionId())
                .requestId(request.getRequestId())
                .deepThink(request.getDeepThink())
                .outputStyle(request.getOutputStyle())
                .traceId(request.getTraceId())
                .user(request.getUser())
                .model(request.getModel())
                .thinking(request.getThinking())
                .thinkingEffort(request.getThinkingEffort())
                .sessionFiles(toFiles(request.getSessionFiles()))
                .forcePlanMode(request.getForcePlanMode())
                .build();
    }

    private List<AgentExecutionFile> toFiles(List<GptQueryRequestVO.FileReferenceVO> files) {
        if (files == null) {
            return null;
        }
        return files.stream().map(file -> AgentExecutionFile.builder()
                .fileName(file.getFileName())
                .fileDesc(file.getFileDesc())
                .ossUrl(file.getOssUrl())
                .domainUrl(file.getDomainUrl())
                .fileSize(file.getFileSize())
                .fileType(file.getFileType())
                .resourceKey(file.getResourceKey())
                .mimeType(file.getMimeType())
                .originFileName(file.getOriginFileName())
                .originFileUrl(file.getOriginFileUrl())
                .originOssUrl(file.getOriginOssUrl())
                .originDomainUrl(file.getOriginDomainUrl())
                .build()).toList();
    }
}
