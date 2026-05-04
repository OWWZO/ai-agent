package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.wwz.ai.Application;
import org.wwz.ai.application.agent.execute.IExecuteStrategy;
import org.wwz.ai.application.agent.execute.workflow.FlowAgentExecuteStrategy;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fix 链路回归测试。
 * 同时校验 Bean 装配仍然可用，且不会误依赖 skill 装配组件。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class FixedAgentExecuteStrategyTest {

    @Resource(name = "flowAgentExecuteStrategy")
    private IExecuteStrategy fixedAgentExecuteStrategy;

    @Test
    public void shouldLoadFixedAgentExecuteStrategy() {
        Assert.assertNotNull("固定执行策略 Bean 不应为空", fixedAgentExecuteStrategy);
    }

    @Test
    public void shouldNotDependOnSkillAssemblyComponents() {
        List<String> fieldTypeNames = Arrays.stream(FlowAgentExecuteStrategy.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getSimpleName)
                .collect(Collectors.toList());

        Assert.assertFalse(fieldTypeNames.contains("AgentToolCollectionFactory"));
        Assert.assertFalse(fieldTypeNames.contains("SkillRegistry"));
    }

}
