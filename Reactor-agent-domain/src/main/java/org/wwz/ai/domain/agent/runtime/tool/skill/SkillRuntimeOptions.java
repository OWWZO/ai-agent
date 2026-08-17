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

    /**
     * 是否挂载 bash 工具，并在会话工作区 materialize skill 后执行脚本。
     * 需要 workspace 已启用且会话有 workspaceRoot。
     */
    @Builder.Default
    private boolean sandboxBashEnabled = true;

    /**
     * 是否挂载遗留 skill_author（默认关；创作走 workspace 虚拟 skills/ + bash）。
     */
    @Builder.Default
    private boolean authoringEnabled = false;

    /** 手册占位符 ${PYTHON} 与默认解释器命令。 */
    @Builder.Default
    private String runtimePython = "python";

    /** bash 默认超时（秒）。 */
    @Builder.Default
    private int bashTimeoutSec = 120;

    /** bash 允许的最大超时（秒）。 */
    @Builder.Default
    private int bashMaxTimeoutSec = 600;

    /** 单流 stdout/stderr 最大字符数（超出头尾截断）。 */
    @Builder.Default
    private int bashOutputMaxChars = 64_000;
}
