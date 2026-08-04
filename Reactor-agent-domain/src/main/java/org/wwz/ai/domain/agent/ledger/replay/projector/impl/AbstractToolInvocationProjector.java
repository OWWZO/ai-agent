package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.ledger.replay.projector.ToolInvocationProjector;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用历史回放投影基类。
 * <p>
 * 统一处理账本事实到前端 eventData 的外壳、artifact 合并、tool binding 和子 Agent 嵌套信息；
 * 具体工具只实现自身 payload 的解释，不直接读取数据库。
 */
abstract class AbstractToolInvocationProjector implements ToolInvocationProjector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected Map<String, Object> readMap(String text) {
        if (StringUtils.isBlank(text)) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(
                    text,
                    MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)
            );
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    protected List<Map<String, Object>> buildArtifactRefs(List<ArtifactView> artifacts) {
        if (CollectionUtils.isEmpty(artifacts)) {
            return List.of();
        }
        List<Map<String, Object>> refs = new ArrayList<>(artifacts.size());
        for (ArtifactView artifact : artifacts) {
            if (artifact == null) {
                continue;
            }
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("resourceKey", StringUtils.defaultIfBlank(artifact.getStorageKey(), artifact.getFileName()));
            ref.put("name", artifact.getFileName());
            ref.put("previewUrl", artifact.getPreviewUrl());
            ref.put("downloadUrl", artifact.getDownloadUrl());
            ref.put("fileName", artifact.getFileName());
            ref.put("mimeType", artifact.getMimeType());
            ref.put("size", artifact.getFileSize());
            ref.put("missing", Boolean.FALSE);
            refs.add(ref);
        }
        return refs;
    }

    /**
     * 将 typed output 里的逻辑 fileRefs 与 artifact 账本中的稳定链接进行合并。
     */
    protected List<Map<String, Object>> mergeFileRefs(List<ToolFileRef> fileRefs, List<ArtifactView> artifacts) {
        List<Map<String, Object>> merged = copyToolFileRefs(fileRefs);
        if (CollectionUtils.isEmpty(artifacts)) {
            return merged;
        }
        if (merged.isEmpty()) {
            return copyArtifactInfos(artifacts);
        }
        enrichFromArtifacts(merged, artifacts);
        markMissingLinks(merged);
        return merged;
    }

    private List<Map<String, Object>> copyToolFileRefs(List<ToolFileRef> fileRefs) {
        List<Map<String, Object>> merged = new ArrayList<>();
        if (CollectionUtils.isEmpty(fileRefs)) {
            return merged;
        }
        for (ToolFileRef fileRef : fileRefs) {
            if (fileRef != null) {
                merged.add(toToolFileInfo(fileRef));
            }
        }
        return merged;
    }

    private List<Map<String, Object>> copyArtifactInfos(List<ArtifactView> artifacts) {
        List<Map<String, Object>> merged = new ArrayList<>();
        for (ArtifactView artifact : artifacts) {
            if (artifact != null) {
                merged.add(toArtifactInfo(artifact));
            }
        }
        return merged;
    }

    private void enrichFromArtifacts(List<Map<String, Object>> merged, List<ArtifactView> artifacts) {
        for (Map<String, Object> info : merged) {
            String fileName = String.valueOf(info.getOrDefault("fileName", ""));
            ArtifactView matched = artifacts.stream()
                    .filter(artifact -> artifact != null && StringUtils.equals(fileName, artifact.getFileName()))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                continue;
            }
            info.putIfAbsent("downloadUrl", matched.getDownloadUrl());
            info.putIfAbsent("previewUrl", matched.getPreviewUrl());
            info.putIfAbsent("ossUrl", matched.getDownloadUrl());
            info.putIfAbsent("domainUrl", matched.getPreviewUrl());
            info.putIfAbsent("missing", Boolean.FALSE);
            if (matched.getFileSize() != null) {
                info.putIfAbsent("fileSize", matched.getFileSize());
            }
        }
    }

    private void markMissingLinks(List<Map<String, Object>> merged) {
        for (Map<String, Object> info : merged) {
            boolean hasPreview = StringUtils.isNotBlank(String.valueOf(info.getOrDefault("previewUrl", "")));
            boolean hasDownload = StringUtils.isNotBlank(String.valueOf(info.getOrDefault("downloadUrl", "")));
            if (!hasPreview && !hasDownload) {
                info.put("missing", Boolean.TRUE);
                info.putIfAbsent("missingReason", "artifact_not_found");
            } else {
                info.putIfAbsent("missing", Boolean.FALSE);
            }
        }
    }

    protected ProjectedReplayEvent buildTaskEvent(EventResult state,
                                                  ToolInvocationView invocation,
                                                  String logicalMessageType,
                                                  Object responsePayload,
                                                  List<Map<String, Object>> artifactRefs) {
        String taskId = state.getTaskId();
        String orderKey = taskId + ":" + logicalMessageType;
        return ProjectedReplayEvent.builder()
                .taskId(taskId)
                .taskOrder(state.getTaskOrder().getAndIncrement())
                .messageId(resolveMessageId(invocation, logicalMessageType))
                .messageType("task")
                .messageOrder(state.getAndIncrOrder(orderKey))
                .resultMap(responsePayload)
                .artifactRefs(CollectionUtils.isEmpty(artifactRefs) ? null : artifactRefs)
                .build();
    }

    protected Map<String, Object> buildStructuredToolResponse(ToolInvocationView invocation,
                                                              String logicalMessageType,
                                                              Map<String, Object> resultMap) {
        decorateToolPayload(resultMap, invocation);
        Map<String, Object> response = newToolReplayEnvelope(invocation, logicalMessageType);
        response.put("resultMap", resultMap);
        // 内层 payload + 外层 envelope 都需要嵌套标签（对齐实时 SSE）
        mirrorNestingToOuter(response, invocation);
        return response;
    }

    protected Map<String, Object> buildToolResultResponse(ToolInvocationView invocation,
                                                          Map<String, Object> toolResult) {
        putToolBindingIfPresent(toolResult, invocation);
        Map<String, Object> response = newToolReplayEnvelope(invocation, "tool_result");
        response.put("toolResult", toolResult);
        mirrorNestingToOuter(response, invocation);
        // 镜像进 resultMap，对齐 BaseAgentResponseHandler 实时路径
        if (StringUtils.isNotBlank(invocation == null ? null : invocation.getParentToolCallId())) {
            Map<String, Object> nested = new LinkedHashMap<>();
            decorateToolPayload(nested, invocation);
            response.put("resultMap", nested);
        }
        return response;
    }

    /** 将嵌套标签同步到外层 envelope，供 resolveParentToolUseId 读取。 */
    private void mirrorNestingToOuter(Map<String, Object> response, ToolInvocationView invocation) {
        appendSubAgentNestingTags(response, invocation);
    }

    private Map<String, Object> newToolReplayEnvelope(ToolInvocationView invocation, String messageType) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", invocation == null ? null : invocation.getRequestId());
        response.put("messageId", resolveMessageId(invocation, messageType));
        response.put("messageTime", resolveMessageTime(invocation));
        response.put("messageType", messageType);
        response.put("isFinal", true);
        response.put("finish", false);
        return response;
    }

    private void decorateToolPayload(Map<String, Object> target, ToolInvocationView invocation) {
        putToolBindingIfPresent(target, invocation);
        appendSubAgentNestingTags(target, invocation);
    }

    protected void putToolBindingIfPresent(Map<String, Object> resultMap, ToolInvocationView invocation) {
        if (resultMap == null || invocation == null) {
            return;
        }
        if (StringUtils.isNotBlank(invocation.getToolCallId())) {
            resultMap.put("toolCallId", invocation.getToolCallId());
        }
        if (StringUtils.isNotBlank(invocation.getToolName())) {
            resultMap.put("toolName", invocation.getToolName());
        }
    }

    /**
     * 还原子 Agent 嵌套标签，与 SubAgentPrinter / BaseAgentResponseHandler 实时契约一致。
     */
    protected void appendSubAgentNestingTags(Map<String, Object> target, ToolInvocationView invocation) {
        if (target == null || invocation == null) {
            return;
        }
        if (StringUtils.isNotBlank(invocation.getParentToolCallId())) {
            target.put("parentToolUseId", invocation.getParentToolCallId());
        }
        if (StringUtils.isNotBlank(invocation.getSubAgentId())) {
            target.put("subAgentId", invocation.getSubAgentId());
        }
        String subAgentType = invocation.getSubAgentType();
        if (StringUtils.isBlank(subAgentType) && StringUtils.startsWith(invocation.getAgentName(), "subagent:")) {
            subAgentType = StringUtils.removeStart(invocation.getAgentName(), "subagent:");
        }
        if (StringUtils.isNotBlank(subAgentType)) {
            target.put("subAgentType", subAgentType);
        }
        if (StringUtils.isNotBlank(invocation.getSubAgentDescription())) {
            target.put("subAgentDescription", invocation.getSubAgentDescription());
        }
    }

    protected String resolveMessageId(ToolInvocationView invocation, String suffix) {
        String base = invocation == null ? null : invocation.getToolCallId();
        if (StringUtils.isBlank(base)) {
            base = invocation == null ? null : invocation.getToolName();
        }
        if (StringUtils.isBlank(base)) {
            base = "history-tool";
        }
        return StringUtils.isBlank(suffix) ? base : base + ":" + suffix;
    }

    protected String resolveMessageTime(ToolInvocationView invocation) {
        if (invocation == null) {
            return String.valueOf(System.currentTimeMillis());
        }
        if (invocation.getFinishedAt() != null) {
            return String.valueOf(invocation.getFinishedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        if (invocation.getStartedAt() != null) {
            return String.valueOf(invocation.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return String.valueOf(System.currentTimeMillis());
    }

    private Map<String, Object> toArtifactInfo(ArtifactView artifact) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("fileName", artifact.getFileName());
        info.put("downloadUrl", artifact.getDownloadUrl());
        info.put("previewUrl", artifact.getPreviewUrl());
        info.put("ossUrl", artifact.getDownloadUrl());
        info.put("domainUrl", artifact.getPreviewUrl());
        info.put("missing", Boolean.FALSE);
        if (artifact.getFileSize() != null) {
            info.put("fileSize", artifact.getFileSize());
        }
        return info;
    }

    private Map<String, Object> toToolFileInfo(ToolFileRef fileRef) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("fileName", StringUtils.defaultString(fileRef.getFileName()));
        if (StringUtils.isNotBlank(fileRef.getDownloadUrl())) {
            info.put("downloadUrl", fileRef.getDownloadUrl());
        }
        if (StringUtils.isNotBlank(fileRef.getPreviewUrl())) {
            info.put("previewUrl", fileRef.getPreviewUrl());
        }
        if (StringUtils.isNotBlank(fileRef.getOssUrl())) {
            info.put("ossUrl", fileRef.getOssUrl());
        }
        if (StringUtils.isNotBlank(fileRef.getDomainUrl())) {
            info.put("domainUrl", fileRef.getDomainUrl());
        }
        if (!info.containsKey("missing")) {
            info.put("missing", Boolean.FALSE);
        }
        if (fileRef.getFileSize() != null) {
            info.put("fileSize", fileRef.getFileSize());
        }
        return info;
    }
}
