package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.dto.skill.ScriptRunnerToolResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件引用模型转换器。
 * 统一把各工具内部 DTO 收口成领域层稳定的 ToolFileRef。
 */
public final class ToolFileRefMapper {

    private ToolFileRefMapper() {
    }

    public static List<ToolFileRef> fromCodeInterpreterFileInfo(List<CodeInterpreterResponse.FileInfo> fileInfo) {
        List<ToolFileRef> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(fileInfo)) {
            return result;
        }
        for (CodeInterpreterResponse.FileInfo item : fileInfo) {
            if (item == null) {
                continue;
            }
            result.add(ToolFileRef.builder()
                    .fileName(item.getFileName())
                    .ossUrl(item.getOssUrl())
                    .domainUrl(item.getDomainUrl())
                    .downloadUrl(item.getOssUrl())
                    .previewUrl(item.getDomainUrl())
                    .fileSize(item.getFileSize() == null ? null : item.getFileSize().longValue())
                    .build());
        }
        return result;
    }

    public static List<ToolFileRef> fromScriptRunnerFileInfo(List<ScriptRunnerToolResponse.FileInfo> fileInfo) {
        List<ToolFileRef> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(fileInfo)) {
            return result;
        }
        for (ScriptRunnerToolResponse.FileInfo item : fileInfo) {
            if (item == null) {
                continue;
            }
            result.add(ToolFileRef.builder()
                    .fileName(item.getFileName())
                    .ossUrl(item.getOssUrl())
                    .domainUrl(item.getDomainUrl())
                    .downloadUrl(StringUtils.defaultIfBlank(item.getDownloadUrl(), item.getOssUrl()))
                    .previewUrl(item.getDomainUrl())
                    .fileSize(item.getFileSize() == null ? null : item.getFileSize().longValue())
                    .build());
        }
        return result;
    }

    public static List<ToolFileRef> fromGenericFileInfo(List<Map<String, Object>> fileInfo) {
        List<ToolFileRef> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(fileInfo)) {
            return result;
        }
        for (Map<String, Object> item : fileInfo) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            result.add(ToolFileRef.builder()
                    .fileName(valueAsString(item.get("fileName")))
                    .ossUrl(valueAsString(item.get("ossUrl")))
                    .domainUrl(valueAsString(item.get("domainUrl")))
                    .downloadUrl(valueAsString(item.get("downloadUrl")))
                    .previewUrl(valueAsString(item.get("previewUrl")))
                    .fileSize(valueAsLong(item.get("fileSize")))
                    .mimeType(valueAsString(item.get("mimeType")))
                    .build());
        }
        return result;
    }

    private static String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long valueAsLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
