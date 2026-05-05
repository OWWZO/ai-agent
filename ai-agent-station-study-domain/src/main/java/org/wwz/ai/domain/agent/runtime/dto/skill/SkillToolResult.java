package org.wwz.ai.domain.agent.runtime.dto.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * skill_tool 的展示结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillToolResult {

    private String name;

    private String description;

    private Path basePath;

    private String content;

    @Builder.Default
    private List<String> availableScripts = Collections.emptyList();

    /**
     * 输出给模型看的固定文本格式。
     */
    public String toDisplayText() {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("技能名称：").append(name).append("\n");
        resultBuilder.append("技能描述：").append(description).append("\n");
        resultBuilder.append("技能目录：").append(basePath).append("\n\n");
        resultBuilder.append("可用脚本：\n");
        if (availableScripts == null || availableScripts.isEmpty()) {
            resultBuilder.append("- （无）\n");
        } else {
            for (String availableScript : availableScripts) {
                resultBuilder.append(availableScript).append("\n");
            }
        }
        resultBuilder.append("\n===== SKILL.md =====\n");
        resultBuilder.append(content == null ? "" : content);
        return resultBuilder.toString();
    }

    @Override
    public String toString() {
        return toDisplayText();
    }
}
