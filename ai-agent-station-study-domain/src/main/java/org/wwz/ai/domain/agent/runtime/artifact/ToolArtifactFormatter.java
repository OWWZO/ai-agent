package org.wwz.ai.domain.agent.runtime.artifact;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.File;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具产物摘要与总结阶段上下文格式化工具。
 */
public final class ToolArtifactFormatter {

    public static final String ARTIFACT_KEY_SEPARATOR = "::";
    public static final String ARTIFACT_DELIMITER = "$$$";
    public static final String ARTIFACT_KEY_SEPARATOR_REGEX = "[、,，\\r\\n]+";

    private ToolArtifactFormatter() {
    }

    public static String appendToolArtifactSummary(String content, List<ToolArtifactBinding> bindings) {
        if (CollectionUtils.isEmpty(bindings)) {
            return content;
        }
        String summary = formatToolArtifactSummary(bindings);
        if (StringUtils.isBlank(summary)) {
            return content;
        }
        if (StringUtils.isBlank(content)) {
            return "关联文件：\n" + summary;
        }
        return content + "\n\n关联文件：\n" + summary;
    }

    public static String formatToolArtifactSummary(List<ToolArtifactBinding> bindings) {
        if (CollectionUtils.isEmpty(bindings)) {
            return "";
        }
        return bindings.stream()
                .map(ToolArtifactFormatter::formatToolArtifactLine)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    public static String formatSummaryContext(List<ToolArtifactBinding> bindings) {
        if (CollectionUtils.isEmpty(bindings)) {
            return "";
        }
        return bindings.stream()
                .map(ToolArtifactFormatter::formatSummaryContextLine)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    public static String buildArtifactKey(ToolArtifactBinding binding) {
        if (binding == null) {
            return "";
        }
        return buildArtifactKey(binding.getSource(), binding.getFile());
    }

    public static String buildArtifactKey(ToolArtifactSource source, File file) {
        if (source == null || file == null) {
            return "";
        }
        return StringUtils.defaultString(source.getToolCallId()) + ARTIFACT_KEY_SEPARATOR + StringUtils.defaultString(file.getFileName());
    }

    public static String resolveFileUrl(File file) {
        if (file == null) {
            return "";
        }
        if (StringUtils.isNotBlank(file.getOriginOssUrl())) {
            return file.getOriginOssUrl();
        }
        if (StringUtils.isNotBlank(file.getOriginDomainUrl())) {
            return file.getOriginDomainUrl();
        }
        if (StringUtils.isNotBlank(file.getOssUrl())) {
            return file.getOssUrl();
        }
        return StringUtils.defaultString(file.getDomainUrl());
    }

    private static String formatToolArtifactLine(ToolArtifactBinding binding) {
        if (binding == null || binding.getFile() == null) {
            return "";
        }
        File file = binding.getFile();
        return String.format("- artifactKey:%s fileName:%s fileDesc:%s",
                buildArtifactKey(binding),
                StringUtils.defaultString(file.getFileName()),
                StringUtils.defaultString(StringUtils.abbreviate(file.getDescription(), 80)));
    }

    private static String formatSummaryContextLine(ToolArtifactBinding binding) {
        if (binding == null || binding.getFile() == null || binding.getSource() == null) {
            return "";
        }
        File file = binding.getFile();
        ToolArtifactSource source = binding.getSource();
        return String.format("artifactKey:%s toolCallId:%s toolName:%s fileName:%s fileDesc:%s fileUrl:%s",
                buildArtifactKey(binding),
                StringUtils.defaultString(source.getToolCallId()),
                StringUtils.defaultString(source.getToolName()),
                StringUtils.defaultString(file.getFileName()),
                StringUtils.defaultString(StringUtils.abbreviate(file.getDescription(), 120)),
                resolveFileUrl(file));
    }
}
