package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentContextFactory;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionAgentMailboxHub;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主 Agent 派发子 Agent 的工具入口（对标 cc-haha AgentTool）。
 * 默认同步阻塞；run_in_background=true 时注册后台任务并立即返回 task_id。
 */
@Slf4j
@Data
public class AgentDispatchTool implements BaseTool {

    public static final String NAME = "Agent";

    private static final ExecutorService BACKGROUND_EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "bg-subagent-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private final SubAgentRunner subAgentRunner;
    private final SubAgentRegistry subAgentRegistry;
    private AgentContext agentContext;

    public AgentDispatchTool(SubAgentRunner subAgentRunner, SubAgentRegistry subAgentRegistry) {
        this.subAgentRunner = subAgentRunner;
        this.subAgentRegistry = subAgentRegistry;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("派发一个子 Agent 执行独立任务。")
                .append("默认阻塞等待完成后返回精简报告；run_in_background=true 时立即返回 task_id 与 agentId，")
                .append("用 TaskOutput 取结果、TaskStop 取消、SendMessage 中途指导。")
                .append("新任务：子 Agent 从零上下文开始，请在 prompt 中写全背景与交付要求。")
                .append("续跑：传入上次结果中的 resume_agent_id（即 agentId），可带着上次工作记忆继续任务。")
                .append("可用 subagent_type：");
        List<String> lines = new ArrayList<>();
        if (subAgentRegistry != null) {
            for (SubAgentDefinition def : subAgentRegistry.list()) {
                lines.add(def.getAgentType() + " — " + def.getWhenToUse());
            }
        }
        if (lines.isEmpty()) {
            sb.append("general-purpose, Explore");
        } else {
            sb.append(String.join("; ", lines));
        }
        sb.append("。省略 subagent_type 时默认 general-purpose。不要用本工具做简单单次查询。");
        return sb.toString();
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", "string");
        description.put("description", "任务短描述，3-5 个词，用于展示与日志");

        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("type", "string");
        prompt.put("description",
                "交给子 Agent 的任务说明。新任务时需写全背景；resume 时写后续指令即可（会加载上次上下文）");

        Map<String, Object> subagentType = new LinkedHashMap<>();
        subagentType.put("type", "string");
        String typeHint = "子 Agent 类型；省略则 general-purpose。可用: "
                + (subAgentRegistry == null || subAgentRegistry.listTypeNames().isEmpty()
                ? "Explore, general-purpose"
                : String.join(", ", subAgentRegistry.listTypeNames()));
        subagentType.put("description", typeHint);

        Map<String, Object> resumeAgentId = new LinkedHashMap<>();
        resumeAgentId.put("type", "string");
        resumeAgentId.put("description",
                "可选。上次 Agent 工具返回的 agentId。传入后唤醒该子 Agent 并保留其工作记忆，而不是新开实例");

        Map<String, Object> runInBackground = new LinkedHashMap<>();
        runInBackground.put("type", "boolean");
        runInBackground.put("description",
                "设为 true 时后台运行：立即返回 task_id，用 TaskOutput 等待/读取结果，TaskStop 可取消");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("description", description);
        properties.put("prompt", prompt);
        properties.put("subagent_type", subagentType);
        properties.put("resume_agent_id", resumeAgentId);
        properties.put("run_in_background", runInBackground);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("description", "prompt"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = input instanceof Map
                    ? (Map<String, Object>) input
                    : JSON.parseObject(JSON.toJSONString(input), Map.class);
            if (params == null) {
                return ToolResultPayload.failureFrom("Agent 执行失败：参数为空", null);
            }

            String description = trimToString(params.get("description"));
            String prompt = trimToString(params.get("prompt"));
            String subagentType = trimToString(params.get("subagent_type"));
            String resumeAgentId = trimToString(params.get("resume_agent_id"));
            boolean background = coerceBoolean(params.get("run_in_background"));

            // Plan Mode：空白或 general-purpose 强制 Explore（只读）
            if (agentContext != null
                    && agentContext.getPlanModeState() != null
                    && agentContext.getPlanModeState().isPlanMode()) {
                if (StringUtils.isBlank(subagentType)
                        || SubAgentRegistry.TYPE_GENERAL_PURPOSE.equals(subagentType)) {
                    subagentType = SubAgentRegistry.TYPE_EXPLORE;
                }
            }

            if (StringUtils.isBlank(prompt)) {
                return ToolResultPayload.failureFrom("Agent 执行失败：prompt 不能为空", null);
            }
            if (StringUtils.isBlank(description)) {
                description = StringUtils.isNotBlank(resumeAgentId)
                        ? "resume-" + resumeAgentId
                        : StringUtils.defaultIfBlank(subagentType, "subagent-task");
            }
            if (subAgentRunner == null) {
                return ToolResultPayload.failureFrom("Agent 执行失败：SubAgentRunner 未注入", null);
            }
            if (agentContext == null) {
                return ToolResultPayload.failureFrom("Agent 执行失败：无 AgentContext", null);
            }

            if (background) {
                return executeBackground(description, prompt, subagentType, resumeAgentId);
            }

            SubAgentResult result = subAgentRunner.run(
                    agentContext, description, prompt, subagentType,
                    StringUtils.isBlank(resumeAgentId) ? null : resumeAgentId);
            Map<String, Object> data = buildObservationData(result);
            if (StringUtils.isNotBlank(resumeAgentId)) {
                data.put("resumed", true);
            }
            if (!result.isCompleted()) {
                return ToolResultPayload.failureFrom(
                        StringUtils.defaultIfBlank(result.getErrorMsg(), "Agent 执行失败"),
                        data);
            }
            return ToolResultPayload.fromData(data);
        } catch (Exception e) {
            log.error("Agent dispatch tool failed", e);
            String msg = "Agent 执行失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ToolResultPayload.failureFrom(msg, null);
        }
    }

