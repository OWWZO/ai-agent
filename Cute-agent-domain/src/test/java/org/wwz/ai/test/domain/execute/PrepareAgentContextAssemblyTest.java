package org.wwz.ai.test.domain.execute;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.cute.config.CuteConfig;
import org.wwz.ai.domain.agent.runtime.CuteRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.capability.SessionCapabilityService;
import org.wwz.ai.domain.agent.runtime.capability.SessionCapabilityService.DisabledSets;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionFile;
import org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalDecision;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.runtime.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.service.execute.support.PrepareAgentContextAssembly;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceSessionFileMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadStateStore;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.PrepareAgentContextNode;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * React / PlanSolve 两个 Prepare 节点的装配语义。
 * <p>
 * 两条链路各有自己的 PrepareAgentContextNode，但 AgentContext 构造、附件物化、
 * workspace read-state hydrate、resume plan mode 恢复都是逐字重复的。这组测试锁住
 * 这些语义，防止收敛共用装配体时改掉任何一条链路看到的行为。
 */
public class PrepareAgentContextAssemblyTest {

    private CapturingRecorder recorder;

    @Before
    public void setUp() {
        recorder = new CapturingRecorder();
    }

    @Test
    public void reactBuildsContextFromRequestAndInitializesLedgerRun() throws Exception {
        org.wwz.ai.domain.agent.service.execute.react.step.PrepareAgentContextNode node =
                newReactNode(DisabledSets.empty(), null, null);
        DefaultReactAgentExecuteStrategyFactory.DynamicContext ctx =
                DefaultReactAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(noopPrinter())
                        .build();

        node.apply(request("req-react-asm-1", "session-react-asm-1"), ctx);

        AgentContext built = ctx.getAgentContext();
        Assert.assertNotNull("Prepare 必须产出 AgentContext", built);
        Assert.assertEquals("req-react-asm-1", built.getRequestId());
        Assert.assertEquals("session-react-asm-1", built.getSessionId());
        Assert.assertEquals("帮我查一下订单", built.getQuery());
        Assert.assertFalse(built.getIsStream());
        Assert.assertEquals(ExecutionLedgerConstants.ENTRY_AGENT_REACT, recorder.entryAgent);
        Assert.assertEquals("table", built.getTemplateType());
        Assert.assertEquals(1, built.getProductFiles().size());
        Assert.assertEquals("订单.xlsx", built.getProductFiles().get(0).getFileName());
        Assert.assertEquals(Integer.valueOf(1024), built.getProductFiles().get(0).getFileSize());
        Assert.assertFalse(Boolean.TRUE.equals(built.getProductFiles().get(0).getIsInternalFile()));
        Assert.assertNotNull("React 主工具集必须已装配", built.getToolCollection());
    }

    @Test
    public void planSolveBuildsContextWithPlanSolveEntryAndFiltersMainTools() throws Exception {
        PrepareAgentContextNode node = newPlanSolveNode(DisabledSets.empty(), null, null);
        DynamicContext ctx = DynamicContext.builder()
                .printer(noopPrinter())
                .build();

        node.apply(request("req-plan-asm-1", "session-plan-asm-1"), ctx);

        AgentContext built = ctx.getAgentContext();
        Assert.assertNotNull(built);
        Assert.assertEquals(ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE, recorder.entryAgent);
        // 主代理工具集被过滤，子代理保留全量工具
        Assert.assertNotNull("PlanSolve 主代理工具集必须已装配", built.getToolCollection());
        Assert.assertNotNull("PlanSolve 子代理工具集必须保留全量工具", built.getSubAgentToolCollection());
    }

    @Test
    public void bothLinksPropagatesDisabledCapabilitiesIntoContext() throws Exception {
        DisabledSets disabled = new DisabledSets(Set.of("web_search"), Set.of("mcp-clickhouse"));

        org.wwz.ai.domain.agent.service.execute.react.step.PrepareAgentContextNode reactNode =
                newReactNode(disabled, null, null);
        DefaultReactAgentExecuteStrategyFactory.DynamicContext reactCtx =
                DefaultReactAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(noopPrinter()).build();
        reactNode.apply(request("req-react-asm-2", "session-react-asm-2"), reactCtx);
        Assert.assertTrue(reactCtx.getAgentContext().getDisabledSkillNames().contains("web_search"));
        Assert.assertTrue(reactCtx.getAgentContext().getDisabledMcpIds().contains("mcp-clickhouse"));

        CapturingRecorder planRecorder = new CapturingRecorder();
        recorder = planRecorder;
        PrepareAgentContextNode planNode = newPlanSolveNode(disabled, null, null);
        DynamicContext planCtx = DynamicContext.builder().printer(noopPrinter()).build();
        planNode.apply(request("req-plan-asm-2", "session-plan-asm-2"), planCtx);
        Assert.assertTrue(planCtx.getAgentContext().getDisabledSkillNames().contains("web_search"));
        Assert.assertTrue(planCtx.getAgentContext().getDisabledMcpIds().contains("mcp-clickhouse"));
    }

