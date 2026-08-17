package org.wwz.ai.domain.agent.runtime.agent;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.llm.LLM;

import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Agent 构造公共步骤：绑上下文、system prompt、printer、步数与 LLM。
 */
public final class AgentBootstrap {

    private AgentBootstrap() {
    }

    public record Profile(
            String name,
            String description,
            Function<ReactorConfig, Map<String, String>> systemPromptMap,
            String defaultSystemPrompt,
            Function<ReactorConfig, String> modelName,
            ToIntFunction<ReactorConfig> maxSteps,
            boolean wireDigitalEmployeePrompt,
            boolean bindContextTools
    ) {
    }

    public static ReactorConfig configure(BaseAgent agent, AgentContext context, Profile profile) {
        ReactorRuntimeDependencies deps = agent.requireRuntimeDependencies(context);
        ReactorConfig config = deps.requireReactorConfig();
        agent.setName(profile.name());
        agent.setDescription(profile.description());
        agent.setContext(context);
        agent.initializeSystemPrompt(profile.systemPromptMap().apply(config), profile.defaultSystemPrompt());
        agent.setPrinter(context.printer);
        agent.setMaxSteps(profile.maxSteps().applyAsInt(config));
        String modelRef = StringUtils.isNotBlank(context.getModel())
                ? context.getModel().trim()
                : profile.modelName().apply(config);
        LLM llm = new LLM(modelRef, "", deps);
        applyThinkingOverride(llm, context);
        agent.setLlm(llm);
        if (profile.bindContextTools() && context.getToolCollection() != null) {
            agent.availableTools = context.getToolCollection();
        }
        if (profile.wireDigitalEmployeePrompt()) {
            agent.setDigitalEmployeePrompt(config.getDigitalEmployeePrompt());
        }
        return config;
    }

    /** 本轮 thinking / effort 覆盖模型默认 reasoning_effort。 */
    private static void applyThinkingOverride(LLM llm, AgentContext context) {
        if (llm == null || context == null || llm.getLlmSettings() == null) {
            return;
        }
        Boolean thinking = context.getThinking();
        if (thinking == null) {
            return;
        }
        if (!thinking) {
            llm.getLlmSettings().setReasoningEffort(null);
            return;
        }
        String effort = StringUtils.trimToNull(context.getThinkingEffort());
        llm.getLlmSettings().setReasoningEffort(effort != null ? effort : "medium");
    }
}
