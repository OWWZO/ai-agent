package org.wwz.ai.trigger.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.types.agent.config.KernelBrowserProperties;

@Configuration
@EnableConfigurationProperties(KernelBrowserProperties.class)
public class KernelBrowserPropertiesConfig {
}