    @Test
    public void bothLinksRestoreApprovedPlanOnResume() throws Exception {
        // resume 一个已批准的 ExitPlanMode：编辑后的 plan 内容应落到 PlanModeState 并退出 plan mode
        String resumeContextJson = UserQuestionResumeContextJson.planMode("plan", "旧计划", ".reactor/plan.md");
        StubPlanApprovalRepository approvals = new StubPlanApprovalRepository(
                PlanApprovalRecord.builder()
                        .approvalId("pa-asm-1")
                        .planContent("已批准的计划")
                        .planFilePath(".reactor/plan.md")
                        .decision(PlanApprovalDecision.builder()
                                .approved(true)
                                .editedPlanContent("修订后的计划")
                                .build())
                        .build());

        org.wwz.ai.domain.agent.service.execute.react.step.PrepareAgentContextNode reactNode =
                newReactNode(DisabledSets.empty(), approvals, null);
        DefaultReactAgentExecuteStrategyFactory.DynamicContext reactCtx =
                DefaultReactAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(noopPrinter()).build();
        reactNode.apply(resumeRequest("req-react-asm-3", resumeContextJson), reactCtx);

        PlanModeState state = reactCtx.getAgentContext().getPlanModeState();
        Assert.assertNotNull(state);
        Assert.assertEquals("修订后的计划", state.getPlanContent());
        Assert.assertFalse("已批准的 plan 应退出 plan mode", state.isPlanMode());

        CapturingRecorder planRecorder = new CapturingRecorder();
        recorder = planRecorder;
        PrepareAgentContextNode planNode = newPlanSolveNode(DisabledSets.empty(), approvals, null);
        DynamicContext planCtx = DynamicContext.builder().printer(noopPrinter()).build();
        planNode.apply(resumeRequest("req-plan-asm-3", resumeContextJson), planCtx);

        PlanModeState planState = planCtx.getAgentContext().getPlanModeState();
        Assert.assertNotNull(planState);
        Assert.assertEquals("修订后的计划", planState.getPlanContent());
        Assert.assertFalse(planState.isPlanMode());
    }

    @Test
    public void bothLinksBindsActiveRunWhenRegistryPresent() throws Exception {
        RecordingActiveAgentRunRegistry registry = new RecordingActiveAgentRunRegistry();
        org.wwz.ai.domain.agent.service.execute.react.step.PrepareAgentContextNode reactNode =
                newReactNode(DisabledSets.empty(), null, registry);
        DefaultReactAgentExecuteStrategyFactory.DynamicContext reactCtx =
                DefaultReactAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(noopPrinter()).build();
        reactNode.apply(request("req-react-asm-4", "session-react-asm-4"), reactCtx);
        Assert.assertEquals("req-react-asm-4", registry.boundRequestId);

        CapturingRecorder planRecorder = new CapturingRecorder();
        recorder = planRecorder;
        RecordingActiveAgentRunRegistry planRegistry = new RecordingActiveAgentRunRegistry();
        PrepareAgentContextNode planNode = newPlanSolveNode(DisabledSets.empty(), null, planRegistry);
        DynamicContext planCtx = DynamicContext.builder().printer(noopPrinter()).build();
        planNode.apply(request("req-plan-asm-4", "session-plan-asm-4"), planCtx);
        Assert.assertEquals("req-plan-asm-4", planRegistry.boundRequestId);
    }

    // ---------- fixtures ----------

    private static AgentExecutionCommand request(String requestId, String sessionId) {
        return AgentExecutionCommand.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .userId("user-asm")
                .query("帮我查一下订单")
                .model("gpt-4o-mini")
                .agentType(1)
                .outputStyle("dataAgent")
                .isStream(false)
                .sessionFiles(List.of(AgentExecutionFile.builder()
                        .fileName("订单.xlsx")
                        .fileDesc("订单明细")
                        .ossUrl("https://oss.example.com/a.xlsx")
                        .domainUrl("https://cdn.example.com/a.xlsx")
                        .fileSize(1024)
                        .originFileName("origin.xlsx")
                        .build()))
                .build();
    }

