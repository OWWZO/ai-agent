package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill 脚本定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillScriptDefinition {

    private String scriptName;

    private String relativePath;

    private Path absolutePath;

    private String runtime;

    private String description;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * 构建脚本摘要，供 skill_tool 直接展示。
     */
    public String toSummaryLine() {
        String desc = (description == null || description.isBlank()) ? "未提供说明" : description;
        return String.format("- %s | runtime=%s | path=%s | %s",
                scriptName,
                runtime,
                relativePath,
                desc);
    }
}
