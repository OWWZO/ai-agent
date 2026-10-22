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

    /**
     * 工具类不允许实例化，所有格式化能力均通过静态方法提供。
     */
    private ToolArtifactFormatter() {
    }

    /**
     * 将工具产物摘要追加到已有内容末尾；没有有效产物时保持原内容不变。
     *
     * @param content 需要追加摘要的原始内容
     * @param bindings 工具调用与文件产物的绑定关系
     * @return 追加“关联文件”段落后的内容
     */
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

    /**
     * 将产物绑定列表格式化为面向用户展示的多行摘要，并过滤无效绑定。
     *
     * @param bindings 工具调用与文件产物的绑定关系
     * @return 每个产物一行的摘要文本；输入为空或没有有效产物时返回空字符串
     */
    public static String formatToolArtifactSummary(List<ToolArtifactBinding> bindings) {
        if (CollectionUtils.isEmpty(bindings)) {
            return "";
        }
        return bindings.stream()
                .map(ToolArtifactFormatter::formatToolArtifactLine)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 将产物绑定列表格式化为总结 Agent 使用的上下文，保留工具调用、文件描述和可访问 URL。
     *
     * @param bindings 工具调用与文件产物的绑定关系
     * @return 每个产物一行的结构化上下文；输入为空或没有有效产物时返回空字符串
     */
    public static String formatSummaryContext(List<ToolArtifactBinding> bindings) {
        if (CollectionUtils.isEmpty(bindings)) {
            return "";
        }
        return bindings.stream()
                .map(ToolArtifactFormatter::formatSummaryContextLine)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 从产物绑定中构造稳定的产物键，便于在摘要、总结上下文和后续交付引用之间关联同一文件。
     *
     * @param binding 工具调用与文件产物的绑定关系
     * @return 由工具调用 ID 和文件名组成的产物键；绑定为空时返回空字符串
     */
    public static String buildArtifactKey(ToolArtifactBinding binding) {
        if (binding == null) {
            return "";
        }
        return buildArtifactKey(binding.getSource(), binding.getFile());
    }

    /**
     * 使用工具调用 ID 与文件名拼接产物键；任一必要对象为空时不生成不完整的键。
     *
     * @param source 工具调用来源
     * @param file 文件产物
     * @return 使用固定分隔符拼接的产物键；参数不完整时返回空字符串
     */
    public static String buildArtifactKey(ToolArtifactSource source, File file) {
        if (source == null || file == null) {
            return "";
        }
        return StringUtils.defaultString(source.getToolCallId()) + ARTIFACT_KEY_SEPARATOR + StringUtils.defaultString(file.getFileName());
    }

    public static String normalizeWorkspacePath(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String path = raw.replace('\\', '/').trim();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    public static String resolveWorkspacePath(File file) {
        if (file == null) {
            return "";
        }
        return normalizeWorkspacePath(StringUtils.defaultIfBlank(file.getRelativePath(),
                StringUtils.defaultIfBlank(file.getOriginFileName(), file.getFileName())));
    }

    public static String workspaceBasename(String path) {
        String normalized = normalizeWorkspacePath(path);
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    /**
     * 解析文件对外可用的访问地址，按原始 OSS 地址、原始域名地址、OSS 地址、域名地址依次回退。
     *
     * @param file 文件产物
     * @return 文件访问地址；文件为空或所有地址均为空时返回空字符串
     */
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

    /**
     * 将单个文件产物格式化为用户可读的摘要行，并限制描述长度避免摘要过长。
     *
     * @param binding 工具调用与文件产物的绑定关系
     * @return 单行产物摘要；绑定或文件为空时返回空字符串
     */
    private static String formatToolArtifactLine(ToolArtifactBinding binding) {
        if (binding == null || binding.getFile() == null) {
            return "";
        }
        File file = binding.getFile();
        return String.format("- filePath:%s fileName:%s fileDesc:%s",
                resolveWorkspacePath(file),
                StringUtils.defaultString(file.getFileName()),
                StringUtils.defaultString(StringUtils.abbreviate(file.getDescription(), 80)));
    }

    /**
     * 将单个文件产物格式化为总结上下文行，提供总结阶段完成文件引用所需的完整元数据。
     *
     * @param binding 工具调用与文件产物的绑定关系
     * @return 单行结构化上下文；绑定、来源或文件为空时返回空字符串
     */
    private static String formatSummaryContextLine(ToolArtifactBinding binding) {
        if (binding == null || binding.getFile() == null || binding.getSource() == null) {
            return "";
        }
        File file = binding.getFile();
        ToolArtifactSource source = binding.getSource();
        return String.format("filePath:%s toolName:%s fileName:%s fileDesc:%s fileUrl:%s",
                resolveWorkspacePath(file),
                StringUtils.defaultString(source.getToolName()),
                StringUtils.defaultString(file.getFileName()),
                StringUtils.defaultString(StringUtils.abbreviate(file.getDescription(), 120)),
                resolveFileUrl(file));
    }
}