    private static AgentExecutionCommand resumeRequest(String requestId, String resumeContextJson) {
        return AgentExecutionCommand.builder()
                .requestId(requestId)
                .sessionId("session-" + requestId)
                .userId("user-asm")
                .query("继续")
                .model("gpt-4o-mini")
                .agentType(1)
                .resumeContextJson(resumeContextJson)
                .resumeApprovalId("pa-asm-1")
                .build();
    }

    private static Printer noopPrinter() {
        return new Printer() {
            @Override
            public void send(String messageId, String messageType, Object message,
                             String digitalEmployee, Boolean isFinal) {
            }

            @Override
            public void send(String messageId, String messageType, Object message,
                             Map<String, Object> extraResultMap, String digitalEmployee,
                             Boolean isFinal) {
            }

            @Override
            public void close() {
            }

            @Override
            public void updateAgentType(org.wwz.ai.domain.agent.runtime.enums.AgentType agentType) {
            }
        };
    }

    private org.wwz.ai.domain.agent.service.execute.react.step.PrepareAgentContextNode newReactNode(
            DisabledSets disabled, StubPlanApprovalRepository approvals,
            RecordingActiveAgentRunRegistry registry) {
        org.wwz.ai.domain.agent.service.execute.react.step.PrepareAgentContextNode node =
                new org.wwz.ai.domain.agent.service.execute.react.step.PrepareAgentContextNode();
        set(node, "prepareAgentContextAssembly", assembly(disabled, approvals, registry));
        set(node, "runReactLoopNode", Mockito.mock(
                org.wwz.ai.domain.agent.service.execute.react.step.RunReactLoopNode.class));
        return node;
    }

    private PrepareAgentContextNode newPlanSolveNode(DisabledSets disabled,
                                                     StubPlanApprovalRepository approvals,
                                                     RecordingActiveAgentRunRegistry registry) {
        PrepareAgentContextNode node = new PrepareAgentContextNode();
        set(node, "prepareAgentContextAssembly", assembly(disabled, approvals, registry));
        set(node, "runReactLoopNode", Mockito.mock(
                org.wwz.ai.domain.agent.service.execute.planexecute.step.RunReactLoopNode.class));
        // PlanSolve 的 prior-run 探测：测试不接 ledger，返回空即"首轮"
        set(node, "executionLedgerReadRepository", emptyLedgerReader());
        return node;
    }

    /**
     * 工具集合不在本测试的关注范围：两个入口都返回真实空集合，
     * 让 Prepare 能完成 setToolCollection，而不是把 null 塞进上下文。
     */
    private static AgentToolCollectionFactory toolFactory() {
        AgentToolCollectionFactory factory = Mockito.mock(AgentToolCollectionFactory.class);
        Mockito.when(factory.buildForReact(Mockito.any(), Mockito.any()))
                .thenReturn(new ToolCollection());
        Mockito.when(factory.buildForPlanSolve(Mockito.any(), Mockito.any()))
                .thenReturn(new ToolCollection());
        Mockito.when(factory.filterForPlanSolveMain(Mockito.any()))
                .thenReturn(new ToolCollection());
        return factory;
    }

    /**
     * 装配体现为两个节点共用的可测缝：这里直接把它实例化并注入依赖，
     * 断言因此覆盖的是真实装配逻辑，而不是节点转发。
     */
    private PrepareAgentContextAssembly assembly(DisabledSets disabled,
                                                 StubPlanApprovalRepository approvals,
                                                 RecordingActiveAgentRunRegistry registry) {
        PrepareAgentContextAssembly assembly = new PrepareAgentContextAssembly();
        set(assembly, "agentExecutionRecorder", recorder);
        set(assembly, "sessionCapabilityService", stubCapability(disabled));
        set(assembly, "agentToolCollectionFactory", toolFactory());
        set(assembly, "cuteRuntimeDependencies", runtimeDependencies());
        set(assembly, "activeAgentRunRegistry", registry != null ? registry : new RecordingActiveAgentRunRegistry());
        set(assembly, "planApprovalRepository", approvals);
        set(assembly, "workspaceService", disabledWorkspaceService());
        set(assembly, "workspaceSessionFileMaterializer", Mockito.mock(WorkspaceSessionFileMaterializer.class));
        set(assembly, "workspaceReadStateStore", Mockito.mock(WorkspaceReadStateStore.class));
        return assembly;
    }

