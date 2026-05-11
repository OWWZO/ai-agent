package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

/**
 * 数字员工提示词格式化测试。
 */
public class DigitalEmployeePromptFormattingTest {

    @Test
    public void shouldOnlyKeepConciseToolDescriptionsForDigitalEmployeePrompt() {
        ToolCollection toolCollection = new ToolCollection();
        toolCollection.addTool(mockTool(
                "skill_tool",
                "这是一个 skill 读取工具，用于按技能名称加载 SKILL.md 正文。\n当前可用 skills：\n- demo: 超长细节说明"
        ));
        toolCollection.addTool(mockTool(
                "report_tool",
                "这是一个报告生成工具，可以输出 HTML、Markdown、PPT，并附带很多额外实现细节很多额外实现细节很多额外实现细节很多额外实现细节很多额外实现细节很多额外实现细节很多额外实现细节"
        ));

        AgentContext context = AgentContext.builder()
                .requestId("req-digital-employee-001")
                .query("分析用户问题")
                .toolCollection(toolCollection)
                .build();
        toolCollection.setAgentContext(context);

        ReActAgent agent = new TestReActAgent();
        agent.setContext(context);
        agent.setDigitalEmployeePrompt("工具列表如下：\n{{ToolsDesc}}\n任务：{{task}}\n问题：{{query}}");

        String prompt = ReflectionTestUtils.invokeMethod(agent, "formatSystemPrompt", "生成报告");

        Assert.assertNotNull(prompt);
        Assert.assertTrue(prompt, prompt.contains("工具名：skill_tool 工具描述：这是一个 skill 读取工具"));
        Assert.assertFalse(prompt, prompt.contains("当前可用 skills"));
        Assert.assertFalse(prompt, prompt.contains("超长细节说明"));
        Assert.assertFalse(prompt, prompt.contains("很多额外实现细节很多额外实现细节很多额外实现细节很多额外实现细节很多额外实现细节"));
    }

    private BaseTool mockTool(String name, String description) {
        BaseTool tool = Mockito.mock(BaseTool.class);
        Mockito.when(tool.getName()).thenReturn(name);
        Mockito.when(tool.getDescription()).thenReturn(description);
        return tool;
    }

    private static class TestReActAgent extends ReActAgent {

        @Override
        public boolean think() {
            return false;
        }

        @Override
        public String act() {
            return "";
        }
    }
}
