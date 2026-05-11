package org.wwz.ai.domain.agent.runtime.tool.common;


import lombok.Data;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Plan;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.planning.PlanLifecycleResult;
import org.wwz.ai.domain.agent.runtime.tool.common.planning.PlanLifecycleService;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;

import java.util.*;
import java.util.function.Function;

/**
 * 计划工具类
 */
@Data
public class PlanningTool implements BaseTool {

    private AgentContext agentContext;
    private final Map<String, Function<Map<String, Object>, String>> commandHandlers = new HashMap<>();
    private final PlanLifecycleService lifecycleService = new PlanLifecycleService();
    private Plan plan;
    private boolean closeUpdateMode;

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
        ReactorConfig reactorConfig = requireReactorConfig();
        return reactorConfig.getPlanToolDesc().isEmpty() ? desc : reactorConfig.getPlanToolDesc();
    }

    @Override
    public Map<String, Object> toParams() {
        ReactorConfig reactorConfig = requireReactorConfig();
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
            String observation = handler.apply(params);
            return ToolResultPayload.structured(
                    observation,
                    observation,
                    lastStructuredOutput
            );
        } else {
            throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private PlanningToolOutput lastStructuredOutput;

    private String createPlan(Map<String, Object> params) {
        String title = (String) params.get("title");
        List<String> steps = (List<String>) params.get("steps");

        if (title == null || steps == null) {
            throw new IllegalArgumentException("title, and steps are required for create command");
        }

        if (plan != null) {
            throw new IllegalStateException("A plan already exists. Delete the current plan first.");
        }

        Plan beforePlan = snapshot(plan);
        PlanLifecycleResult result = closeUpdateMode
                ? createCompatPlan(title, steps)
                : lifecycleService.create(title, steps);
        plan = result.getPlan();
        lastStructuredOutput = buildStructuredOutput("create", beforePlan, result);
        return "我已创建plan";
    }

    private String updatePlan(Map<String, Object> params) {
        String title = (String) params.get("title");
        List<String> steps = (List<String>) params.get("steps");

        if (plan == null) {
            throw new IllegalStateException("No plan exists. Create a plan first.");
        }
        if (steps == null) {
            throw new IllegalArgumentException("steps are required for update command");
        }

        Plan beforePlan = snapshot(plan);
        PlanLifecycleResult result = closeUpdateMode
                ? updateCompatPlan(title, steps)
                : lifecycleService.update(plan, title, steps);
        plan = result.getPlan();
        lastStructuredOutput = buildStructuredOutput("update", beforePlan, result);
        return "我已更新plan";
    }

    private String markStep(Map<String, Object> params) {
        Integer stepIndex = normalizeStepIndex((Integer) params.get("step_index"));
        String stepStatus = (String) params.get("step_status");
        String stepNotes = (String) params.get("step_notes");

        if (plan == null) {
            throw new IllegalStateException("No plan exists. Create a plan first.");
        }

        if (stepIndex == null) {
            throw new IllegalArgumentException("step_index is required for mark_step command");
        }

        Plan beforePlan = snapshot(plan);
        PlanLifecycleResult result = closeUpdateMode
                ? markStepCompat(stepIndex, stepStatus, stepNotes)
                : lifecycleService.markStep(plan, stepIndex, stepStatus, stepNotes);
        plan = result.getPlan();
        lastStructuredOutput = buildStructuredOutput("mark_step", beforePlan, result);

        return String.format("我已标记plan %d 为 %s", stepIndex, stepStatus);
    }

    private String finishPlan(Map<String, Object> params) {
        Plan beforePlan = snapshot(plan);
        PlanLifecycleResult result = lifecycleService.finish(plan);
        plan = result.getPlan();
        lastStructuredOutput = buildStructuredOutput("finish", beforePlan, result);
        return "我已更新plan为完成状态";
    }

    public void stepPlan() {
        if (plan == null) {
            return;
        }
        plan.stepPlan();
    }

    /**
     * 兼容顺推模式下，按既有 stepPlan 语义推进一步，并产出可持久化的 planning 结构化快照。
     * 这样历史回放可以恢复真实完成进度，而不再只能停留在 create 初始态。
     */
    public PlanningToolOutput advanceCompatPlanAndCapture() {
        if (plan == null) {
            return null;
        }
        Plan beforePlan = snapshot(plan);
        Integer currentStepIndex = plan.getCurrentStepIndex();
        if (currentStepIndex == null) {
            PlanLifecycleResult result = lifecycleService.ensureExecutable(plan);
            plan = result.getPlan();
            lastStructuredOutput = buildStructuredOutput("mark_step", beforePlan, result);
            return lastStructuredOutput;
        }

        PlanLifecycleResult result = markStepCompat(currentStepIndex, "completed", plan.getNotes().get(currentStepIndex));
        plan = result.getPlan();
        lastStructuredOutput = buildStructuredOutput("mark_step", beforePlan, result);
        return lastStructuredOutput;
    }


    public String getFormatPlan() {
        if (plan == null) {
            return "目前还没有Plan";
        }
        return plan.format();
    }

    private PlanLifecycleResult createCompatPlan(String title, List<String> steps) {
        return lifecycleService.create(title, steps);
    }

    private PlanLifecycleResult updateCompatPlan(String title, List<String> steps) {
        plan.update(title, steps);
        return lifecycleService.ensureExecutable(plan);
    }

    private PlanLifecycleResult markStepCompat(Integer stepIndex, String stepStatus, String stepNotes) {
        plan.updateStepStatus(stepIndex, stepStatus, stepNotes);
        if ("completed".equals(stepStatus)) {
            return lifecycleService.ensureExecutable(plan);
        }
        return PlanLifecycleResult.builder()
                .plan(plan)
                .currentStep(plan.getCurrentStep())
                .currentStepIndex(plan.getCurrentStepIndex())
                .autoAdvanced(Boolean.FALSE)
                .autoFinished(lifecycleService.isAllStepsCompleted(plan))
                .build();
    }

    private PlanningToolOutput buildStructuredOutput(String command, Plan beforePlan, PlanLifecycleResult result) {
        return PlanningToolOutput.builder()
                .command(command)
                .beforePlan(beforePlan)
                .afterPlan(snapshot(result == null ? null : result.getPlan()))
                .currentStep(result == null ? null : result.getCurrentStep())
                .currentStepIndex(result == null ? null : result.getCurrentStepIndex())
                .autoAdvanced(result != null && Boolean.TRUE.equals(result.getAutoAdvanced()))
                .autoFinished(result != null && Boolean.TRUE.equals(result.getAutoFinished()))
                .build();
    }

    /**
     * 兼容模型按展示序号回写 step_index 的情况。
     * 当前步骤在计划中的展示通常是 1-based，而工具协议本身要求 0-based；
     * 当仅存在“当前步骤的展示序号”这一种明显候选时，自动折算为真实索引，减少无意义失败。
     */
    private Integer normalizeStepIndex(Integer stepIndex) {
        if (stepIndex == null || plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return stepIndex;
        }
        Integer currentStepIndex = plan.getCurrentStepIndex();
        if (currentStepIndex == null) {
            return stepIndex;
        }

        int displayedCurrentIndex = currentStepIndex + 1;
        if (stepIndex.equals(displayedCurrentIndex)) {
            return currentStepIndex;
        }
        if (stepIndex >= 0 && stepIndex < plan.getSteps().size()) {
            return stepIndex;
        }
        return stepIndex;
    }

    private Plan snapshot(Plan source) {
        return source == null ? null : source.copy();
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("PlanningTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }
}


