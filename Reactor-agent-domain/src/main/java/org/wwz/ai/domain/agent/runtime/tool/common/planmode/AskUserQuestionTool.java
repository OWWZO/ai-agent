package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.askuser.PendingUserQuestion;
import org.wwz.ai.domain.agent.runtime.askuser.PendingUserQuestionRegistry;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 向用户提问（对标 cc-haha AskUserQuestionTool）。
 * <p>
 * Web 适配：不占用 HTTP 请求线程空等，而是：
 * 1) SSE 推送 ask_user_question 卡片
 * 2) 工具线程在 Agent 工作线程上 await CompletableFuture
 * 3) 前端 POST /api/agent/ask-user/answer 完成 future
 * 4) 工具返回 answers，Agent 继续推理，同一条 SSE 继续吐流
 * </p>
 */
@Slf4j
@Data
public class AskUserQuestionTool implements BaseTool {

    public static final String NAME = "AskUserQuestion";

    private AgentContext agentContext;
    private PendingUserQuestionRegistry registry;

    public AskUserQuestionTool(PendingUserQuestionRegistry registry) {
        this.registry = registry;
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
                + "用户可能较久才回答；调用后会挂起直到用户提交或超时。"
                + "仅主 Agent 可用。";
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
        try {
            if (agentContext == null || registry == null) {
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

            PendingUserQuestion pending = registry.create(
                    agentContext.getSessionId(),
                    agentContext.getRequestId(),
                    toolCallId,
                    questions,
                    null);

            // 1) 推送到前端（SSE 卡片）
            if (agentContext.getPrinter() != null) {
                agentContext.getPrinter().send(
                        pending.getQuestionId(),
                        "ask_user_question",
                        pending.toClientPayload(),
                        false);
            }

            // 2) 在 Agent 工作线程阻塞等待（不阻塞 Tomcat 请求线程）
            Map<String, String> answers;
            try {
                answers = registry.awaitAnswers(pending);
            } catch (TimeoutException e) {
                String msg = "用户在时限内未回答问题（timeout=" + pending.getTimeoutMs() + "ms）。请基于已有信息继续，或再次提问。";
                return ToolResultPayload.failure(msg, msg, null, "timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return fail("等待用户回答被中断");
            } catch (RuntimeException e) {
                return fail("等待用户回答失败：" + e.getMessage());
            }

            // 3) 打包回模型
            StringBuilder sb = new StringBuilder("User has answered your questions: ");
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, String> e : answers.entrySet()) {
                parts.add("\"" + e.getKey() + "\"=\"" + e.getValue() + "\"");
            }
            sb.append(String.join(", ", parts));
            sb.append(". You can now continue with the user's answers in mind.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("questions", questions);
            body.put("answers", answers);
            body.put("questionId", pending.getQuestionId());
            return ToolResultPayload.text(sb + "\n" + JSON.toJSONString(body));
        } catch (Exception e) {
            log.warn("AskUserQuestion failed", e);
            return fail("AskUserQuestion 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
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

    private static ToolResultPayload fail(String msg) {
        return ToolResultPayload.failure(msg, msg, null, msg);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> coerceMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (input == null) {
            return Map.of();
        }
        return JSON.parseObject(JSON.toJSONString(input), Map.class);
    }
}
