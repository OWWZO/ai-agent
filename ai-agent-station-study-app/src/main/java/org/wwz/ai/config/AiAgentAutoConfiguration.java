package org.wwz.ai.config;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.application.agent.armory.IArmoryService;
import org.wwz.ai.domain.agent.model.valobj.AiAgentVO;

import javax.annotation.Resource;
import java.util.List;

/**
 * AI Agent 自动装配配置类
 * 在 Spring Boot 应用启动完成后，根据配置触发应用层装配入口。
 * 装配主归属留在 case 层，app 只负责启动时机与 Bean wiring。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({AiAgentAutoConfigProperties.class, AiAgentSkillProperties.class})
@ConditionalOnProperty(prefix = "spring.ai.agent.auto-config", name = "enabled", havingValue = "true")
public class AiAgentAutoConfiguration implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Resource
    private IArmoryService armoryService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            log.info("AI Agent 自动装配开始，配置: {}", aiAgentAutoConfigProperties);

            // 检查配置是否有效
            if (!aiAgentAutoConfigProperties.isEnabled()) {
                log.info("AI Agent 自动装配未启用");
                return;
            }

            // 运行时策略工厂仍由 Spring 提前装配，这里只触发应用层入口，不在 app 内拼业务逻辑。
            List<AiAgentVO> aiAgentVOS = armoryService.acceptArmoryAllAvailableAgents();

            log.info("AI Agent 自动装配完成 {}", JSON.toJSONString(aiAgentVOS));
        } catch (Exception e) {
            log.error("AI Agent 自动装配失败", e);
        }
    }

}
