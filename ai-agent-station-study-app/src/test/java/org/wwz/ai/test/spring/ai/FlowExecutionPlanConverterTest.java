package org.wwz.ai.test.spring.ai;

import org.junit.Test;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.wwz.ai.domain.agent.model.entity.ExecutionPlanStep;

import java.util.List;

import static org.junit.Assert.*;

public class FlowExecutionPlanConverterTest {

    @Test
    public void should_convert_mixed_content_json_to_execution_plan_steps() {
        // 使用 Spring AI 的 BeanOutputConverter 将 JSON 数组结构化为强类型对象，验证结构化输出链路可用
        BeanOutputConverter<List<ExecutionPlanStep>> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {});

        String mixedOutput = """
                AI 助手: 生成执行计划
                下面是执行计划（请忽略解释性文本）：
                [
                  {
                    "stepNumber": 1,
                    "stepName": "处理用户问候",
                    "actionType": "TOOL",
                    "description": "响应用户的你好问候，生成友好回复",
                    "toolName": "auto_analysis",
                    "toolParams": { "task": "你好", "modelCodeList": [] },
                    "reasoning": "使用工具获取结构化结果，后续由 LLM 汇总输出",
                    "dependencies": [],
                    "expectedOutput": "工具执行返回的分析结果"
                  }
                ]
                """;

        // 模拟 Step2 的兜底逻辑：截取最外层 JSON 数组片段，避免前后夹带文本导致解析失败
        int start = mixedOutput.indexOf('[');
        int end = mixedOutput.lastIndexOf(']');
        assertTrue(start >= 0 && end > start);
        String jsonArray = mixedOutput.substring(start, end + 1);

        List<ExecutionPlanStep> steps = converter.convert(jsonArray);
        assertNotNull(steps);
        assertEquals(1, steps.size());
        assertEquals(1, steps.get(0).stepNumber());
        assertEquals("TOOL", steps.get(0).actionType());
        assertEquals("auto_analysis", steps.get(0).toolName());
    }
}

