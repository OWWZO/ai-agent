package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    // ── 扫描发现 ─────────────────────────────────────────────────────────

    /**
     * 是否递归扫描 skill 根目录。
     * 默认 false，与历史行为一致（只扫一层）；开启后支持 {@code category/name/SKILL.md} 结构。
     */
    @Builder.Default
    private boolean recursiveScan = false;

    /** 递归扫描的最大目录深度（仅在 {@link #recursiveScan} 为 true 时生效）。 */
    @Builder.Default
    private int maxScanDepth = SkillScanRules.DEFAULT_MAX_SCAN_DEPTH;

    /** 扫描时忽略的目录名；默认取 {@link SkillScanRules#EXCLUDED_DIRS}。 */
    @Builder.Default
    private Set<String> excludedDirs = SkillScanRules.defaultExcludedDirs();

    /** 扫描结果缓存 TTL（秒）。<= 0 表示每次强制全量扫描。 */
    @Builder.Default
    private int scanCacheTtlSeconds = 30;

    // ── 导入安全 ─────────────────────────────────────────────────────────

    /** 是否对导入的 skill 做静态威胁扫描。 */
    @Builder.Default
    private boolean threatScanEnabled = true;

    // ── prompt 索引 ──────────────────────────────────────────────────────

    /** system prompt 中技能索引的总字符预算；超出后按档位降级。 <= 0 表示不限制。 */
    @Builder.Default
    private int promptIndexMaxChars = 6000;

    /** 技能索引中单条描述的最大字符数。 */
    @Builder.Default
    private int promptIndexDescriptionChars = 80;
}
