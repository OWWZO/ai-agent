package org.wwz.ai.domain.agent.reactor.config;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;

import java.util.HashMap;
import java.util.Map;

/**
 * Reactor Phase 1 期间保留在 domain 的过渡态共享配置契约。
 * 删除/迁移时机：当 Reactor 共享配置被单独 change 收敛到 app 或专用配置模块后再迁移；
 * 当前阶段禁止顺手改写读取语义。
 */
@Slf4j
@Getter
@Configuration
public class ReactorConfig {

    private Map<String, String> plannerSystemPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.planner.system_prompt:{}}")
    public void setPlannerSystemPromptMap(String list) {
        this.plannerSystemPromptMap = parseStringMap(list);
    }

    private Map<String, String> plannerNextStepPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.planner.next_step_prompt:{}}")
    public void setPlannerNextStepPromptMap(String list) {
        this.plannerNextStepPromptMap = parseStringMap(list);
    }

    private Map<String, String> executorSystemPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.executor.system_prompt:{}}")
    public void setExecutorSystemPromptMap(String list) {
        this.executorSystemPromptMap = parseStringMap(list);
    }

    private Map<String, String> executorNextStepPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.executor.next_step_prompt:{}}")
    public void setExecutorNextStepPromptMap(String list) {
        this.executorNextStepPromptMap = parseStringMap(list);
    }

    private Map<String, String> executorSopPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.executor.sop_prompt:{}}")
    public void setExecutorSopPromptMap(String list) {
        this.executorSopPromptMap = parseStringMap(list);
    }

    private Map<String, String> reactSystemPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.react.system_prompt:{}}")
    public void setReactSystemPromptMap(String list) {
        this.reactSystemPromptMap = parseStringMap(list);
    }

    private Map<String, String> reactNextStepPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.react.next_step_prompt:{}}")
    public void setReactNextStepPromptMap(String list) {
        this.reactNextStepPromptMap = parseStringMap(list);
    }

    @Value("${autobots.autoagent.planner.model_name:qwen-vl-max}")
    private String plannerModelName;

    @Value("${autobots.autoagent.executor.model_name:qwen-vl-max}")
    private String executorModelName;

    @Value("${autobots.autoagent.react.model_name:qwen-vl-max}")
    private String reactModelName;

    @Value("${autobots.autoagent.tool.plan_tool.desc:}")
    private String planToolDesc;

    @Value("${autobots.autoagent.tool.code_agent.desc:}")
    private String codeAgentDesc;

    @Value("${autobots.autoagent.tool.report_tool.desc:}")
    private String reportToolDesc;

    @Value("${autobots.autoagent.tool.file_tool.desc:}")
    private String fileToolDesc;

    @Value("${autobots.autoagent.tool.deep_search_tool.desc:}")
    private String deepSearchToolDesc;

    @Value("${autobots.autoagent.tool.web_fetch_tool.desc:}")
    private String webFetchToolDesc;

    @Value("${autobots.autoagent.tool.multimodalagent_tool.desc:}")
    private String multiModalAgentDesc;

    @Value("${autobots.autoagent.tool.image_generation_tool.desc:}")
    private String imageGenerationToolDesc;

    @Value("${autobots.autoagent.tool.data_analysis_tool.desc:}")
    private String dataAnalysisToolDesc;

    /**
     * planTool 配置
     */
    private Map<String, Object> planToolParams = new HashMap<>();
    @Value("${autobots.autoagent.tool.plan_tool.params:{}}")
    public void setPlanToolParams(String jsonStr) {
        this.planToolParams = parseObjectMap(jsonStr);
    }

    /**
     * codeAgent 配置
     */
    private Map<String, Object> codeAgentParams = new HashMap<>();
    @Value("${autobots.autoagent.tool.code_agent.params:{}}")
    public void setCodeAgentParams(String jsonStr) {
        this.codeAgentParams = parseObjectMap(jsonStr);
    }

    /**
     * reportTool 配置
     */
    private Map<String, Object> reportToolParams = new HashMap<>();
    @Value("${autobots.autoagent.tool.report_tool.params:{}}")
    public void setReportToolParams(String jsonStr) {
        this.reportToolParams = parseObjectMap(jsonStr);
    }

    /**
     * fileTool 配置
     */
    private Map<String, Object> fileToolParams = new HashMap<>();
    @Value("${autobots.autoagent.tool.file_tool.params:{}}")
    public void setFileToolParams(String jsonStr) {
        this.fileToolParams = parseObjectMap(jsonStr);
    }

    /**
     * DeepSearchTool 配置
     */
    private Map<String, Object> deepSearchToolParams = new HashMap<>();
    @Value("${autobots.autoagent.tool.deep_search.params:{}}")
    public void setDeepSearchToolParams(String jsonStr) {
        this.deepSearchToolParams = parseObjectMap(jsonStr);
    }

    /**
     * WebFetchTool 配置
     */
    private Map<String, Object> webFetchToolParams = new HashMap<>();
    @Value("${autobots.autoagent.tool.web_fetch.params:{}}")
    public void setWebFetchToolParams(String jsonStr) {
        this.webFetchToolParams = parseObjectMap(jsonStr);
    }

    /**
     * MultiModalAgentTool 配置
     */
    private Map<String, Object> multiModalAgentParams = new HashMap<>();
    @Value("${autobots.autoagent.tool.multimodalagent_tool.params:{}}")
    public void setMultiModalAgentParams(String jsonStr) {
        this.multiModalAgentParams = parseObjectMap(jsonStr);
    }

    /**
     * ImageGenerationTool 配置
     */
    private Map<String, Object> imageGenerationToolParams = new HashMap<>();
    @Value("${autobots.autoagent.tool.image_generation_tool.params:{}}")
    public void setImageGenerationToolParams(String jsonStr) {
        this.imageGenerationToolParams = parseObjectMap(jsonStr);
    }

    /**
     * DataAnalysisTool 配置
     */
    private Map<String, Object> dataAnalysisToolParams = new HashMap<>();
    @Value("${autobots.autoagent.tool.data_analysis_tool.params:{}}")
    public void setDataAnalysisToolParams(String jsonStr) {
        this.dataAnalysisToolParams = parseObjectMap(jsonStr);
    }

    @Value("${autobots.autoagent.tool.file_tool.truncate_len:5000}")
    private Integer fileToolContentTruncateLen;

    @Value("${autobots.autoagent.tool.deep_search.file_desc.truncate_len:500}")
    private Integer deepSearchToolFileDescTruncateLen;

    @Value("${autobots.autoagent.tool.deep_search.message.truncate_len:500}")
    private Integer deepSearchToolMessageTruncateLen;

    @Value("${autobots.autoagent.planner.pre_prompt:分析问题并制定计划：}")
    private String planPrePrompt;

    @Value("${autobots.autoagent.task.pre_prompt:参考对话历史回答，}")
    private String taskPrePrompt;

    @Value("${autobots.autoagent.tool.clear_tool_message:1}")
    private String clearToolMessage;

    @Value("${autobots.autoagent.planner.close_update:1}")
    private String planningCloseUpdate;

    @Value("${autobots.autoagent.deep_search_page_count:3}")
    private String deepSearchPageCount;

    private Map<String, String> multiAgentToolListMap = new HashMap<>();
    @Value("${autobots.autoagent.tool_list:{}}")
    public void setMultiAgentToolList(String list) {
        this.multiAgentToolListMap = parseStringMap(list);
    }

    /**
     * LLM Settings
     */
    private Map<String, LLMSettings> llmSettingsMap;
    @Value("${llm.settings:{}}")
    public void setLLMSettingsMap(String jsonStr) {
        Map<String, LLMSettings> rawSettings = JSON.parseObject(jsonStr, new TypeReference<Map<String, LLMSettings>>() {
        });
        this.llmSettingsMap = normalizeLlmSettingsMap(rawSettings);
    }

    @Value("${autobots.autoagent.planner.max_steps:40}")
    private Integer plannerMaxSteps;

    @Value("${autobots.autoagent.planner.max_parallel_tasks:2}")
    private Integer plannerMaxParallelTasks;

    @Value("${autobots.autoagent.executor.max_steps:40}")
    private Integer executorMaxSteps;

    @Value("${autobots.autoagent.react.max_steps:40}")
    private Integer reactMaxSteps;

    @Value("${autobots.autoagent.executor.max_observe:10000}")
    private String maxObserve;

    @Value("${autobots.autoagent.code_interpreter_url:}")
    private String codeInterpreterUrl;

    @Value("${autobots.autoagent.deep_search_url:}")
    private String deepSearchUrl;

    @Value("${autobots.autoagent.web_fetch_url:}")
    private String webFetchUrl;

    @Value("${autobots.autoagent.multimodalagent_url:}")
    private String multiModalAgentUrl;

    @Value("${autobots.autoagent.image_generation_url:}")
    private String imageGenerationUrl;

    @Value("${autobots.autoagent.mcp_client_url:}")
    private String mcpClientUrl;

    @Value("${autobots.autoagent.mcp_server_url:}")
    private String[] mcpServerUrlArr;

    @Value("${autobots.autoagent.knowledge_url:}")
    private String autoBotsKnowledgeUrl;

    @Value("${autobots.autoagent.data_analysis_url:}")
    private String dataAnalysisUrl;

    @Value("${autobots.autoagent.summary.system_prompt:}")
    private String summarySystemPrompt;

    @Value("${autobots.autoagent.summary.model_name:}")
    private String summaryModelName;

    @Value("${autobots.autoagent.summary.temperature:0.7}")
    private Double summaryTemperature;

    @Value("${autobots.autoagent.digital_employee_prompt:}")
    private String digitalEmployeePrompt;

    @Value("${autobots.autoagent.summary.message_size_limit:1000}")
    private Integer messageSizeLimit;

    /**
     * skill 在 ReAct 链路中的启用开关，主要用于日志观测与排障。
     */
    @Value("${autobots.autoagent.skill.react-enabled:true}")
    private Boolean skillReactEnabled;

    /**
     * skill 在 PlanSolve 链路中的启用开关，主要用于日志观测与排障。
     */
    @Value("${autobots.autoagent.skill.plan-solve-enabled:true}")
    private Boolean skillPlanSolveEnabled;

    /**
     * skill 脚本默认超时，便于在统一配置快照中查看当前生效值。
     */
    @Value("${autobots.autoagent.skill.default-script-timeout-seconds:120}")
    private Integer skillDefaultScriptTimeoutSeconds;

    /**
     * skill 文本读取上限，便于和 read_tool / skill_tool 的截断行为联动排查。
     */
    @Value("${autobots.autoagent.skill.max-read-chars:12000}")
    private Integer skillMaxReadChars;

    private Map<String, String> sensitivePatterns = new HashMap<>();
    @Value("${autobots.autoagent.sensitive_patterns:{}}")
    public void setSensitivePatterns(String jsonStr) {
        this.sensitivePatterns = parseStringMap(jsonStr);
    }

    private Map<String, String> outputStylePrompts = new HashMap<>();
    @Value("${autobots.autoagent.output_style_prompts:{}}")
    public void setOutputStylePrompts(String jsonStr) {
        this.outputStylePrompts = parseStringMap(jsonStr);
    }

    private Map<String, String> messageInterval = new HashMap<>();
    @Value("${autobots.autoagent.message_interval:{}}")
    public void setMessageInterval(String jsonStr) {
        this.messageInterval = parseStringMap(jsonStr);
    }

    private String structParseToolSystemPrompt = "";
    @Value("${autobots.autoagent.struct_parse_tool_system_prompt:}")
    public void setStructParseToolSystemPrompt(String str) {
        this.structParseToolSystemPrompt = str;
    }

	@Value("${autobots.multiagent.sseClient.readTimeout:18000}")
	private Integer sseClientReadTimeout;

	@Value("${autobots.multiagent.sseClient.connectTimeout:18000}")
	private Integer sseClientConnectTimeout;

	@Value("${autobots.autoagent.reactor_sop_prompt:}")
	private String reactorSopPrompt;

    @Value("${autobots.autoagent.reactor_base_prompt:}")
    private String reactorBasePrompt;

    @Value("${autobots.autoagent.tool.task_complete_desc:当前task完成，请将当前task标记为 completed}")
    private String taskCompleteDesc;

    @Value("${spring.ai.agent.chat.default-role-id:}")
    private String chatDefaultRoleId;

    private static Map<String, String> parseStringMap(String json) {
        if (!StringUtils.hasText(json) || "{}".equals(json.trim())) {
            return new HashMap<>();
        }
        return JSON.parseObject(json, new TypeReference<Map<String, String>>() {});
    }

    private static Map<String, Object> parseObjectMap(String json) {
        if (!StringUtils.hasText(json) || "{}".equals(json.trim())) {
            return new HashMap<>();
        }
        return JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 对 llm.settings 的 key 和 model 字段做规范化，避免配置里混入首尾空格导致模型配置失效。
     */
    private static Map<String, LLMSettings> normalizeLlmSettingsMap(Map<String, LLMSettings> rawSettings) {
        Map<String, LLMSettings> normalizedSettings = new HashMap<>();
        if (rawSettings == null || rawSettings.isEmpty()) {
            return normalizedSettings;
        }

        rawSettings.forEach((modelName, settings) -> {
            if (settings == null) {
                return;
            }

            String normalizedModelName = StringUtils.hasText(modelName) ? modelName.trim() : "";
            if (!StringUtils.hasText(normalizedModelName)) {
                return;
            }

            normalizedSettings.put(normalizedModelName, LLMSettings.builder()
                    .model(StringUtils.hasText(settings.getModel()) ? settings.getModel().trim() : normalizedModelName)
                    .maxTokens(settings.getMaxTokens())
                    .temperature(settings.getTemperature())
                    .apiType(settings.getApiType())
                    .apiKey(settings.getApiKey())
                    .apiVersion(settings.getApiVersion())
                    .baseUrl(settings.getBaseUrl())
                    .interfaceUrl(settings.getInterfaceUrl())
                    .functionCallType(settings.getFunctionCallType())
                    .maxInputTokens(settings.getMaxInputTokens())
                    .extParams(settings.getExtParams())
                    .build());
        });
        return normalizedSettings;
    }

}
