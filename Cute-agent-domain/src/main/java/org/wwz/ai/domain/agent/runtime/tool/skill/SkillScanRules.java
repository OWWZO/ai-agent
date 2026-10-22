package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * skill 扫描的目录规则，参照 hermes-agent
 * {@code agent/skill_utils.py} 的 {@code EXCLUDED_SKILL_DIRS} / {@code SKILL_SUPPORT_DIRS}。
 */
public final class SkillScanRules {

    /** 依赖、版本控制、缓存目录：不扫描，避免把第三方内容当成 skill。 */
    public static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", ".github", ".gitlab", ".idea", ".vscode", ".hub", ".archive",
            ".venv", "venv", "env", "node_modules", "site-packages", "__pycache__",
            ".tox", ".nox", ".pytest_cache", ".mypy_cache", ".ruff_cache",
            "target", "dist", "build", "out", ".gradle", ".mvn");

    /**
     * 渐进披露支撑目录：位于含 SKILL.md 的 skill 根下时，不再向下扫描，
     * 因为其中可能存有归档的旧 skill 包（references/old-skill/SKILL.md）。
     */
    public static final Set<String> SUPPORT_DIRS = Set.of(
            "references", "templates", "assets", "scripts", "examples");

    public static final int DEFAULT_MAX_SCAN_DEPTH = 3;

    private SkillScanRules() {
    }

    public static boolean isExcluded(String directoryName) {
        return directoryName != null && EXCLUDED_DIRS.contains(directoryName);
    }

    public static boolean isSupportDir(String directoryName) {
        return directoryName != null && SUPPORT_DIRS.contains(directoryName);
    }

    /**
     * 默认排除集的可变副本，供配置覆盖时作为起点。
     */
    public static Set<String> defaultExcludedDirs() {
        return new LinkedHashSet<>(EXCLUDED_DIRS);
    }
}
