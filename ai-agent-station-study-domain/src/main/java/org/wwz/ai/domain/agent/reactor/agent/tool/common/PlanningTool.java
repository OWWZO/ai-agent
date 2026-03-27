package org.wwz.ai.domain.agent.reactor.agent.tool.common;


import lombok.Data;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.Plan;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.*;
import java.util.function.Function;

/**
 * 计划工具类
 */
@Data
public class PlanningTool implements BaseTool {

    private AgentContext agentContext;
    private final Map<String, Function<Map<String, Object>, String>> commandHandlers = new HashMap<>();
    private Plan plan;

    public PlanningTool() {
        commandHandlers.put("create", this::createPlan);
        commandHandlers.put("update", this::updatePlan);
        commandHandlers.put("mark_step", this::markStep);
        commandHandlers.put("finish", this::finishPlan);
    }

    @Override
    public String getName() {
        return "planning";
    }

    @Override
    public String getDescription() {
        String desc = "这是一个计划工具，可让代理创建和管理用于解决复杂任务的计划。\n该工具提供创建计划、更新计划步骤和跟踪进度的功能。\n使用中文回答";
        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
        return reactorConfig.getPlanToolDesc().isEmpty() ? desc : reactorConfig.getPlanToolDesc();
    }

    @Override
    public Map<String, Object> toParams() {
        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
        if (!reactorConfig.getPlanToolParams().isEmpty()) {
            return reactorConfig.getPlanToolParams();
        }

        return getParameters();
    }

    private Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", getProperties());
        parameters.put("required", List.of("command"));
        return parameters;
    }

    private Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("command", getCommandProperty());
        properties.put("title", getTitleProperty());
        properties.put("steps", getStepsProperty());
        properties.put("step_index", getStepIndexProperty());
        properties.put("step_status", getStepStatusProperty());
        properties.put("step_notes", getStepNotesProperty());
        return properties;
    }

    private Map<String, Object> getCommandProperty() {
        Map<String, Object> command = new HashMap<>();
        command.put("type", "string");
        command.put("enum", Arrays.asList("create", "update", "mark_step", "finish"));
        command.put("description", "The command to execute. Available commands: create, update, mark_step, finish");
        return command;
    }

    private Map<String, Object> getTitleProperty() {
        Map<String, Object> title = new HashMap<>();
        title.put("type", "string");
        title.put("description", "Title for the plan. Required for create command, optional for update command.");
        return title;
    }

    private Map<String, Object> getStepsProperty() {
        Map<String, Object> items = new HashMap<>();
        items.put("type", "string");
        Map<String, Object> command = new HashMap<>();
        command.put("type", "array");
        command.put("items", items);
        command.put("description", "List of plan steps. Required for create command, optional for update command.");
        return command;
    }

    private Map<String, Object> getStepIndexProperty() {
        Map<String, Object> stepIndex = new HashMap<>();
        stepIndex.put("type", "integer");
        stepIndex.put("description", "Index of the step to update (0-based). Required for mark_step command.");
        return stepIndex;
    }

    private Map<String, Object> getStepStatusProperty() {
        Map<String, Object> stepStatus = new HashMap<>();
        stepStatus.put("type", "string");
        stepStatus.put("enum", Arrays.asList("not_started", "in_progress", "completed", "blocked"));
        stepStatus.put("description", "Status to set for a step. Used with mark_step command.");
        return stepStatus;
    }

    private Map<String, Object> getStepNotesProperty() {
        Map<String, Object> stepNotes = new HashMap<>();
        stepNotes.put("type", "string");
        stepNotes.put("description", "Additional notes for a step. Optional for mark_step command.");
        return stepNotes;
    }

    @Override
    public Object execute(Object input) {
        if (!(input instanceof Map)) {
            throw new IllegalArgumentException("Input must be a Map");
        }

        Map<String, Object> params = (Map<String, Object>) input;
        String command = (String) params.get("command");

        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Command is required");
        }

        Function<Map<String, Object>, String> handler = commandHandlers.get(command);
        if (handler != null) {
            return handler.apply(params);
        } else {
            throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private String createPlan(Map<String, Object> params) {
        String title = (String) params.get("title");
        List<String> steps = (List<String>) params.get("steps");

        if (title == null || steps == null) {
            throw new IllegalArgumentException("title, and steps are required for create command");
        }

        if (plan != null) {
            throw new IllegalStateException("A plan already exists. Delete the current plan first.");
        }

        plan = Plan.create(title, steps);
        return "我已创建plan";
    }

    private String updatePlan(Map<String, Object> params) {
        String title = (String) params.get("title");
        List<String> steps = (List<String>) params.get("steps");

        if (plan == null) {
            throw new IllegalStateException("No plan exists. Create a plan first.");
        }

        plan.update(title, steps);
        return "我已更新plan";
    }

    private String markStep(Map<String, Object> params) {
        Integer stepIndex = (Integer) params.get("step_index");
        String stepStatus = (String) params.get("step_status");
        String stepNotes = (String) params.get("step_notes");

        if (plan == null) {
            throw new IllegalStateException("No plan exists. Create a plan first.");
        }

        if (stepIndex == null) {
            throw new IllegalArgumentException("step_index is required for mark_step command");
        }

        plan.updateStepStatus(stepIndex, stepStatus, stepNotes);

        return String.format("我已标记plan %d 为 %s", stepIndex, stepStatus);
    }

    private String finishPlan(Map<String, Object> params) {
        if (Objects.isNull(plan)) {
            plan = new Plan();
        } else {
            for (int stepIndex = 0; stepIndex < plan.getSteps().size(); stepIndex++) {
                plan.updateStepStatus(stepIndex, "completed", "");
            }
        }
        return "我已更新plan为完成状态";
    }

    public void stepPlan() {
        plan.stepPlan();
    }


    public String getFormatPlan() {
        if (plan == null) {
            return "目前还没有Plan";
        }
        return plan.format();
    }
}


