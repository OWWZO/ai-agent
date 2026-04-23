package org.wwz.ai.domain.agent.reactor.config;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.reactor.agent.llm.LLMSettings;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Getter
@Configuration
public class ReactorConfig {

    private Map<String, String> plannerSystemPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.planner.system_prompt:{}}")
    public void setPlannerSystemPromptMap(String list) {
        plannerSystemPromptMap = JSON.parseObject(list, new TypeReference<Map<String, String>>() {
        });
    }

    private Map<String, String> plannerNextStepPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.planner.next_step_prompt:{}}")
    public void setPlannerNextStepPromptMap(String list) {
        plannerNextStepPromptMap = JSON.parseObject(list, new TypeReference<Map<String, String>>() {
        });
    }

    private Map<String, String> executorSystemPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.executor.system_prompt:{}}")
    public void setExecutorSystemPromptMap(String list) {
        executorSystemPromptMap = JSON.parseObject(list, new TypeReference<Map<String, String>>() {
        });
    }

    private Map<String, String> executorNextStepPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.executor.next_step_prompt:{}}")
    public void setExecutorNextStepPromptMap(String list) {
        executorNextStepPromptMap = JSON.parseObject(list, new TypeReference<Map<String, String>>() {
        });
    }

    private Map<String, String> executorSopPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.executor.sop_prompt:{}}")
    public void setExecutorSopPromptMap(String list) {
        executorSopPromptMap = JSON.parseObject(list, new TypeReference<Map<String, String>>() {
        });
    }

    private Map<String, String> reactSystemPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.react.system_prompt:{}}")
    public void setReactSystemPromptMap(String list) {
        reactSystemPromptMap = JSON.parseObject(list, new TypeReference<Map<String, String>>() {
        });
    }

    private Map<String, String> reactNextStepPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.react.next_step_prompt:{}}")
    public void setReactNextStepPromptMap(String list) {
        reactNextStepPromptMap = JSON.parseObject(list, new TypeReference<Map<String, String>>() {
        });
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

    @Value("${autobots.autoagent.tool.data_analysis_tool.desc:}")
    private String dataAnalysisToolDesc;

    /**
     * planTool 配置
     */
    private Map<String, Object> planToolParams = new HashMap<>();
    @Value("${autobots.autoagent.tool.plan_tool.params:{}}")
    public void setPlanToolParams(String jsonStr) {
        this.planToolParams = JSON.parseObject(jsonStr, Map.class);
    }

    /**
     * codeAgent 配置
     */
    private Map<String, Object> codeAgentPamras = new HashMap<>();
    @Value("${autobots.autoagent.tool.code_agent.params:{}}")
    public void setCodeAgentPamras(String jsonStr) {
        this.codeAgentPamras = JSON.parseObject(jsonStr, Map.class);
    }

    /**
     * reportTool 配置
     */
    private Map<String, Object> reportToolPamras = new HashMap<>();
    @Value("${autobots.autoagent.tool.report_tool.params:{}}")
    public void setHtmlToolPamras(String jsonStr) {
        this.reportToolPamras = JSON.parseObject(jsonStr, Map.class);
    }

    /**
     * fileTool 配置
     */
    private Map<String, Object> fileToolPamras = new HashMap<>();
    @Value("${autobots.autoagent.tool.file_tool.params:{}}")
    public void setFileoolPamras(String jsonStr) {
        this.fileToolPamras = JSON.parseObject(jsonStr, Map.class);
    }

    /**
     * DeepSearchTool 配置
     */
    private Map<String, Object> deepSearchToolPamras = new HashMap<>();
    @Value("${autobots.autoagent.tool.deep_search.params:{}}")
    public void setDeepSearchToolPamras(String jsonStr) {
        this.deepSearchToolPamras = JSON.parseObject(jsonStr, Map.class);
    }

    /**
     * DataAnalysisTool 配置
     */
    private Map<String, Object> dataAnalysisToolPamras = new HashMap<>();
    @Value("${autobots.autoagent.tool.data_analysis_tool.params:{}}")
    public void setDtaAnalysisToolPamras(String jsonStr) {
        this.dataAnalysisToolPamras = JSON.parseObject(jsonStr, Map.class);
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
        multiAgentToolListMap = JSON.parseObject(list, new TypeReference<>() {
        });
    }

    /**
     * LLM Settings
     */
    private Map<String, LLMSettings> llmSettingsMap;
    @Value("${llm.settings:{}}")
    public void setLLMSettingsMap(String jsonStr) {
        this.llmSettingsMap = JSON.parseObject(jsonStr, new TypeReference<Map<String, LLMSettings>>() {
        });
    }

    @Value("${autobots.autoagent.planner.max_steps:40}")
    private Integer plannerMaxSteps;

    @Value("${autobots.autoagent.executor.max_steps:40}")
    private Integer executorMaxSteps;

    @Value("${autobots.autoagent.react.max_steps:40}")
    private Integer reactMaxSteps;;

    @Value("${autobots.autoagent.executor.max_observe:10000}")
    private String maxObserve;

    @Value("${autobots.autoagent.code_interpreter_url:}")
    private String CodeInterpreterUrl;

    @Value("${autobots.autoagent.deep_search_url:}")
    private String DeepSearchUrl;

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
        this.sensitivePatterns = JSON.parseObject(jsonStr, new TypeReference<Map<String, String>>() {
        });
    }

    private Map<String, String> outputStylePrompts = new HashMap<>();
    @Value("${autobots.autoagent.output_style_prompts:{}}")
    public void setOutputStylePrompts(String jsonStr) {
        this.outputStylePrompts = JSON.parseObject(jsonStr, new TypeReference<Map<String, String>>() {
        });
    }

    private Map<String, String> messageInterval = new HashMap<>();
    @Value("${autobots.autoagent.message_interval:{}}")
    public void setMessageInterval(String jsonStr) {
        this.messageInterval = JSON.parseObject(jsonStr, new TypeReference<Map<String, String>>() {
        });
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

    /**
     * 会话记忆功能总开关。设为 false 时完全禁用上下文压缩和记忆恢复，所有请求直接 BYPASS。
     */
    @Value("${autobots.autoagent.session-memory.enabled:true}")
    private Boolean sessionMemoryEnabled;

    /**
     * 触发压缩的 Token 阈值。当会话工作记忆估算 Token 数超过此值时启动压缩流程。
     * 单位：Token（估算值，非精确值）。
     * 与 hard-limit-tokens 的关系：threshold < hard-limit，形成两级梯度。
     */
    @Value("${autobots.autoagent.session-memory.compaction-threshold-tokens:12000}")
    private Integer sessionMemoryCompactionThresholdTokens;

    /**
     * 最近窗口保留的轮次数量上限。压缩时至少保留最近 N 轮不被压缩，确保上下文连贯性。
     * 实际保留轮次取 min(recent-window-turns, 当前总轮次)。
     */
    @Value("${autobots.autoagent.session-memory.recent-window-turns:10}")
    private Integer sessionMemoryRecentWindowTurns;

    /**
     * Token 硬上限。当估算 Token 超过此值且压缩失败/熔断时，请求将被 REJECTED。
     * 这是保护 LLM 上下文不溢出的最后一道防线。
     * 必须 > compaction-threshold-tokens，否则阈值逻辑失效。
     */
    @Value("${autobots.autoagent.session-memory.hard-limit-tokens:20000}")
    private Integer sessionMemoryHardLimitTokens;

    /**
     * 最近窗口的最大 Token 数。即使轮次未达 recent-window-turns，若 Token 已超此值也停止保留。
     * 与 recent-window-turns 形成"轮次+Token"双维度控制，防止单轮超大内容撑爆窗口。
     */
    @Value("${autobots.autoagent.session-memory.recent-window-max-tokens:4000}")
    private Integer sessionMemoryRecentWindowMaxTokens;

    /**
     * 最近窗口的最小消息数。无论 Token 是否超限，至少保留这么多条消息不被压缩。
     * 保证即使最近轮次很短，也有足够上下文让 Agent 理解当前状态。
     */
    @Value("${autobots.autoagent.session-memory.recent-window-min-messages:4}")
    private Integer sessionMemoryRecentWindowMinMessages;

    /**
     * 连续压缩失败多少次后打开熔断器。
     * 达到此值后，在 circuit-open-seconds 时间内不再尝试压缩，直接返回降级或拒绝。
     */
    @Value("${autobots.autoagent.session-memory.max-consecutive-failures:3}")
    private Integer sessionMemoryMaxConsecutiveFailures;

    /**
     * 熔断器打开后保持的时间窗口。在此时间内压缩请求被短路。
     * 单位：秒。
     */
    @Value("${autobots.autoagent.session-memory.circuit-open-seconds:600}")
    private Integer sessionMemoryCircuitOpenSeconds;

    /**
     * 压缩后摘要文本的最大长度限制。
     * 单位：字符数（非 Token）。
     */
    @Value("${autobots.autoagent.session-memory.summary-max-length:4000}")
    private Integer sessionMemorySummaryMaxLength;

}
