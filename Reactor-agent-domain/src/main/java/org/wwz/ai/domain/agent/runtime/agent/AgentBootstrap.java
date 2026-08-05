package org.wwz.ai.domain.agent.runtime.agent;

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
        agent.setLlm(new LLM(profile.modelName().apply(config), "", deps));
        if (profile.bindContextTools() && context.getToolCollection() != null) {
            agent.availableTools = context.getToolCollection();
        }
        if (profile.wireDigitalEmployeePrompt()) {
            agent.setDigitalEmployeePrompt(config.getDigitalEmployeePrompt());
        }
        return config;
    }
}
