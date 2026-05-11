package org.wwz.ai.domain.agent.ledger.replay;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactFormatter;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 历史回放里的总结结果解析器。
 * 统一把账本中保存的原始总结协议（summary + $$$ + artifactKey 列表）
 * 还原成前端可直接消费的「总结文本 + 文件列表 + artifact 引用」结构。
 */
public final class SummaryReplayResultResolver {

    private static final Pattern ARTIFACT_SPLIT_PATTERN = Pattern.compile(ToolArtifactFormatter.ARTIFACT_KEY_SEPARATOR_REGEX);

    private SummaryReplayResultResolver() {
    }

    public static ResolvedSummary resolve(String rawSummaryText, List<ArtifactView> artifacts) {
        String normalizedRawText = StringUtils.defaultString(rawSummaryText);
        String[] parts = normalizedRawText.split(Pattern.quote(ToolArtifactFormatter.ARTIFACT_DELIMITER), 2);
        String summaryText = parts.length == 0 ? "" : StringUtils.trimToEmpty(parts[0]);

        List<ArtifactView> visibleOutputArtifacts = resolveVisibleOutputArtifacts(artifacts);
        List<ArtifactView> selectedArtifacts = parts.length < 2
                ? List.of()
                : selectArtifacts(parts[1], visibleOutputArtifacts);

        // 与实时发送 result 的逻辑保持一致：
        // 1. 若总结明确点名了文件，则只回放这些文件；
        // 2. 若未点名或点名失败，但 run 中存在可见产物，则回退为全部可见产物。
        List<ArtifactView> finalArtifacts = !selectedArtifacts.isEmpty()
                ? selectedArtifacts
                : reverseVisibleArtifacts(visibleOutputArtifacts);

        return new ResolvedSummary(
                summaryText,
                buildFileList(finalArtifacts),
                buildArtifactRefs(finalArtifacts)
        );
    }

    private static List<ArtifactView> resolveVisibleOutputArtifacts(List<ArtifactView> artifacts) {
        if (CollectionUtils.isEmpty(artifacts)) {
            return List.of();
        }
        List<ArtifactView> result = new ArrayList<>(artifacts.size());
        for (ArtifactView artifact : artifacts) {
            if (artifact == null) {
                continue;
            }
            if (!StringUtils.equals(artifact.getArtifactRole(), ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)) {
                continue;
            }
            if (!StringUtils.equals(artifact.getVisibility(), ExecutionLedgerConstants.VISIBILITY_VISIBLE)) {
                continue;
            }
            if (StringUtils.isBlank(artifact.getToolCallId()) || StringUtils.isBlank(artifact.getFileName())) {
                continue;
            }
            result.add(artifact);
        }
        return result;
    }

    private static List<ArtifactView> selectArtifacts(String artifactSection, List<ArtifactView> visibleArtifacts) {
        if (StringUtils.isBlank(artifactSection) || CollectionUtils.isEmpty(visibleArtifacts)) {
            return List.of();
        }

        Map<String, ArtifactView> artifactIndex = new LinkedHashMap<>();
        for (ArtifactView artifact : visibleArtifacts) {
            artifactIndex.put(buildArtifactKey(artifact), artifact);
        }

        LinkedHashSet<ArtifactView> selected = new LinkedHashSet<>();
        for (String item : splitArtifactItems(artifactSection)) {
            ArtifactView exactMatch = artifactIndex.get(item);
            if (exactMatch != null) {
                selected.add(exactMatch);
                continue;
            }

            for (Map.Entry<String, ArtifactView> entry : artifactIndex.entrySet()) {
                if (item.contains(entry.getKey())) {
                    selected.add(entry.getValue());
                    break;
                }
            }
        }
        return new ArrayList<>(selected);
    }

    private static List<String> splitArtifactItems(String artifactSection) {
        if (StringUtils.isBlank(artifactSection)) {
            return List.of();
        }
        String[] parts = ARTIFACT_SPLIT_PATTERN.split(artifactSection);
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = StringUtils.trimToEmpty(part);
            if (StringUtils.isNotBlank(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<ArtifactView> reverseVisibleArtifacts(List<ArtifactView> artifacts) {
        if (CollectionUtils.isEmpty(artifacts)) {
            return List.of();
        }
        List<ArtifactView> reversed = new ArrayList<>(artifacts);
        Collections.reverse(reversed);
        return reversed;
    }

    private static String buildArtifactKey(ArtifactView artifact) {
        if (artifact == null) {
            return "";
        }
        return StringUtils.defaultString(artifact.getToolCallId())
                + ToolArtifactFormatter.ARTIFACT_KEY_SEPARATOR
                + StringUtils.defaultString(artifact.getFileName());
    }

    private static List<Map<String, Object>> buildFileList(List<ArtifactView> artifacts) {
        if (CollectionUtils.isEmpty(artifacts)) {
            return List.of();
        }
        List<Map<String, Object>> fileList = new ArrayList<>(artifacts.size());
        for (ArtifactView artifact : artifacts) {
            if (artifact == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileName", artifact.getFileName());
            item.put("ossUrl", artifact.getDownloadUrl());
            item.put("domainUrl", artifact.getPreviewUrl());
            item.put("downloadUrl", artifact.getDownloadUrl());
            item.put("previewUrl", artifact.getPreviewUrl());
            item.put("resourceKey", StringUtils.defaultIfBlank(artifact.getStorageKey(), artifact.getFileName()));
            item.put("missing", Boolean.FALSE);
            if (artifact.getFileSize() != null) {
                item.put("fileSize", artifact.getFileSize());
            }
            fileList.add(item);
        }
        return fileList;
    }

    private static List<Map<String, Object>> buildArtifactRefs(List<ArtifactView> artifacts) {
        if (CollectionUtils.isEmpty(artifacts)) {
            return List.of();
        }
        List<Map<String, Object>> artifactRefs = new ArrayList<>(artifacts.size());
        for (ArtifactView artifact : artifacts) {
            if (artifact == null) {
                continue;
            }
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("resourceKey", StringUtils.defaultIfBlank(artifact.getStorageKey(), artifact.getFileName()));
            ref.put("name", artifact.getFileName());
            ref.put("fileName", artifact.getFileName());
            ref.put("previewUrl", artifact.getPreviewUrl());
            ref.put("downloadUrl", artifact.getDownloadUrl());
            ref.put("mimeType", artifact.getMimeType());
            ref.put("size", artifact.getFileSize());
            ref.put("missing", Boolean.FALSE);
            artifactRefs.add(ref);
        }
        return artifactRefs;
    }

    public static final class ResolvedSummary {

        private final String summaryText;
        private final List<Map<String, Object>> fileList;
        private final List<Map<String, Object>> artifactRefs;

        public ResolvedSummary(String summaryText,
                               List<Map<String, Object>> fileList,
                               List<Map<String, Object>> artifactRefs) {
            this.summaryText = summaryText;
            this.fileList = fileList == null ? List.of() : fileList;
            this.artifactRefs = artifactRefs == null ? List.of() : artifactRefs;
        }

        public String getSummaryText() {
            return summaryText;
        }

        public List<Map<String, Object>> getFileList() {
            return fileList;
        }

        public List<Map<String, Object>> getArtifactRefs() {
            return artifactRefs;
        }
    }
}
