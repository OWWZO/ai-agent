package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 技能路径契约与 SKILL.md 占位符替换。
 *
 * <p>对外 / 沙箱统一：{@code skills/&lt;技能名&gt;}（虚拟路径；workspace 工具映射到 runtime 库，
 * bash 执行时在沙箱 cwd 下物化为同名相对路径）。
 */
@Component
public class SkillRuntimeLayout {

    /** 虚拟路径与沙箱相对根，统一 skills/ */
    public static final String RELATIVE_ROOT = "skills";
    public static final String PLACEHOLDER_SKILL_DIR = "${SKILL_DIR}";
    public static final String PLACEHOLDER_PYTHON = "${PYTHON}";

    private static final Pattern UNSAFE_SEGMENT = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final int MAX_SEGMENT_LEN = 80;
    private static final String FALLBACK_SEGMENT = "skill";

    private final String python;

    public SkillRuntimeLayout(SkillRuntimeOptions skillRuntimeOptions) {
        String configured = skillRuntimeOptions == null ? null : skillRuntimeOptions.getRuntimePython();
        this.python = (configured == null || configured.isBlank()) ? "python" : configured.trim();
    }

    public String getPython() {
        return python;
    }

    public String render(String skillName, String content) {
        if (content == null || content.isEmpty()) {
            return content == null ? "" : content;
        }
        return content
                .replace(PLACEHOLDER_SKILL_DIR, dirOf(skillName))
                .replace(PLACEHOLDER_PYTHON, python);
    }

    public String relativeDirOf(String skillName) {
        return RELATIVE_ROOT + "/" + segmentOf(skillName);
    }

    public String dirOf(String skillName) {
        return relativeDirOf(skillName);
    }

    public String segmentOf(String name) {
        if (name == null || name.isBlank()) {
            return FALLBACK_SEGMENT;
        }
        String cleaned = UNSAFE_SEGMENT.matcher(name.trim()).replaceAll("-");
        if (cleaned.length() > MAX_SEGMENT_LEN) {
            cleaned = cleaned.substring(0, MAX_SEGMENT_LEN);
        }
        cleaned = cleaned.replaceAll("^[.-]+|[.-]+$", "");
        return cleaned.isBlank() ? FALLBACK_SEGMENT : cleaned;
    }
}
