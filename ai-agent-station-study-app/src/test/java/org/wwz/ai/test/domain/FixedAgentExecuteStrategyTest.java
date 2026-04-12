package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.wwz.ai.domain.agent.service.execute.fixed.FixedAgentExecuteStrategy;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fix 链路结构回归测试。
 */
public class FixedAgentExecuteStrategyTest {

    @Test
    public void shouldNotDependOnSkillAssemblyComponents() {
        List<String> fieldTypeNames = Arrays.stream(FixedAgentExecuteStrategy.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getSimpleName)
                .collect(Collectors.toList());

        Assert.assertFalse(fieldTypeNames.contains("AgentToolCollectionFactory"));
        Assert.assertFalse(fieldTypeNames.contains("SkillRegistry"));
    }

}