    /** 测试不接 ledger：查不到 prior run 即视为首轮 PlanSolve。 */
    private static IExecutionLedgerReadRepository emptyLedgerReader() {
        IExecutionLedgerReadRepository reader = Mockito.mock(IExecutionLedgerReadRepository.class);
        Mockito.when(reader.queryRunsBySessionId(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(List.of());
        return reader;
    }

    private static SessionCapabilityService stubCapability(DisabledSets disabled) {
        SessionCapabilityService service = Mockito.mock(SessionCapabilityService.class);
        Mockito.when(service.loadDisabled(Mockito.anyString())).thenReturn(disabled);
        return service;
    }

    private static CuteRuntimeDependencies runtimeDependencies() {
        return CuteRuntimeDependencies.builder().build();
    }

    private static WorkspaceService disabledWorkspaceService() {
        WorkspaceService service = Mockito.mock(WorkspaceService.class);
        Mockito.when(service.isEnabled()).thenReturn(false);
        return service;
    }

    private static void set(Object target, String field, Object value) {
        java.lang.reflect.Field f;
        try {
            f = target.getClass().getDeclaredField(field);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("missing field " + field, e);
        }
        f.setAccessible(true);
        try {
            f.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 构造 resumeContextJson：与 UserQuestionResumeContext 的序列化形状一致。 */
    private static final class UserQuestionResumeContextJson {
        private UserQuestionResumeContextJson() {
        }

        private static String planMode(String mode, String planContent, String planFilePath) {
            return "{\"entryAgent\":\"plan_solve\",\"planMode\":{"
                    + "\"mode\":\"" + mode + "\","
                    + "\"planContent\":\"" + planContent + "\","
                    + "\"planFilePath\":\"" + planFilePath + "\""
                    + "}}";
        }
    }

    /** 捕获 initializeRun 写入的 entryAgent。 */
    private static final class CapturingRecorder implements AgentExecutionRecorder {
        private String entryAgent;

        @Override
        public Long createRun(DialogueRunStartRecord record) {
            this.entryAgent = record.getEntryAgent();
            return 9001L;
        }

        @Override
        public void finishRun(DialogueRunFinishRecord record) {
        }

        @Override
        public Long createLlmInvocation(LlmInvocationStartRecord record) {
            return 1L;
        }

        @Override
        public void finishLlmInvocation(LlmInvocationFinishRecord record) {
        }

        @Override
        public Map<String, Long> createToolInvocations(ToolInvocationBatchStartRecord record) {
            return Map.of();
        }

        @Override
        public void finishToolInvocation(ToolInvocationFinishRecord record) {
        }

        @Override
        public void recordArtifacts(List<ArtifactRecordCommand> records) {
        }

        @Override
        public void recordArtifactsOrThrow(List<ArtifactRecordCommand> records) {
        }
    }

    private static final class StubPlanApprovalRepository implements IPlanApprovalRepository {
        private final PlanApprovalRecord record;

        private StubPlanApprovalRepository(PlanApprovalRecord record) {
            this.record = record;
        }

        @Override
        public void insert(PlanApprovalRecord record) {
        }

        @Override
        public Optional<PlanApprovalRecord> findByApprovalId(String approvalId) {
            return Optional.ofNullable(record);
        }

        @Override
        public Optional<PlanApprovalRecord> findByResumeRequestId(String resumeRequestId) {
            return Optional.empty();
        }

        @Override
        public List<PlanApprovalRecord> listOpenBySessionId(String sessionId) {
            return List.of();
        }

        @Override
        public boolean hasOpenBySessionId(String sessionId) {
            return false;
        }

        @Override
        public boolean casDecidePending(String approvalId, String userId,
                                        PlanApprovalDecision decision, String resumeRequestId) {
            return false;
        }

        @Override
        public boolean casClaimResume(String resumeRequestId, String userId) {
            return false;
        }

        @Override
        public boolean markAnswered(String approvalId) {
            return false;
        }

        @Override
        public boolean markStatus(String approvalId, String status) {
            return false;
        }

        @Override
        public boolean casCancel(String approvalId, String userId) {
            return false;
        }

        @Override
        public int cancelBySourceRequestId(String sourceRequestId, String reason) {
            return 0;
        }
    }

    private static final class RecordingActiveAgentRunRegistry extends ActiveAgentRunRegistry {
        private String boundRequestId;

        @Override
        public void bindContext(String requestId, org.wwz.ai.domain.agent.runtime.agent.AgentContext ctx) {
            this.boundRequestId = requestId;
        }
    }
}
