package org.wwz.ai.application.catalog.skill;

/** 技能 ZIP 包命令。 */
public record SkillPackageCommand(byte[] zipBytes, String originalFilename) {

    public static final int MAX_PACKAGE_BYTES = 32 * 1024 * 1024;
}
