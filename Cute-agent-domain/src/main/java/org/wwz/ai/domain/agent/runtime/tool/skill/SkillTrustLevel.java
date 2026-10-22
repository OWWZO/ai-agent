package org.wwz.ai.domain.agent.runtime.tool.skill;

/**
 * skill 来源信任等级，决定威胁扫描结果的处置方式。
 * <p>
 * 参照 hermes-agent {@code tools/skills_guard.py::_resolve_trust_level} 的分级思路，
 * 但简化为与 {@link SkillRef#getSource()} 对齐的四档。
 */
public enum SkillTrustLevel {

    /** 随应用发布的内置 skill：不扫描。 */
    BUILTIN,

    /** 本地文件系统已有的 skill：不扫描。 */
    LOCAL,

    /** 用户上传（zip / 粘贴 markdown）：扫描但只告警，不阻断。 */
    UPLOAD,

    /** 从 URL 下载：扫描且 BLOCK 级命中直接拒绝。 */
    URL;

    public static SkillTrustLevel fromSource(String source) {
        if (source == null || source.isBlank()) {
            return BUILTIN;
        }
        return switch (source.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "url" -> URL;
            case "upload" -> UPLOAD;
            case "local" -> LOCAL;
            default -> BUILTIN;
        };
    }
}
