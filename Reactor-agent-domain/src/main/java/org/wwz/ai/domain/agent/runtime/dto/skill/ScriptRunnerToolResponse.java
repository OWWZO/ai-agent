package org.wwz.ai.domain.agent.runtime.dto.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * script_runner_tool 返回结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptRunnerToolResponse {

    private String requestId;

    private String skillName;

    private String scriptName;

    private String runtime;

    private Boolean success;

    private Integer exitCode;

    private String stdout;

    private String stderr;

    private String summary;

    @Builder.Default
    private List<FileInfo> fileInfo = new ArrayList<>();

    /**
     * 固定文本格式，便于模型直接消费。
     */
    public String toDisplayText() {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("技能：").append(skillName).append("\n");
        resultBuilder.append("脚本：").append(scriptName).append("\n");
        resultBuilder.append("运行时：").append(runtime).append("\n");
        resultBuilder.append("是否成功：").append(Boolean.TRUE.equals(success)).append("\n");
        resultBuilder.append("退出码：").append(exitCode == null ? -1 : exitCode).append("\n");
        resultBuilder.append("摘要：").append(summary == null ? "" : summary).append("\n");
        resultBuilder.append("stdout:\n").append(stdout == null ? "" : stdout).append("\n");
        resultBuilder.append("stderr:\n").append(stderr == null ? "" : stderr).append("\n");
        resultBuilder.append("产出文件：\n");
        if (fileInfo == null || fileInfo.isEmpty()) {
            resultBuilder.append("- （无）\n");
        } else {
            for (FileInfo file : fileInfo) {
                resultBuilder.append("- ")
                        .append(file.getFileName())
                        .append(" | ")
                        .append(file.getDomainUrl())
                        .append("\n");
            }
        }
        return resultBuilder.toString();
    }

    @Override
    public String toString() {
        return toDisplayText();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileInfo {
        private String fileName;
        private String ossUrl;
        private String domainUrl;
        private String downloadUrl;
        private Integer fileSize;
    }
}
