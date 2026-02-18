package org.wwz.ai.domain.agent.model.entity;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Map;

/**
 * 智能执行计划步骤 Record
 * 用于 Spring AI 的 BeanOutputConverter 进行结构化输出
 */
public record ExecutionPlanStep(
    @JsonPropertyDescription("步骤编号，从1开始")
    int stepNumber,
    
    @JsonPropertyDescription("步骤名称")
    String stepName,

    @JsonPropertyDescription("步骤动作类型：TOOL（以工具调用为主，必要时可补充简短说明）或 LLM（需要模型生成文本/总结；可不调用工具）")
    String actionType,
    
    @JsonPropertyDescription("步骤详细描述，包含用户需求传递")
    String description,
    
    @JsonPropertyDescription("工具函数名称（仅当 actionType=TOOL 或该步骤需要调用工具时填写；不需要工具则留空）")
    String toolName,
    
    @JsonPropertyDescription("工具参数")
    Map<String, Object> toolParams,
    
    @JsonPropertyDescription("选择此工具和参数的理由")
    String reasoning,
    
    @JsonPropertyDescription("前置步骤序号，如无依赖则为空数组")
    List<Integer> dependencies,
    
    @JsonPropertyDescription("预期输出结果")
    String expectedOutput
) {}
