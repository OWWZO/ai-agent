package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.config.AiAgentSkillAutoConfiguration;
import org.wwz.ai.config.AiAgentSkillProperties;
import org.wwz.ai.config.SkillDirectoryResolver;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;

import java.nio.file.Path;
import java.util.List;

/**
 * Skill 自动装配测试。
 */
public class AiAgentSkillAutoConfigurationTest {

    @Test
    public void shouldResolveDirectoriesBeforeBuildingRuntimeOptions() {
        AiAgentSkillProperties properties = new AiAgentSkillProperties();
        properties.setEnabled(true);
        properties.setDirectories(List.of("D:/invalid/project/runtime/skills"));

        SkillDirectoryResolver resolver = new SkillDirectoryResolver(Path.of("D:/repo/Reactor-agent/Reactor-agent-app"));
        AiAgentSkillAutoConfiguration autoConfiguration = new AiAgentSkillAutoConfiguration(resolver);

        SkillRuntimeOptions options = autoConfiguration.skillRuntimeOptions(properties);

        Assert.assertTrue(options.isEnabled());
        Assert.assertTrue(options.getDirectories().isEmpty());
    }
}
