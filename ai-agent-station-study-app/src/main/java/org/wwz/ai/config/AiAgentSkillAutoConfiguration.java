package org.wwz.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.reactor.agent.tool.skill.SkillRuntimeOptions;

/**
 * Skill 自动装配配置
 */
@Configuration
@EnableConfigurationProperties(AiAgentSkillProperties.class)
public class AiAgentSkillAutoConfiguration {

    @Bean
    public SkillRuntimeOptions skillRuntimeOptions(AiAgentSkillProperties properties) {
        return SkillRuntimeOptions.builder()
                .enabled(properties.isEnabled())
                .directories(properties.getDirectories())
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
