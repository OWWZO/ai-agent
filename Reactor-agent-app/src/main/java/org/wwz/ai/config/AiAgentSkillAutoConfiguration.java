package org.wwz.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScanRules;

/**
 * Skill 自动装配配置
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiAgentSkillProperties.class)
public class AiAgentSkillAutoConfiguration {

    private final SkillDirectoryResolver skillDirectoryResolver;

    public AiAgentSkillAutoConfiguration(SkillDirectoryResolver skillDirectoryResolver) {
        this.skillDirectoryResolver = skillDirectoryResolver;
    }

    @Bean
    public SkillRuntimeOptions skillRuntimeOptions(AiAgentSkillProperties properties) {
        java.util.List<String> resolvedDirectories = skillDirectoryResolver.resolve(properties.getDirectories());
        log.info("skill runtime options prepared, enabled={}, configuredDirectories={}, resolvedDirectories={}",
                properties.isEnabled(), properties.getDirectories(), resolvedDirectories);
        return SkillRuntimeOptions.builder()
                .enabled(properties.isEnabled())
                .directories(resolvedDirectories)
                .reactEnabled(properties.isReactEnabled())
                .planSolveEnabled(properties.isPlanSolveEnabled())
                .maxReadChars(properties.getMaxReadChars())
                .maxListEntries(properties.getMaxListEntries())
                .maxGlobResults(properties.getMaxGlobResults())
                .maxGrepMatches(properties.getMaxGrepMatches())
                .sandboxBashEnabled(properties.isSandboxBashEnabled())
                .runtimePython(properties.getRuntimePython())
                .bashTimeoutSec(properties.getBashTimeoutSec())
                .bashMaxTimeoutSec(properties.getBashMaxTimeoutSec())
                .bashOutputMaxChars(properties.getBashOutputMaxChars())
                .recursiveScan(properties.isRecursiveScan())
                .maxScanDepth(properties.getMaxScanDepth())
                .excludedDirs(resolveExcludedDirs(properties.getExcludedDirs()))
                .scanCacheTtlSeconds(properties.getScanCacheTtlSeconds())
                .threatScanEnabled(properties.isThreatScanEnabled())
                .promptIndexMaxChars(properties.getPromptIndexMaxChars())
                .promptIndexDescriptionChars(properties.getPromptIndexDescriptionChars())
                .build();
    }

    /**
     * 配置未指定 excludedDirs 时回落到内置默认集，避免用户漏配导致 .git / node_modules 被当成 skill 扫描。
     */
    private java.util.Set<String> resolveExcludedDirs(java.util.Set<String> configured) {
        if (configured == null || configured.isEmpty()) {
            return SkillScanRules.defaultExcludedDirs();
        }
        return new java.util.LinkedHashSet<>(configured);
    }
}
