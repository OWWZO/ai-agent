package org.wwz.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;

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
                .defaultScriptTimeoutSeconds(properties.getDefaultScriptTimeoutSeconds())
                .build();
    }
}
