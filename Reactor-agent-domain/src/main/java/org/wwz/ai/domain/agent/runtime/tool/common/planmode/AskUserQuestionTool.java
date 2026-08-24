package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.askuser.UserInputRequiredException;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向用户提问。
 * <p>
 * Continuation 版：校验参数后抛出 {@link UserInputRequiredException}，由上层持久化断点并结束 Run A；
 * 不再在工具线程上 Future.get 阻塞等待。
 * </p>
 */
@Slf4j
@Data
public class AskUserQuestionTool implements BaseTool {

    public static final String NAME = "AskUserQuestion";

    private AgentContext agentContext;

    public AskUserQuestionTool() {
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "向用户提出 1–4 个选择题以澄清需求、方案或偏好。"
                + "每题 2–4 个选项；multiSelect=true 允许多选。"
                + "不要用本工具问“计划可以吗？”（用 ExitPlanMode）。"
                + "调用后当前 run 会结束并等待用户回答；用户提交后由 continuation run 继续。"
                + "本轮必须是唯一 tool call。仅主 Agent 可用。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> optionProps = new LinkedHashMap<>();
        Map<String, Object> label = new LinkedHashMap<>();
        label.put("type", "string");
        label.put("description", "选项短标签（1–5 词）");
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", "string");
        description.put("description", "选项说明/权衡");
        optionProps.put("label", label);
        optionProps.put("description", description);

        Map<String, Object> optionItem = new LinkedHashMap<>();
        optionItem.put("type", "object");
        optionItem.put("properties", optionProps);
        optionItem.put("required", List.of("label", "description"));

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("type", "array");
        options.put("items", optionItem);
        options.put("minItems", 2);
        options.put("maxItems", 4);
        options.put("description", "2–4 个选项；不要写 Other（前端可加自定义）");

        Map<String, Object> questionText = new LinkedHashMap<>();
        questionText.put("type", "string");
        questionText.put("description", "完整问题文本，建议以 ? 结尾");

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("type", "string");
        header.put("description", "短标签（≤12 字）");

        Map<String, Object> multiSelect = new LinkedHashMap<>();
        multiSelect.put("type", "boolean");
        multiSelect.put("description", "是否多选，默认 false");

        Map<String, Object> qProps = new LinkedHashMap<>();
        qProps.put("question", questionText);
        qProps.put("header", header);
        qProps.put("options", options);
        qProps.put("multiSelect", multiSelect);

        Map<String, Object> questionItem = new LinkedHashMap<>();
        questionItem.put("type", "object");
        questionItem.put("properties", qProps);
        questionItem.put("required", List.of("question", "header", "options"));

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("type", "array");
        questions.put("items", questionItem);
        questions.put("minItems", 1);
        questions.put("maxItems", 4);
        questions.put("description", "1–4 道题");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("questions", questions);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("questions"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        if (agentContext == null) {
            return fail("AskUserQuestion 未正确装配");
        }
        if (agentContext.getRequestId() != null && agentContext.getRequestId().contains(":sub:")) {
            return fail("AskUserQuestion 不能在子 Agent 中使用");
        }

        Map<String, Object> params = coerceMap(input);
        List<Map<String, Object>> questions = normalizeQuestions(params.get("questions"));
        if (questions.isEmpty()) {
            return fail("questions 不能为空（1–4 题）");
        }

        String toolCallId = null;
        ToolArtifactSource source = agentContext.getCurrentToolArtifactSource();
        if (source != null) {
            toolCallId = source.getToolCallId();
        }
        throw new UserInputRequiredException(questions, toolCallId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeQuestions(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        if (result.size() > 4) {
            return result.subList(0, 4);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> coerceMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static ToolResultPayload fail(String msg) {
        return ToolResultPayload.failureFrom(msg, null);
    }
}
