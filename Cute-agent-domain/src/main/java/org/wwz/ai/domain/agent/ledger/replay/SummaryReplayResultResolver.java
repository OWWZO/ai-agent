package org.wwz.ai.domain.agent.ledger.replay;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactFormatter;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 历史回放里的总结结果解析器。
 * 账本保存终答原文（含 {@code $$$} 点名）；不在回放阶段对 artifact 表做交付匹配，
 * 前端用会话文件表按相对路径/文件名映射展示卡片。
 */
public final class SummaryReplayResultResolver {

    private static final Pattern ARTIFACT_SPLIT_PATTERN =
            Pattern.compile(ToolArtifactFormatter.ARTIFACT_KEY_SEPARATOR_REGEX);

    private SummaryReplayResultResolver() {
    }

    public static ResolvedSummary resolve(String rawSummaryText, List<ArtifactView> artifacts) {
        String raw = StringUtils.defaultString(rawSummaryText);
        String[] parts = raw.split(Pattern.quote(ToolArtifactFormatter.ARTIFACT_DELIMITER), 2);
        List<String> requestedKeys = parts.length < 2 ? List.of() : splitArtifactItems(parts[1]);
        return new ResolvedSummary(raw, List.of(), List.of(), requestedKeys);
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

    public static final class ResolvedSummary {

        private final String summaryText;
        private final List<Map<String, Object>> fileList;
        private final List<Map<String, Object>> artifactRefs;
        private final List<String> artifactKeys;

        public ResolvedSummary(String summaryText,
                               List<Map<String, Object>> fileList,
                               List<Map<String, Object>> artifactRefs,
                               List<String> artifactKeys) {
            this.summaryText = summaryText;
            this.fileList = fileList == null ? List.of() : fileList;
            this.artifactRefs = artifactRefs == null ? List.of() : artifactRefs;
            this.artifactKeys = artifactKeys == null ? List.of() : artifactKeys;
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

        public List<String> getArtifactKeys() {
            return artifactKeys;
        }
    }
}
