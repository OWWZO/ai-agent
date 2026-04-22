package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 会话级文件恢复与 artifact 归一化支持
 */
@Component
public class SessionArtifactRestoreSupport {

    /**
     * 从快照和最近窗口恢复稳定文件。
     */
    public List<FileInformation> restoreFiles(String snapshotArtifactRefsJson,
                                              List<AgentMessage> recentMessages,
                                              Map<Long, List<AgentMessageEvent>> eventMap) {
        List<JSONObject> artifactRefs = new ArrayList<>();
        artifactRefs.addAll(parseArtifactRefs(snapshotArtifactRefsJson));
        artifactRefs.addAll(collectArtifactRefs(recentMessages, eventMap));
        return toFiles(artifactRefs);
    }

    /**
     * 聚合一批消息对应的上传文件与事件 artifact。
     */
    public List<JSONObject> collectArtifactRefs(List<AgentMessage> messages,
                                                Map<Long, List<AgentMessageEvent>> eventMap) {
        if (CollectionUtils.isEmpty(messages)) {
            return List.of();
        }

        List<JSONObject> artifactRefs = new ArrayList<>();
        for (AgentMessage message : messages) {
            artifactRefs.addAll(normalizeFilesToArtifactRefs(parseFiles(message.getFilesJson())));
            artifactRefs.addAll(extractArtifactRefs(eventMap == null ? null : eventMap.get(message.getId())));
        }
        return deduplicateArtifactRefs(artifactRefs);
    }

    /**
     * 从事件 payload 中提取标准化 artifact 引用。
     */
    public List<JSONObject> extractArtifactRefs(List<AgentMessageEvent> events) {
        if (CollectionUtils.isEmpty(events)) {
            return List.of();
        }

        List<JSONObject> artifactRefs = new ArrayList<>();
        for (AgentMessageEvent event : events) {
            JSONObject payload = ConversationEventPayloadNormalizer.normalizePayloadJson(event.getPayloadJson());
            artifactRefs.addAll(ConversationEventPayloadNormalizer.extractNormalizedArtifactRefs(payload));
        }
        return deduplicateArtifactRefs(artifactRefs);
    }

    /**
     * 把上传文件结构转换为统一 artifact 结构，便于与快照和事件共同归档。
     */
    public List<JSONObject> normalizeFilesToArtifactRefs(List<FileInformation> files) {
        if (CollectionUtils.isEmpty(files)) {
            return List.of();
        }

        List<JSONObject> artifactRefs = new ArrayList<>();
        for (FileInformation file : files) {
            if (!StringUtils.hasText(file.getDomainUrl()) && !StringUtils.hasText(file.getOssUrl())) {
                continue;
            }
            JSONObject ref = new JSONObject(new LinkedHashMap<>());
            ref.put("artifactType", file.getFileType());
            ref.put("displayName", firstNonBlank(file.getFileName(), file.getOriginFileName(), "未命名文件"));
            ref.put("description", file.getFileDesc());
            ref.put("resourceKey", firstNonBlank(file.getResourceKey(), file.getOriginOssUrl(), file.getOssUrl(), file.getDomainUrl(), file.getFileName()));
            ref.put("downloadUrl", firstNonBlank(file.getOssUrl(), file.getDomainUrl()));
            ref.put("previewUrl", firstNonBlank(file.getDomainUrl(), file.getOssUrl()));
            ref.put("fileSize", file.getFileSize());
            ref.put("mimeType", file.getMimeType());
            ref.put("originFileName", file.getOriginFileName());
            ref.put("originFileUrl", file.getOriginFileUrl());
            ref.put("originOssUrl", file.getOriginOssUrl());
            ref.put("originDomainUrl", file.getOriginDomainUrl());
            ref.put("missing", false);
            artifactRefs.add(ref);
        }
        return deduplicateArtifactRefs(artifactRefs);
    }

