package org.wwz.ai.trigger.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.types.agent.config.AgentBrowserProperties;

@Configuration
@EnableConfigurationProperties(AgentBrowserProperties.class)
public class AgentBrowserPropertiesConfig {
}
