package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.dto.Plan;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchStage;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputView;

import java.util.List;

/**
 * 输出表 reader 契约测试。
 */
public class ToolStructuredOutputReaderTest {

    @Test
    public void shouldReadByInvocationIdAndDirectLookup() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .toolInvocationId(301L)
                .runId(401L)
                .requestId("req-reader-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId("session-reader-001")
                .toolCallId("tool-call-reader-001")
                .toolName("deep_search")
                .status(ExecutionLedgerConstants.STATUS_FAILED)
                .errorMsg("timeout")
                .structuredOutput(DeepSearchToolOutput.of(
                        "AI 芯片供应链",
                        "timeout",
                        List.of(DeepSearchStage.extend(List.of("AI 芯片供应链")))
                ))
                .build());

        DeepSearchToolOutput byInvocation = (DeepSearchToolOutput) ctx.toolOutputReader
                .readByInvocationId("deep_search", 301L)
                .orElseThrow();
        ToolOutputView direct = ctx.toolOutputReader.readDirect("req-reader-001", "tool-call-reader-001")
                .orElseThrow();

        Assert.assertEquals("AI 芯片供应链", byInvocation.getQuery());
        Assert.assertEquals(1, byInvocation.getStages().size());
        Assert.assertEquals("extend", byInvocation.getStages().get(0).getStage());
        Assert.assertEquals("deep_search", direct.getToolName());
        Assert.assertEquals(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT, direct.getRequestSource());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), direct.getStatus());
    }

    @Test
    public void shouldReturnEmptyWhenDirectLookupHitsMultipleToolTables() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .requestId("req-reader-conflict-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .toolCallId("tool-call-conflict-001")
                .toolName("file_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(FileToolOutput.builder()
                        .command("upload")
                        .primaryFileName("conflict-a.md")
                        .build())
                .build());
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .requestId("req-reader-conflict-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .toolCallId("tool-call-conflict-001")
                .toolName("report_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(ReportToolOutput.builder()
                        .summary("conflict")
                        .content("conflict")
                        .build())
                .build());

        Assert.assertTrue(ctx.toolOutputReader.readDirect("req-reader-conflict-001", "tool-call-conflict-001").isEmpty());
    }

    @Test
    public void shouldReadFileToolPreviewUrlByInvocationIdAndDirectLookup() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .toolInvocationId(501L)
                .runId(601L)
                .requestId("req-reader-file-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId("session-reader-file-001")
                .toolCallId("tool-call-reader-file-001")
                .toolName("file_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(FileToolOutput.builder()
                        .command("get")
                        .primaryFileName("风险日报.md")
                        .previewUrl("https://file.example.com/preview/risk.md")
                        .downloadUrl("https://file.example.com/download/risk.md")
                        .build())
                .build());

        FileToolOutput byInvocation = (FileToolOutput) ctx.toolOutputReader
                .readByInvocationId("file_tool", 501L)
                .orElseThrow();
        ToolOutputView direct = ctx.toolOutputReader
                .readDirect("req-reader-file-001", "tool-call-reader-file-001")
                .orElseThrow();
        FileToolOutput directOutput = (FileToolOutput) direct.getStructuredOutput();

        Assert.assertEquals("风险日报.md", byInvocation.getPrimaryFileName());
        Assert.assertEquals("https://file.example.com/preview/risk.md", byInvocation.getPreviewUrl());
        Assert.assertEquals("https://file.example.com/download/risk.md", byInvocation.getDownloadUrl());
        Assert.assertEquals("https://file.example.com/preview/risk.md", directOutput.getPreviewUrl());
        Assert.assertEquals("https://file.example.com/download/risk.md", directOutput.getDownloadUrl());
        Assert.assertEquals("file_tool", direct.getToolName());
        Assert.assertEquals(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT, direct.getRequestSource());
    }

    @Test
    public void shouldReadPlanningStructuredOutputByInvocationIdAndDirectLookup() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .toolInvocationId(601L)
                .runId(701L)
                .requestId("req-reader-planning-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId("session-reader-planning-001")
                .toolCallId("tool-call-reader-planning-001")
                .toolName("planning")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(PlanningToolOutput.builder()
                        .command("update")
                        .beforePlan(Plan.builder()
                                .title("旧计划")
                                .steps(List.of("步骤一", "步骤二"))
                                .stepStatus(List.of("completed", "in_progress"))
                                .notes(List.of("已完成", ""))
                                .build())
                        .afterPlan(Plan.builder()
                                .title("新计划")
                                .steps(List.of("步骤一", "新步骤A", "新步骤B"))
                                .stepStatus(List.of("completed", "in_progress", "not_started"))
                                .notes(List.of("已完成", "", ""))
                                .build())
                        .currentStep("新步骤A")
                        .currentStepIndex(1)
                        .autoAdvanced(true)
                        .autoFinished(false)
                        .build())
                .build());

        PlanningToolOutput byInvocation = (PlanningToolOutput) ctx.toolOutputReader
                .readByInvocationId("planning", 601L)
                .orElseThrow();
        ToolOutputView direct = ctx.toolOutputReader
                .readDirect("req-reader-planning-001", "tool-call-reader-planning-001")
                .orElseThrow();

        Assert.assertEquals("update", byInvocation.getCommand());
        Assert.assertEquals("新步骤A", byInvocation.getCurrentStep());
        Assert.assertEquals(Integer.valueOf(1), byInvocation.getCurrentStepIndex());
        Assert.assertEquals("新计划", byInvocation.getAfterPlan().getTitle());
        Assert.assertEquals("planning", direct.getToolName());
        Assert.assertEquals(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT, direct.getRequestSource());
    }
}
