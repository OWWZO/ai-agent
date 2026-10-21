package org.wwz.ai.domain.agent.runtime.agent;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ToolExecutionPipeline 账本写入路径的契约回归。
 * <p>
 * 锁住拆分（把账本记录抽成独立类）时不能改变的行为：
 * 1. 预登记只在「有活跃 run + 有 llmInvocationId」时发生，否则整批跳过
 * 2. dispatchIndex 从 1 开始按命令顺序递增，被跳过的命令不占位
 * 3. 终态写入带 structuredOutput / errorMsg，backgroundAck 除外（见 BackgroundAgentLedgerStatusTest）
 * 4. 产物只在有绑定且有 toolInvocationId 时记录，内部文件标 internal
 */
public class ToolExecutionPipelineLedgerTest {

    private static final String TOOL_CALL_ID = "call-ledger-1";
    private static final long TOOL_INVOCATION_ID = 777L;
    private static final long RUN_ID = 42L;
    private static final long LLM_INVOCATION_ID = 900L;

    private AgentExecutionRecorder recorder;
    private AgentContext context;

    @Test
    public void preRegisterSkipsEntireBatchWhenLlmInvocationIdMissing() {
        setUpHarness(null, Map.of());
        AgentRunState runState = context.getAgentRunState();
        // 没有 llmInvocationId：整批不登记，避免产生悬空的 tool_invocation 行。
        runState.bindCurrentLlmInvocationId(null);

        pipeline().executeBatch(List.of(newToolCall("demo_tool")));

        verify(recorder, never()).createToolInvocations(any());
    }

    @Test
    public void preRegisterAssignsSequentialDispatchIndexSkippingInvalidCalls() {
        setUpHarness(LLM_INVOCATION_ID, Map.of());
        ToolCall invalid = ToolCall.builder().id("call-broken").build(); // 无 function

        pipeline().executeBatch(List.of(
                newToolCall("call-a", "first"),
                invalid,
                newToolCall("call-b", "second")));

        ArgumentCaptor<ToolInvocationBatchStartRecord> captor =
                ArgumentCaptor.forClass(ToolInvocationBatchStartRecord.class);
        verify(recorder).createToolInvocations(captor.capture());
        List<ToolInvocationBatchStartRecord.Item> items = captor.getValue().getItems();

        // 无效命令被跳过且不占 dispatchIndex，避免前端按 index 排序时出现空洞。
        Assert.assertEquals(2, items.size());
        Assert.assertEquals(Integer.valueOf(1), items.get(0).getDispatchIndex());
        Assert.assertEquals("first", items.get(0).getToolName());
        Assert.assertEquals(Integer.valueOf(2), items.get(1).getDispatchIndex());
        Assert.assertEquals("second", items.get(1).getToolName());
    }

