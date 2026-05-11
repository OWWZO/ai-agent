package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 运行时配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillRuntimeOptions {

    private boolean enabled;

    @Builder.Default
    private List<String> directories = new ArrayList<>();

    @Builder.Default
    private boolean reactEnabled = true;

    @Builder.Default
    private boolean planSolveEnabled = true;

    @Builder.Default
    private int maxReadChars = 12000;

    @Builder.Default
    private int maxListEntries = 200;

    @Builder.Default
    private int maxGlobResults = 100;

    @Builder.Default
    private int maxGrepMatches = 100;

    @Builder.Default
    private int defaultScriptTimeoutSeconds = 120;
}
