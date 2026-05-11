package org.wwz.ai.domain.agent.runtime;

import lombok.Builder;
import lombok.Value;
import org.springframework.core.env.Environment;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;
import org.springframework.scheduling.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Reactor 运行时依赖包。
 * domain 侧只依赖这个 typed bundle，不再直接触碰 Spring 容器全局入口。
 */
@Value
@Builder(toBuilder = true)
public class ReactorRuntimeDependencies {

    ReactorConfig reactorConfig;

    Environment environment;

    ReactorLlmDependencies llmDependencies;

    McpToolExecutor mcpToolExecutor;

    IImageGenerationExecutionKernel imageGenerationExecutionKernel;

    RemoteHttpPort remoteHttpPort;

    RemoteStreamPort remoteStreamPort;

    FileArtifactPort fileArtifactPort;

    //预留给之后并发调用llm
    Executor llmExecutor;

    Executor taskExecutor;

    Executor toolExecutor;

    TaskScheduler heartbeatScheduler;

    public ReactorConfig requireReactorConfig() {
        return Objects.requireNonNull(reactorConfig, "ReactorConfig must not be null");
    }

    public Environment requireEnvironment() {
        return Objects.requireNonNull(environment, "Environment must not be null");
    }

    public ReactorLlmDependencies requireLlmDependencies() {
        return Objects.requireNonNull(llmDependencies, "ReactorLlmDependencies must not be null");
    }

    public McpToolExecutor getOptionalMcpToolExecutor() {
        return mcpToolExecutor;
    }

    public IImageGenerationExecutionKernel requireImageGenerationExecutionKernel() {
        return Objects.requireNonNull(imageGenerationExecutionKernel, "IImageGenerationExecutionKernel must not be null");
    }

    public RemoteHttpPort requireRemoteHttpPort() {
        return Objects.requireNonNull(remoteHttpPort, "RemoteHttpPort must not be null");
    }

    public RemoteStreamPort requireRemoteStreamPort() {
        return Objects.requireNonNull(remoteStreamPort, "RemoteStreamPort must not be null");
    }

    public FileArtifactPort requireFileArtifactPort() {
        return Objects.requireNonNull(fileArtifactPort, "FileArtifactPort must not be null");
    }

    public Executor requireLlmExecutor() {
        return Objects.requireNonNull(llmExecutor, "llmExecutor must not be null");
    }

    public Executor requireToolExecutor() {
        return Objects.requireNonNull(toolExecutor, "toolExecutor must not be null");
    }

    public Executor requireTaskExecutor() {
        return Objects.requireNonNull(taskExecutor, "taskExecutor must not be null");
    }

    public TaskScheduler requireHeartbeatScheduler() {
        return Objects.requireNonNull(heartbeatScheduler, "heartbeatScheduler must not be null");
    }

    /**
     * 统一解析 LLM 配置。
     * 优先读取 ReactorConfig.llmSettings，其次回退到 Environment 中的 llm.default.*。
     */
    public LLMSettings resolveLlmSettings(String modelName) {
        ReactorConfig config = requireReactorConfig();
        String normalizedModelName = modelName == null ? "" : modelName.trim();
        if (config.getLlmSettingsMap() != null && !normalizedModelName.isBlank()) {
            LLMSettings settings = config.getLlmSettingsMap().get(normalizedModelName);
            if (settings != null) {
                return settings;
            }
        }

        LLMSettings defaultConfig = buildDefaultLlmSettings();
        if (!normalizedModelName.isBlank()) {
            defaultConfig.setModel(normalizedModelName);
        }
        return defaultConfig;
    }

    private LLMSettings buildDefaultLlmSettings() {
        Environment env = requireEnvironment();
        return LLMSettings.builder()
                .model(env.getProperty("llm.default.model", "gpt-4o-0806"))
                .maxTokens(parseInt(env.getProperty("llm.default.max_tokens"), 16384))
                .temperature(parseDouble(env.getProperty("llm.default.temperature"), 0.0))
                .baseUrl(env.getProperty("llm.default.base_url", ""))
                .interfaceUrl(env.getProperty("llm.default.interface_url", "/v1/chat/completions"))
                .functionCallType(env.getProperty("llm.default.function_call_type", "function_call"))
                .apiKey(env.getProperty("llm.default.apikey", ""))
                .maxInputTokens(parseInt(env.getProperty("llm.default.max_input_tokens"), 100000))
                .extParams(new HashMap<>())
                .build();
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    private double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