    @Test
    public void finishCarriesToolNameAndTerminalStatus() {
        setUpHarness(LLM_INVOCATION_ID, Map.of(TOOL_CALL_ID, TOOL_INVOCATION_ID));

        pipeline().executeOne(newToolCall("demo_tool"));

        ArgumentCaptor<ToolInvocationFinishRecord> captor =
                ArgumentCaptor.forClass(ToolInvocationFinishRecord.class);
        verify(recorder).finishToolInvocation(captor.capture());
        ToolInvocationFinishRecord record = captor.getValue();

        Assert.assertEquals(Long.valueOf(TOOL_INVOCATION_ID), record.getToolInvocationId());
        Assert.assertEquals(Long.valueOf(RUN_ID), record.getRunId());
        Assert.assertEquals(TOOL_CALL_ID, record.getToolCallId());
        Assert.assertEquals("demo_tool", record.getToolName());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_SUCCESS), record.getStatus());
        Assert.assertNotNull("正常工具必须有 finished_at", record.getFinishedAt());
        // stub 未提供 structuredOutput，账本字段应保持 null 而不是空对象。
        Assert.assertNull(record.getStructuredOutput());
        Assert.assertNull(record.getErrorMsg());
    }

    @Test
    public void finishSkipsWhenToolInvocationIdUnresolved() {
        // runState 里没有该 toolCallId 的账本 ID：不写终态，避免写出孤儿行。
        setUpHarness(LLM_INVOCATION_ID, Map.of());

        pipeline().executeOne(newToolCall("demo_tool"));

        verify(recorder, never()).finishToolInvocation(any());
    }

    @Test
    public void artifactsAreRecordedWithVisibilityAndSourceName() {
        setUpHarness(LLM_INVOCATION_ID, Map.of(TOOL_CALL_ID, TOOL_INVOCATION_ID));

        ToolArtifactSource source = ToolArtifactSource.builder()
                .sessionId("session-ledger")
                .requestId("req-ledger")
                .toolCallId(TOOL_CALL_ID)
                .toolName("demo_tool")
                .build();
        File visible = File.builder()
                .fileName("report.html")
                .ossUrl("https://oss/report.html")
                .domainUrl("https://cdn/report.html")
                .fileSize(2048)
                .build();
        File internal = File.builder()
                .fileName("scratch.txt")
                .ossUrl("https://oss/scratch.txt")
                .isInternalFile(true)
                .build();
        context.registerGeneratedArtifact(source, visible);
        context.registerGeneratedArtifact(source, internal);

        pipeline().executeOne(newToolCall("demo_tool"));

        ArgumentCaptor<List<ArtifactRecordCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(recorder).recordArtifacts(captor.capture());
        List<ArtifactRecordCommand> commands = captor.getValue();

        Assert.assertEquals(2, commands.size());
        ArtifactRecordCommand first = commands.get(0);
        Assert.assertEquals("report.html", first.getFileName());
        Assert.assertEquals(ExecutionLedgerConstants.VISIBILITY_VISIBLE, first.getVisibility());
        Assert.assertEquals(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT, first.getSourceType());
        Assert.assertEquals("demo_tool", first.getSourceName());
        Assert.assertEquals(Long.valueOf(TOOL_INVOCATION_ID), first.getToolInvocationId());
        Assert.assertEquals("https://oss/report.html", first.getDownloadUrl());
        Assert.assertEquals("https://cdn/report.html", first.getPreviewUrl());

        ArtifactRecordCommand second = commands.get(1);
        Assert.assertEquals(ExecutionLedgerConstants.VISIBILITY_INTERNAL, second.getVisibility());
    }

    @Test
    public void artifactsAreSkippedWithoutActiveLedgerRun() {
        setUpHarness(LLM_INVOCATION_ID, Map.of());
        ToolArtifactSource source = ToolArtifactSource.builder()
                .sessionId("session-ledger")
                .requestId("req-ledger")
                .toolCallId(TOOL_CALL_ID)
                .toolName("demo_tool")
                .build();
        context.registerGeneratedArtifact(source, File.builder().fileName("x.txt").build());

        pipeline().executeOne(newToolCall("demo_tool"));

        verify(recorder, never()).recordArtifacts(any());
    }

    private ToolExecutionPipeline pipeline() {
        BaseAgent agent = mock(BaseAgent.class);
        when(agent.getContext()).thenReturn(context);
        when(agent.getPrinter()).thenReturn(new NoopPrinter());
        when(agent.getAvailableTools()).thenReturn(toolsWithStub());
        when(agent.getName()).thenReturn("react");
        when(agent.getCurrentStep()).thenReturn(1);
        when(agent.buildFinalLlmObservation(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new ToolExecutionPipeline(agent);
    }

    private ToolCollection toolsWithStub() {
        ToolCollection tools = new ToolCollection();
        tools.setAgentContext(context);
        for (String name : List.of("demo_tool", "first", "second")) {
            tools.addTool(new StubTool(name, () -> ToolResultPayload.fromData(Map.of())));
        }
        return tools;
    }

    private void setUpHarness(Long llmInvocationId, Map<String, Long> toolInvocationIds) {
        recorder = mock(AgentExecutionRecorder.class);
        when(recorder.createToolInvocations(any())).thenReturn(toolInvocationIds);
        AgentRunState runState = AgentRunState.builder().runId(RUN_ID).build();
        runState.bindCurrentLlmInvocationId(llmInvocationId);
        runState.bindToolInvocationIds(toolInvocationIds);
        context = AgentContext.builder()
                .requestId("req-ledger")
                .sessionId("session-ledger")
                .executionRecorder(recorder)
                .agentRunState(runState)
                .build();
    }

    private static ToolCall newToolCall(String toolName) {
        return newToolCall(TOOL_CALL_ID, toolName);
    }

    private static ToolCall newToolCall(String toolCallId, String toolName) {
        return ToolCall.builder()
                .id(toolCallId)
                .type("function")
                .function(ToolCall.Function.builder()
                        .name(toolName)
                        .arguments("{}")
                        .build())
                .build();
    }

    private static final class StubTool implements BaseTool {
        private final String name;
        private final Supplier<ToolResultPayload> payloadSupplier;

        private StubTool(String name, Supplier<ToolResultPayload> payloadSupplier) {
            this.name = name;
            this.payloadSupplier = payloadSupplier;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return name;
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of();
        }

        @Override
        public Object execute(Object input) {
            return payloadSupplier.get();
        }
    }

    private static final class NoopPrinter implements Printer {
        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageType, Object message) {
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(org.wwz.ai.domain.agent.runtime.enums.AgentType agentType) {
        }
    }
}