    private Object executeBackground(String description,
                                     String prompt,
                                     String subagentType,
                                     String resumeAgentId) {
        RuntimeBackgroundTaskRegistry registry = agentContext.requireBackgroundTasks();
        RuntimeBackgroundTask task = registry.registerLocalAgent(description, subagentType, prompt);
        RunCancellation taskCancel = task.getCancellation() != null
                ? task.getCancellation()
                : new RunCancellation();
        task.setCancellation(taskCancel);

        // 预分配 agentId，便于立刻 SendMessage(to=agentId|task_id)
        final String preferredAgentId = StringUtils.isNotBlank(resumeAgentId)
                ? resumeAgentId.trim()
                : SubAgentContextFactory.newAgentId();
        task.setAgentId(preferredAgentId);
        registry.bindAgentId(task.getId(), preferredAgentId);

        final String desc = description;
        final String pmt = prompt;
        final String type = subagentType;
        final String resume = StringUtils.isBlank(resumeAgentId) ? null : resumeAgentId;
        final AgentContext parent = agentContext;
        final String taskId = task.getId();
        final String sessionKey = StringUtils.defaultIfBlank(parent.getSessionId(), parent.getRequestId());
        // 在子线程 markActive 之前也可投递（队列先建好）
        SessionAgentMailboxHub.queue(sessionKey, preferredAgentId);

        Future<?> future = BACKGROUND_EXECUTOR.submit(() -> {
            try {
                SubAgentResult result = subAgentRunner.run(
                        parent, desc, pmt, type, resume, taskCancel, preferredAgentId);
                registry.bindAgentId(taskId, result.getAgentId());
                if (taskCancel.isCancelled()
                        || RuntimeBackgroundTask.STATUS_STOPPED.equals(
                        registry.get(taskId).map(RuntimeBackgroundTask::getStatus).orElse(null))) {
                    // TaskStop 已置 stopped；若仍有部分输出则补上
                    registry.get(taskId).ifPresent(t -> {
                        if (StringUtils.isBlank(t.getOutput()) && StringUtils.isNotBlank(result.getContent())) {
                            t.setOutput(result.getContent());
                        }
                        if (StringUtils.isBlank(t.getAgentId())) {
                            t.setAgentId(result.getAgentId());
                        }
                        t.setAgentType(result.getAgentType());
                        t.setTotalToolUseCount(result.getTotalToolUseCount());
                        t.setTotalDurationMs(result.getTotalDurationMs());
                    });
                    return;
                }
                if (result.isCompleted()) {
                    registry.complete(taskId, result);
                } else {
                    registry.fail(taskId, result);
                }
            } catch (Exception e) {
                log.error("{} background subagent failed taskId={}", parent.getRequestId(), taskId, e);
                if (taskCancel.isCancelled()) {
                    return;
                }
                registry.fail(taskId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        });
        registry.bindFuture(taskId, future);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", NAME);
        data.put("ok", true);
        data.put("status", RuntimeBackgroundTask.STATUS_RUNNING);
        data.put("task_id", taskId);
        data.put("agentId", preferredAgentId);
        data.put("task_type", RuntimeBackgroundTask.TYPE_LOCAL_AGENT);
        data.put("description", description);
        data.put("agentType", StringUtils.defaultIfBlank(subagentType, SubAgentRegistry.TYPE_GENERAL_PURPOSE));
        data.put("run_in_background", true);
        data.put("message", "后台子 Agent 已启动。用 TaskOutput(task_id=\"" + taskId
                + "\") 取结果；SendMessage(to=\"" + preferredAgentId
                + "\" 或 task_id) 中途指导；TaskStop 取消。");
        if (StringUtils.isNotBlank(resumeAgentId)) {
            data.put("resumed", true);
            data.put("resume_agent_id", resumeAgentId);
        }
        return ToolResultPayload.fromData(data);
    }

    private static Map<String, Object> buildObservationData(SubAgentResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tool", NAME);
        body.put("ok", result.isCompleted());
        body.put("status", result.getStatus());
        body.put("agentId", result.getAgentId());
        body.put("agentType", result.getAgentType());
        body.put("description", result.getDescription());
        body.put("content", result.getContent());
        body.put("totalToolUseCount", result.getTotalToolUseCount());
        body.put("totalDurationMs", result.getTotalDurationMs());
        if (StringUtils.isNotBlank(result.getErrorMsg())) {
            body.put("errorMsg", result.getErrorMsg());
        }
        return body;
    }

    private static String trimToString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean coerceBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }
}
