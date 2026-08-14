package org.wwz.ai.domain.agent.runtime.agent;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactBinding;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactRegistry;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.runtime.cancel.PendingInjectMessage;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskListStore;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceFileReadState;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.memory.WorkingMemoryScopes;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Agent 运行时上下文：请求身份、工具会话、记忆、账本与取消控制。
 * 字段仍扁平暴露（builder/getter 兼容）；逻辑分组见底部 view 方法。
 */
@Data
@Builder
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    // ---- identity / request ----
    /**
     * 请求唯一标识（全链路追踪ID）
     * 用途：
     * 1. 日志排查：所有环节的日志均携带该ID，可快速定位单次请求的完整执行链路；
     * 2. 问题定位：关联智能体思考、工具调用、LLM请求等所有环节的异常日志；
     * 格式示例：uuid/雪花ID（如"8f2e9c45-6789-4321-abcd-1234567890ab"）
     */
    String requestId;

    /**
     * 会话ID（用户连续对话标识）
     * 用途：
     * 1. 多轮对话：关联同一用户的多次请求，维护会话级上下文；
     * 2. 记忆管理：基于该ID加载/存储用户的对话历史记忆；
     * 格式示例：用户ID+时间戳（如"user_123456_1710000000000"）
     */
    String sessionId;

    /**
     * 用户原始查询语句
     * 用途：智能体的核心输入，是所有任务拆解、工具调用的源头；
     * 示例："帮我分析这款产品的市场竞争力并生成报告"
     */
    String query;

    /**
     * 当前智能体执行的具体任务（结构化的query）
     * 用途：
     * 1. 任务拆解：由原始query解析而来的结构化任务（粒度更细）；
     * 2. 工具绑定：关联当前任务专属的工具、文件、提示词；
     * 示例："生成产品市场竞争力分析报告"（对应query的子任务）
     */
    String task;


    // ---- I/O & tools ----
    /**
     * 输出器（结果推送工具）
     * 用途：
     * 1. 实时推送：向前端/用户推送智能体执行过程（如思考过程、计划步骤、工具执行结果）；
     * 2. 消息分类：支持按类型（plan_thought/task/plan）推送不同格式的消息；
     * 核心方法：printer.send(String type, Object content)
     */
    @ToString.Exclude
    @JSONField(serialize = false)
    Printer printer;

    /**
     * 工具集合（智能体可调用的所有工具容器）
     * 用途：
     * 1. 工具管理：存储所有可用工具（基础工具、数字员工工具）的元信息、状态；
     * 2. 工具调用：智能体思考阶段从该集合中选择待执行的工具；
     * 核心能力：工具注册、更新、查询、执行结果存储
     */
    @ToString.Exclude
    @JSONField(serialize = false)
    ToolCollection toolCollection;

    /**
     * 主 Agent 的隐藏完整工具池，仅供 Agent 派发子 Agent 时筛选工具使用。
     */
    @ToString.Exclude
    @JSONField(serialize = false)
    ToolCollection subAgentToolCollection;

    /**
     * Reactor 运行时依赖包。
     * 所有 Agent / Tool / LLM 必须通过这里读取运行时协作者，禁止自行回 Spring 容器查找。
     */
    @ToString.Exclude
    @JSONField(serialize = false)
    ReactorRuntimeDependencies runtimeDependencies;

    /**
     * 日期时间信息（格式化字符串）
     * 用途：
     * 1. 会话上下文：由 BaseAgent 作为 session_env 用户消息预置，让 LLM 感知当前时间；
     * 2. 数据溯源：标记文件/任务的时间维度信息；
     * 格式示例："2026-02-19 15:30:00"
     */
    String dateInfo;

    /**
     * 产品相关文件列表（全局）
     * 用途：
     * 1. 上下文补充：为智能体提供产品基础信息（如商品详情、规格文档、售后政策）；
     * 2. 用户消息补充：由 BaseAgent 格式化后追加到本轮用户消息，供 LLM 参考；
     * 范围：覆盖当前会话的所有产品文件，粒度为「会话级」
     */
    List<File> productFiles;

    /**
     * 会话工作区根目录（cwd 模式，供 workspace_* 工具使用）。
     */
    String workspaceRoot;

    /**
     * 本轮会话 workspace_read 状态（对齐 cchaha readFileState：path + range + mtime）。
     */
    @Builder.Default
    @ToString.Exclude
    @JSONField(serialize = false)
    Map<String, WorkspaceFileReadState> workspaceReadStateByPath = new ConcurrentHashMap<>();

    /**
     * 是否流式响应
     * 用途：
     * 1. 响应模式控制：true=流式输出（逐字返回结果，提升用户体验），false=一次性返回结果；
     * 2. LLM调用适配：控制LLM的调用模式（流式/非流式）；
     * 核心场景：聊天类Agent优先设为true，批量任务类Agent设为false
     */
    Boolean isStream;

    /**
     * 流式消息类型
     * 用途：
     * 1. 前端适配：标识流式返回的消息分类，前端可按类型展示不同样式（如plan_thought=思考过程、task=任务步骤）；
     * 2. 消息过滤：按类型筛选/处理不同的流式消息；
     * 枚举值示例："plan_thought"、"task"、"plan"、"result"
     */
    String streamMessageType;

    /**
     * SOP提示词（标准作业流程提示词）
     * 用途：
     * 1. 流程约束：引导智能体按固定SOP执行任务（如电商客服话术流程、报告生成流程）；
     * 2. 提示词填充：填充到系统提示词的{{sopPrompt}}占位符，规范LLM的输出逻辑；
     * 示例："生成市场报告需包含：行业分析、竞品对比、结论建议三个部分，每部分不低于200字"
     */
    String sopPrompt;

    /**
     * 基础提示词模板（核心指令模板）
     * 用途：
     * 模板复用：作为智能体的核心指令模板，可替换占位符生成最终的系统提示词；
     */
    String basePrompt;


    // ---- memory ----
    /**
     * 会话历史摘要兼容字段；默认历史通过 workingMemoryMessages 进入 Memory。
     */
    String historyDialogue;

    /**
     * 跨轮工作记忆消息链（ledger hydrate 结果），run 前 preload 进 Memory。
     */
    List<Message> workingMemoryMessages;

    /**
     * 用户级策展记忆归属（LTM）。
     */
    LtmOwner ltmOwner;

    /**
     * 本轮深度记忆 prefetch 围栏文本（user 侧注入，非 system）。
     */
    String ltmMemoryContext;

    /**
     * 对齐 Hermes skip_memory：为 true 时禁止 memory tool 写用户画像（子代理等）。
     */
    @Builder.Default
    Boolean skipMemory = Boolean.FALSE;

    /**
     * LTM fork（flush/review）专用：允许 memory tool 写入，但禁止再 sync/再调度 review/prefetch 副作用。
     * 类似于 Hermes review fork 的 skip 外部 provider 污染，同时仍可写 builtin memory。
     */
    @Builder.Default
    Boolean ltmSideEffectsDisabled = Boolean.FALSE;

    /**
     * 智能体类型标识
     * 用途：
     * 1. 逻辑路由：区分不同类型的Agent（如1=规划型Agent、2=执行型Agent、3=客服型Agent）；
     * 2. 配置加载：根据类型加载对应的最大步骤、提示词模板、工具集合；
     * 枚举值示例：1=PlanningAgent，2=ExecutorAgent
     */
    Integer agentType;


    // ---- artifacts / ledger ----
    /**
     * 当前请求运行期的工具产物登记簿。
     * 这是工具文件来源的唯一事实来源。
     */
    @Builder.Default
    @ToString.Exclude
    @JSONField(serialize = false)
    ToolArtifactRegistry toolArtifactRegistry = new ToolArtifactRegistry();

    /**
     * 当前线程绑定的工具来源快照。
     * 同步工具直接读取；异步工具必须在 execute 阶段捕获后显式传递到回调线程。
     */
    @Builder.Default
    @ToString.Exclude
    @JSONField(serialize = false)
    ThreadLocal<ToolArtifactSource> currentToolArtifactSourceHolder = new ThreadLocal<>();

    /**
     * 子 Agent 嵌套展示元数据：父 Agent 工具的 toolCallId。
     * 仅 SubAgentContextFactory 写入；账本登记与 SSE 共用。
     */
    String parentToolUseId;

    /** 子 Agent 运行时 id（展示/账本） */
    String subAgentId;

    /** 子 Agent 类型（Explore / general-purpose 等） */
    String subAgentType;

    /** 子 Agent 任务短描述 */
    String subAgentDescription;

    /**
     * 当前请求的执行账本写入器。
     * 根节点初始化后挂入，LLM / BaseAgent / Summary 等运行时统一复用。
     */
    @ToString.Exclude
    @JSONField(serialize = false)
    AgentExecutionRecorder executionRecorder;

    /**
     * 当前请求的 run 级运行态。
     * 统一保存 runId、LLM 顺序号和 toolCallId 映射，并兼容并发 executor 的线程内视图。
     */
    @Builder.Default
    @ToString.Exclude
    @JSONField(serialize = false)
    AgentRunState agentRunState = AgentRunState.builder().build();


    // ---- run control ----
    /**
     * 会话 Todo 任务列表（TaskCreate/TaskGet）。
     * 主/子 Agent 共享同一引用；懒创建。
     */
    @ToString.Exclude
    @JSONField(serialize = false)
    SessionTaskListStore sessionTaskList;

    /**
     * 后台运行任务注册表（TaskStop）。
     */
    @Builder.Default
    @ToString.Exclude
    @JSONField(serialize = false)
    RuntimeBackgroundTaskRegistry backgroundTasks = new RuntimeBackgroundTaskRegistry();

    /**
     * Plan Mode 状态（EnterPlanMode / ExitPlanMode）。
     */
    @Builder.Default
    @ToString.Exclude
    @JSONField(serialize = false)
    PlanModeState planModeState = PlanModeState.builder().build();

    /**
     * 本轮协作式取消（用户停止 / SSE 断开）。
     */
    @ToString.Exclude
    @JSONField(serialize = false)
    RunCancellation runCancellation;

    /**
     * 运行中用户/协调注入队列（控制面；与 ActiveRun 共享同一实例）。
     */
    @ToString.Exclude
    @JSONField(serialize = false)
    @Builder.Default
    ConcurrentLinkedQueue<PendingInjectMessage> pendingInjects = new ConcurrentLinkedQueue<>();

    /**
     * working_memory 投影作用域：main 或 sub:{agentId}。
     */
    @Builder.Default
    String memoryScope = WorkingMemoryScopes.MAIN;

    /**
     * 提示词模板类型
     * 用途：
     * 1. 模板加载：根据类型加载不同场景的提示词模板（如default=通用模板、ecommerce=电商模板）；
     * 2. 场景适配：不同模板类型对应不同的提示词占位符、输出格式；
     * 枚举值示例："default"、"ecommerce"、"customer_service"、"market_analysis"
     */
    String templateType;

    /**
     * 获取或创建会话 Todo 列表（listId 优先 sessionId）。
     */
    public synchronized SessionTaskListStore requireSessionTaskList() {
        // Todo 列表按 sessionId 建立稳定归属；没有会话 ID 时退回 requestId，保证
        // 临时/测试上下文也不会共享默认列表。
        if (sessionTaskList == null) {
            String listId = sessionId != null && !sessionId.isBlank() ? sessionId : requestId;
            sessionTaskList = new SessionTaskListStore(listId == null ? "default" : listId);
        }
        return sessionTaskList;
    }

    public RuntimeBackgroundTaskRegistry requireBackgroundTasks() {
        // 这类运行态注册表允许由 builder 缺省创建，也兼容旧调用方传入 null；
        // 懒创建集中在上下文边界，工具不需要自行维护生命周期。
        if (backgroundTasks == null) {
            backgroundTasks = new RuntimeBackgroundTaskRegistry();
        }
        return backgroundTasks;
    }

    public PlanModeState requirePlanModeState() {
        // Plan Mode 是请求级状态机，缺省值在首次读取时补齐，避免空上下文让
        // Enter/ExitPlanMode 的行为依赖具体装配路径。
        if (planModeState == null) {
            planModeState = PlanModeState.builder().build();
        }
        return planModeState;
    }

    public boolean isRunCancelled() {
        return runCancellation != null && runCancellation.isCancelled();
    }

    public String getRunCancelReason() {
        return runCancellation == null ? null : runCancellation.getReason();
    }

    /**
     * 绑定与 ActiveRun 共享的 inject 队列（控制面，不 begin 新 run）。
     */
    public void bindPendingInjectQueue(ConcurrentLinkedQueue<PendingInjectMessage> queue) {
        if (queue != null) {
            this.pendingInjects = queue;
        }
    }

    public ConcurrentLinkedQueue<PendingInjectMessage> requirePendingInjects() {
        if (pendingInjects == null) {
            pendingInjects = new ConcurrentLinkedQueue<>();
        }
        return pendingInjects;
    }

    public void offerInject(PendingInjectMessage message) {
        if (message == null || message.getText() == null || message.getText().isBlank()) {
            return;
        }
        requirePendingInjects().offer(message);
    }

    /**
     * 取出并清空待注入消息（步进边界调用）。
     */
    public List<PendingInjectMessage> drainPendingInjects() {
        ConcurrentLinkedQueue<PendingInjectMessage> queue = requirePendingInjects();
        List<PendingInjectMessage> drained = new ArrayList<>();
        PendingInjectMessage next;
        while ((next = queue.poll()) != null) {
            drained.add(next);
        }
        return drained;
    }

    public String resolveMemoryScope() {
        return WorkingMemoryScopes.normalize(memoryScope);
    }

    public void bindCurrentToolArtifactSource(ToolArtifactSource toolArtifactSource) {
        // 当前来源通过 ThreadLocal 绑定到一次工具调用；异步回调不能隐式依赖
        // 线程切换后的值，必须在创建回调时显式捕获并传递 source。
        currentToolArtifactSourceHolder.set(toolArtifactSource);
    }

    public void clearCurrentToolArtifactSource() {
        currentToolArtifactSourceHolder.remove();
    }

    public ToolArtifactSource requireCurrentToolArtifactSource(String toolName) {
        ToolArtifactSource source = currentToolArtifactSourceHolder.get();
        if (source == null) {
            throw new IllegalStateException("Missing current tool artifact source for tool: " + toolName);
        }
        return source;
    }

    /**
     * 读取当前线程绑定的工具来源快照。
     * 主要用于前端实时事件补齐 toolCallId / toolName 等关联信息。
     */
    public ToolArtifactSource getCurrentToolArtifactSource() {
        return currentToolArtifactSourceHolder.get();
    }


    public void markWorkspaceFileRead(WorkspaceFileReadState state) {
        if (state == null || state.getAbsolutePath() == null || state.getAbsolutePath().isBlank()) {
            return;
        }
        if (workspaceReadStateByPath == null) {
            workspaceReadStateByPath = new ConcurrentHashMap<>();
        }
        workspaceReadStateByPath.put(state.getAbsolutePath(), state);
    }

    public WorkspaceFileReadState getWorkspaceFileReadState(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank() || workspaceReadStateByPath == null) {
            return null;
        }
        return workspaceReadStateByPath.get(absolutePath);
    }

    public boolean hasWorkspaceFileBeenRead(String absolutePath) {
        return getWorkspaceFileReadState(absolutePath) != null;
    }

    public Map<String, WorkspaceFileReadState> snapshotWorkspaceReadState() {
        if (workspaceReadStateByPath == null || workspaceReadStateByPath.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(workspaceReadStateByPath);
    }


    public ToolArtifactBinding registerGeneratedArtifact(ToolArtifactSource source, File file) {
        // 工具产物先登记到唯一 registry，再同步到会话级兼容文件列表；
        // 前端可见列表因此由 ledger/toolCall 绑定派生，而不是由工具自行拼装。
        return toolArtifactRegistry.registerGeneratedFile(
                source,
                file,
                ensureProductFiles()
        );
    }

    public List<ToolArtifactBinding> getArtifactBindingsByToolCallId(String toolCallId) {
        return toolArtifactRegistry.findBindingsByToolCallId(toolCallId);
    }

    public List<ToolArtifactBinding> getVisibleArtifactBindings() {
        return toolArtifactRegistry.listVisibleBindings();
    }

    public List<File> getVisibleArtifactFiles() {
        return getVisibleArtifactBindings().stream()
                .map(ToolArtifactBinding::getFile)
                .toList();
    }

    /**
     * 获取可见产物文件列表（按时间倒序，最新的在前）。
     * 避免外部调用时的双重拷贝和手动反转。
     */
    public List<File> getReversedVisibleArtifactFiles() {
        List<ToolArtifactBinding> bindings = toolArtifactRegistry.listVisibleBindings();
        List<File> result = new ArrayList<>(bindings.size());
        for (int i = bindings.size() - 1; i >= 0; i--) {
            result.add(bindings.get(i).getFile());
        }
        return result;
    }

    /**
     * 绑定本次请求的 run 主键与外部身份。
     */
    public void activateLedgerRun(Long runId, String runUid) {
        // run 激活只绑定账本身份，不重置上下文中的会话、工具或文件状态；后续
        // LLM/tool 记录点通过同一个 AgentRunState 取得顺序号和当前执行位置。
        ensureAgentRunState().setRunId(runId);
        ensureAgentRunState().setRunUid(runUid);
    }

    /**
     * 标记当前线程所在的 agent 与步号。
     * 这样 LLM 和工具记录点无需知道具体是哪一种 Agent 实现。
     */
    public void markExecutionPosition(String agentName, Integer stepNo) {
        ensureAgentRunState().markExecutionPosition(agentName, stepNo);
    }

    /**
     * 当前上下文是否已具备可用的执行账本能力。
     */
    public boolean hasActiveLedgerRun() {
        return executionRecorder != null
                && agentRunState != null
                && agentRunState.getRunId() != null;
    }

    /**
     * 为并发子任务创建轻量上下文分叉。
     * child context 共享 run 级依赖与账本事实，但复制任务态兼容视图，避免并发写回父上下文。
     */
    public AgentContext forkForParallelTask(String parallelTask) {
        // 并行子任务共享 printer、runtime、registry 和 ledger run 身份，以便事实
        // 仍归属于同一运行；任务文件、workspace 读状态和 ThreadLocal 则复制/隔离，
        // 防止子任务互相覆盖当前任务视图或工具来源。
        return AgentContext.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .query(query)
                .task(parallelTask)
                .printer(printer)
                .runtimeDependencies(runtimeDependencies)
                .dateInfo(dateInfo)
                .productFiles(copyFiles(productFiles))
                .workspaceRoot(workspaceRoot)
                .subAgentToolCollection(subAgentToolCollection)
                .workspaceReadStateByPath(copyWorkspaceReadState())
                .isStream(isStream)
                .streamMessageType(streamMessageType)
                .sopPrompt(sopPrompt)
                .basePrompt(basePrompt)
                .historyDialogue(historyDialogue)
                .workingMemoryMessages(workingMemoryMessages)
                .agentType(agentType)
                .toolArtifactRegistry(toolArtifactRegistry)
                .currentToolArtifactSourceHolder(new ThreadLocal<>())
                .executionRecorder(executionRecorder)
                .agentRunState(agentRunState)
                .templateType(templateType)
                .build();
    }

    private synchronized List<File> ensureProductFiles() {
        if (productFiles == null) {
            productFiles = new ArrayList<>();
        }
        return productFiles;
    }

    private synchronized AgentRunState ensureAgentRunState() {
        if (agentRunState == null) {
            agentRunState = AgentRunState.builder().build();
        }
        return agentRunState;
    }

    private Map<String, WorkspaceFileReadState> copyWorkspaceReadState() {
        Map<String, WorkspaceFileReadState> copy = new ConcurrentHashMap<>();
        if (workspaceReadStateByPath != null) {
            copy.putAll(workspaceReadStateByPath);
        }
        return copy;
    }

    private List<File> copyFiles(List<File> sourceFiles) {
        return sourceFiles == null ? new ArrayList<>() : new ArrayList<>(sourceFiles);
    }


    // ---- logical views (API-compatible field layout unchanged) ----

    public record RequestIdentity(String requestId, String sessionId, String query, String task) {
    }

    public record MemorySession(
            String historyDialogue,
            List<Message> workingMemoryMessages,
            LtmOwner ltmOwner,
            String ltmMemoryContext,
            Boolean skipMemory,
            Boolean ltmSideEffectsDisabled
    ) {
    }

    public record ToolSession(
            ToolCollection toolCollection,
            ToolArtifactRegistry toolArtifactRegistry,
            String workspaceRoot
    ) {
    }

    public record RunControl(
            Boolean isStream,
            String streamMessageType,
            RunCancellation runCancellation,
            PlanModeState planModeState,
            Integer agentType
    ) {
    }

    public RequestIdentity requestIdentity() {
        return new RequestIdentity(requestId, sessionId, query, task);
    }

    public MemorySession memorySession() {
        return new MemorySession(
                historyDialogue, workingMemoryMessages, ltmOwner, ltmMemoryContext, skipMemory, ltmSideEffectsDisabled);
    }

    public ToolSession toolSession() {
        return new ToolSession(toolCollection, toolArtifactRegistry, workspaceRoot);
    }

    public RunControl runControl() {
        return new RunControl(isStream, streamMessageType, runCancellation, planModeState, agentType);
    }

    @Override
    public String toString() {
        return "AgentContext(" +
                "requestId='" + requestId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", query='" + query + '\'' +
                ", task='" + task + '\'' +
                ", dateInfo='" + dateInfo + '\'' +
                ", historyDialogue='" + historyDialogue + '\'' +
                ", productFiles=" + productFiles +
                ", isStream=" + isStream +
                ", streamMessageType='" + streamMessageType + '\'' +
                ", agentType=" + agentType +
                ", templateType='" + templateType + '\'' +
                ')';
    }
}
