package org.wwz.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionLoader;

import javax.annotation.Resource;

/**
 * 启动完成后加载 DB 中的可配置子 Agent 定义到 {@link org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry}。
 */
@Slf4j
@Configuration
public class SubAgentDefinitionAutoConfiguration implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private SubAgentDefinitionLoader subAgentDefinitionLoader;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            int count = subAgentDefinitionLoader.reload();
            log.info("SubAgent 定义启动加载完成 configuredCount={}", count);
        } catch (Exception e) {
            log.error("SubAgent 定义启动加载失败", e);
        }
    }
}
