package org.wwz.ai.domain.agent.runtime.tool.common;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;

/**
 * 报告产物命名规则：文件名以 reportFileName 为基准，不从完整任务文本派生。
 */
final class DeepSearchFileNamePolicy {

    static final int MAX_REPORT_FILE_NAME_LENGTH = 20;
    private static final String DEFAULT_REPORT_FILE_NAME = "深度搜索报告.md";

    private DeepSearchFileNamePolicy() {
    }

    static String buildSearchResultFileName(String reportFileName) {
        int extensionStart = reportFileName.lastIndexOf('.');
        String baseName = extensionStart > 0 ? reportFileName.substring(0, extensionStart) : reportFileName;
        return baseName + "_search_result.txt";
    }

    static String resolveReportFileName(String requestedFileName) {
        return resolveReportFileName(requestedFileName, DEFAULT_REPORT_FILE_NAME);
    }

    static String resolveReportFileName(String requestedFileName, String fallbackFileName) {
        String fileName = sanitizeReportFileName(requestedFileName);
        if (validateReportFileName(requestedFileName) == null) {
            return fileName;
        }
        String fallback = sanitizeReportFileName(fallbackFileName);
        return validateReportFileName(fallback) == null ? fallback : DEFAULT_REPORT_FILE_NAME;
    }

    static String validateReportFileName(String requestedFileName) {
        String fileName = sanitizeReportFileName(requestedFileName);
        if (StringUtils.isBlank(fileName)) {
            return "reportFileName不能为空。";
        }
        if (fileName.codePointCount(0, fileName.length()) > MAX_REPORT_FILE_NAME_LENGTH) {
            return "reportFileName不能超过20个字符（含扩展名）。";
        }
        return null;
    }

    private static String sanitizeReportFileName(String requestedFileName) {
        String fileName = StringUtils.trimToEmpty(requestedFileName)
                .replace('/', '_')
                .replace('\\', '_');
        fileName = StringUtil.removeSpecialChars(fileName);
        if (StringUtils.isBlank(fileName)) {
            return "";
        }
        return fileName.toLowerCase().endsWith(".md") ? fileName : fileName + ".md";
    }
}