    /**
     * 解析请求中的上传文件 JSON。
     */
    public List<FileInformation> parseFiles(String filesJson) {
        if (!StringUtils.hasText(filesJson)) {
            return List.of();
        }
        try {
            JSONArray jsonArray = JSON.parseArray(filesJson);
            List<FileInformation> files = new ArrayList<>(jsonArray.size());
            for (Object item : jsonArray) {
                if (!(item instanceof JSONObject)) {
                    continue;
                }
                JSONObject file = (JSONObject) item;
                files.add(FileInformation.builder()
                        .fileName(firstNonBlank(file.getString("fileName"), file.getString("name")))
                        .fileDesc(firstNonBlank(file.getString("fileDesc"), file.getString("description")))
                        .ossUrl(firstNonBlank(file.getString("ossUrl"), file.getString("downloadUrl"), file.getString("url")))
                        .domainUrl(firstNonBlank(file.getString("domainUrl"), file.getString("previewUrl"), file.getString("url")))
                        .fileSize(firstNumber(file.get("fileSize"), file.get("size")))
                        .fileType(firstNonBlank(file.getString("fileType"), file.getString("type")))
                        .resourceKey(firstNonBlank(file.getString("resourceKey"), file.getString("downloadUrl"), file.getString("domainUrl"), file.getString("url")))
                        .mimeType(file.getString("mimeType"))
                        .originFileName(file.getString("originFileName"))
                        .originFileUrl(file.getString("originFileUrl"))
                        .originOssUrl(file.getString("originOssUrl"))
                        .originDomainUrl(file.getString("originDomainUrl"))
                        .build());
            }
            return files;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 解析快照中的 artifact JSON。
     */
    public List<JSONObject> parseArtifactRefs(String artifactRefsJson) {
        if (!StringUtils.hasText(artifactRefsJson)) {
            return List.of();
        }
        try {
            JSONArray jsonArray = JSON.parseArray(artifactRefsJson);
            List<JSONObject> refs = new ArrayList<>(jsonArray.size());
            for (Object item : jsonArray) {
                if (item instanceof JSONObject) {
                    refs.add((JSONObject) item);
                }
            }
            return deduplicateArtifactRefs(refs);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 把 artifact 结构转回 Agent 可消费的文件结构。
     */
    public List<FileInformation> toFiles(List<JSONObject> artifactRefs) {
        if (CollectionUtils.isEmpty(artifactRefs)) {
            return List.of();
        }

        List<FileInformation> files = new ArrayList<>();
        Set<String> deduplicatedKeys = new LinkedHashSet<>();
        for (JSONObject ref : artifactRefs) {
            if (ref == null || ref.getBooleanValue("missing")) {
                continue;
            }

            String domainUrl = firstNonBlank(ref.getString("previewUrl"), ref.getString("downloadUrl"));
            String ossUrl = firstNonBlank(ref.getString("downloadUrl"), ref.getString("previewUrl"));
            if (!StringUtils.hasText(domainUrl) && !StringUtils.hasText(ossUrl)) {
                continue;
            }

            String deduplicatedKey = firstNonBlank(
                    ref.getString("resourceKey"),
                    ossUrl,
                    domainUrl,
                    ref.getString("displayName"));
            if (!deduplicatedKeys.add(deduplicatedKey)) {
                continue;
            }

            files.add(FileInformation.builder()
                    .fileName(firstNonBlank(ref.getString("displayName"), ref.getString("fileName")))
                    .fileDesc(ref.getString("description"))
                    .ossUrl(ossUrl)
                    .domainUrl(domainUrl)
                    .fileSize(ref.getInteger("fileSize"))
                    .fileType(ref.getString("artifactType"))
                    .resourceKey(ref.getString("resourceKey"))
                    .mimeType(ref.getString("mimeType"))
                    .originFileName(ref.getString("originFileName"))
                    .originFileUrl(ref.getString("originFileUrl"))
                    .originOssUrl(ref.getString("originOssUrl"))
                    .originDomainUrl(ref.getString("originDomainUrl"))
                    .build());
        }
        return files;
    }

    /**
     * 序列化为快照中的 artifactRefs_json。
     */
    public String toArtifactRefsJson(List<JSONObject> artifactRefs) {
        return JSON.toJSONString(deduplicateArtifactRefs(artifactRefs));
    }

    /**
     * 合并两批文件，优先保留先出现的稳定引用。
     */
    public List<FileInformation> mergeFiles(List<FileInformation> primaryFiles, List<FileInformation> secondaryFiles) {
        List<FileInformation> mergedFiles = new ArrayList<>();
        Set<String> deduplicatedKeys = new LinkedHashSet<>();
        appendFiles(mergedFiles, deduplicatedKeys, primaryFiles);
        appendFiles(mergedFiles, deduplicatedKeys, secondaryFiles);
        return mergedFiles;
    }

    private void appendFiles(List<FileInformation> mergedFiles,
                             Set<String> deduplicatedKeys,
                             List<FileInformation> files) {
        if (CollectionUtils.isEmpty(files)) {
            return;
        }
        for (FileInformation file : files) {
            if (file == null) {
                continue;
            }
            String deduplicatedKey = firstNonBlank(
                    file.getResourceKey(),
                    file.getOriginOssUrl(),
                    file.getOriginFileUrl(),
                    file.getOssUrl(),
                    file.getDomainUrl(),
                    file.getFileName());
            if (deduplicatedKey == null || !deduplicatedKeys.add(deduplicatedKey)) {
                continue;
            }
            mergedFiles.add(file);
        }
    }

    private List<JSONObject> deduplicateArtifactRefs(List<JSONObject> artifactRefs) {
        if (CollectionUtils.isEmpty(artifactRefs)) {
            return List.of();
        }

        List<JSONObject> deduplicatedRefs = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (JSONObject ref : artifactRefs) {
            if (ref == null) {
                continue;
            }
            String deduplicatedKey = firstNonBlank(
                    ref.getString("resourceKey"),
                    ref.getString("downloadUrl"),
                    ref.getString("previewUrl"),
                    ref.getString("displayName"));
            if (deduplicatedKey == null || !seenKeys.add(deduplicatedKey)) {
                continue;
            }
            deduplicatedRefs.add(ref);
        }
        return deduplicatedRefs;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer firstNumber(Object... values) {
        for (Object value : values) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value == null) {
                continue;
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
