package org.wwz.ai.domain.agent.runtime.tool.skill;

/**
 * 依据来源信任等级决定威胁扫描结果的处置方式。
 * <p>
 * 策略（参照 hermes-agent {@code tools/skills_guard.py::INSTALL_POLICY} 的 trust × verdict 思路）：
 * <ul>
 *   <li>{@link SkillTrustLevel#BUILTIN} / {@link SkillTrustLevel#LOCAL}：放行，扫描结果只记录；</li>
 *   <li>{@link SkillTrustLevel#UPLOAD}：始终只告警，不阻断（避免误伤用户自己的 skill）；</li>
 *   <li>{@link SkillTrustLevel#URL}：出现 BLOCK 级命中直接拒绝，其余告警。</li>
 * </ul>
 */
public final class SkillInstallPolicy {

    public enum Decision {
        ALLOW, WARN, BLOCK
    }

    private SkillInstallPolicy() {
    }

    public static Decision decide(SkillTrustLevel level, SkillThreatScanner.Report report) {
        SkillTrustLevel trustLevel = level == null ? SkillTrustLevel.UPLOAD : level;
        if (report == null || report.clean()) {
            return Decision.ALLOW;
        }
        return switch (trustLevel) {
            case BUILTIN, LOCAL -> Decision.ALLOW;
            case UPLOAD -> Decision.WARN;
            case URL -> report.blocked() ? Decision.BLOCK : Decision.WARN;
        };
    }
}
