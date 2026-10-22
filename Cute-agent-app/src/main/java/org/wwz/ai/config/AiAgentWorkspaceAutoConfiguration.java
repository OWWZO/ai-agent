package org.wwz.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;

/**
 * 工作区工具自动装配。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiAgentWorkspaceProperties.class)
public class AiAgentWorkspaceAutoConfiguration {

    @Bean
    public WorkspaceRuntimeOptions workspaceRuntimeOptions(AiAgentWorkspaceProperties properties) {
        log.info("workspace runtime options prepared, enabled={}, rootTemplate={}",
                properties.isEnabled(), properties.getRootTemplate());
        return WorkspaceRuntimeOptions.builder()
                .enabled(properties.isEnabled())
                .rootTemplate(properties.getRootTemplate())
                .maxReadChars(properties.getMaxReadChars())
                .maxListEntries(properties.getMaxListEntries())
                .maxGlobResults(properties.getMaxGlobResults())
                .maxGrepMatches(properties.getMaxGrepMatches())
                .maxWriteChars(properties.getMaxWriteChars())
                .build();
    }
}
