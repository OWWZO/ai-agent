package org.wwz.ai.domain.agent.runtime;

import lombok.Builder;
import lombok.Value;
import org.springframework.core.env.Environment;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.runtime.tasklist.TasklistPersistencePort;
import org.wwz.ai.domain.agent.memory.SessionContextCompactionService;
import org.wwz.ai.domain.agent.memory.ltm.BackgroundReviewService;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushService;
import org.wwz.ai.domain.agent.memory.ltm.SessionSearchService;
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

    /**
     * 工具批 allOf 超时秒数；null 或 ≤0 表示不超时（仅测试夹具）。
     */
    Long toolBatchTimeoutSeconds;

    /** 可选：跨轮/中途工作记忆压缩 */
    SessionContextCompactionService sessionContextCompactionService;

    /** 可选：长期记忆编排（策展 + 深度 Provider） */
    LtmManager ltmManager;

    /** 可选：用户级策展记忆存储 */
    CuratedMemoryStore curatedMemoryStore;

    /** 可选：情节按需检索（ledger） */
    SessionSearchService sessionSearchService;

    /** LTM flush-min-turns（0=关闭） */
    Integer ltmFlushMinTurns;

    MemoryFlushService memoryFlushService;

    BackgroundReviewService backgroundReviewService;

    /** 可选：会话 Todo / 后台任务持久化 */
    TasklistPersistencePort tasklistPersistencePort;

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
     * @return 工具批超时秒数；未配置时默认 600。
     */
    public long resolveToolBatchTimeoutSeconds() {
        if (toolBatchTimeoutSeconds == null) {
            return 600L;
        }
        return toolBatchTimeoutSeconds;
    }

    public SessionContextCompactionService getOptionalSessionContextCompactionService() {
        return sessionContextCompactionService;
    }

    public LtmManager getOptionalLtmManager() {
        return ltmManager;
    }

    public CuratedMemoryStore getOptionalCuratedMemoryStore() {
        return curatedMemoryStore;
    }

    public SessionSearchService getOptionalSessionSearchService() {
        return sessionSearchService;
    }

    public int resolveLtmFlushMinTurns() {
        return ltmFlushMinTurns == null ? 6 : ltmFlushMinTurns;
    }

    public MemoryFlushService getOptionalMemoryFlushService() {
        return memoryFlushService;
    }

    public BackgroundReviewService getOptionalBackgroundReviewService() {
        return backgroundReviewService;
    }

    public TasklistPersistencePort getOptionalTasklistPersistencePort() {
        return tasklistPersistencePort;
    }

    /**
     * 统一解析 LLM 配置。
     * <ol>
     *   <li>DB 管理台（{@link org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog}）— 热更新、免重启</li>
     *   <li>ReactorConfig.llmSettings（yml）</li>
     *   <li>Environment llm.default.*</li>
     * </ol>
     */
    public LLMSettings resolveLlmSettings(String modelName) {
        String normalizedModelName = modelName == null ? "" : modelName.trim();

        if (llmDependencies != null && llmDependencies.getModelCatalog() != null) {
            var fromDb = llmDependencies.getModelCatalog().resolve(normalizedModelName);
            if (fromDb.isPresent()) {
                return enrichSamplingFromYml(fromDb.get(), normalizedModelName);
            }
        }

        ReactorConfig config = requireReactorConfig();
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

    /**
     * DB 只保证 base/key/model；采样参数优先叠 yml 同名条目，否则用 llm.default。
     */
    private LLMSettings enrichSamplingFromYml(LLMSettings db, String modelRef) {
        LLMSettings yml = null;
        ReactorConfig config = reactorConfig;
        if (config != null && config.getLlmSettingsMap() != null) {
            if (db.getModel() != null && !db.getModel().isBlank()) {
                yml = config.getLlmSettingsMap().get(db.getModel());
            }
            if (yml == null && modelRef != null && !modelRef.isBlank()) {
                yml = config.getLlmSettingsMap().get(modelRef);
            }
        }
        LLMSettings defaults = buildDefaultLlmSettings();
        LLMSettings sample = yml != null ? yml : defaults;
        String functionCallType = sample.getFunctionCallType();
        if (functionCallType == null || functionCallType.isBlank()) {
            functionCallType = defaults.getFunctionCallType();
        }
        return LLMSettings.builder()
                .model(db.getModel())
                .baseUrl(db.getBaseUrl())
                .apiKey(db.getApiKey())
                .interfaceUrl(db.getInterfaceUrl())
                .maxTokens(sample.getMaxTokens() > 0 ? sample.getMaxTokens() : defaults.getMaxTokens())
                .temperature(sample.getTemperature())
                .maxInputTokens(sample.getMaxInputTokens() > 0 ? sample.getMaxInputTokens() : defaults.getMaxInputTokens())
                .functionCallType(functionCallType)
                .reasoningEffort(sample.getReasoningEffort())
                .apiType(sample.getApiType())
                .apiVersion(sample.getApiVersion())
                .extParams(sample.getExtParams() != null ? sample.getExtParams() : new HashMap<>())
                .build();
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
