package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputView;
import org.wwz.ai.domain.agent.runtime.dto.Plan;

import java.util.List;

/**
 * 输出表 writer 契约测试。
 */
public class ToolStructuredOutputWriterTest {

    @Test
    public void shouldPersistRichToolOutputAndKeepFirstWriteWins() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ToolOutputPersistCommand first = ToolOutputPersistCommand.builder()
                .toolInvocationId(101L)
                .runId(201L)
                .requestId("req-writer-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId("session-writer-001")
                .toolCallId("tool-call-writer-001")
                .toolName("file_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(FileToolOutput.builder()
                        .command("upload")
                        .primaryFileName("report-a.md")
                        .previewUrl("https://file.example.com/preview/report-a.md")
                        .downloadUrl("https://file.example.com/download/report-a.md")
                        .fileRefs(List.of(ToolFileRef.builder().fileName("report-a.md").build()))
                        .build())
                .build();
        ToolOutputPersistCommand duplicate = ToolOutputPersistCommand.builder()
                .toolInvocationId(101L)
                .runId(201L)
                .requestId("req-writer-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId("session-writer-001")
                .toolCallId("tool-call-writer-001")
                .toolName("file_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(FileToolOutput.builder()
                        .command("upload")
                        .primaryFileName("report-b.md")
                        .previewUrl("https://file.example.com/preview/report-b.md")
                        .downloadUrl("https://file.example.com/download/report-b.md")
                        .fileRefs(List.of(ToolFileRef.builder().fileName("report-b.md").build()))
                        .build())
                .build();

        ctx.toolOutputWriter.write(first);
        ctx.toolOutputWriter.write(duplicate);

        ToolOutputView outputView = ctx.toolOutputReader.readDirect("req-writer-001", "tool-call-writer-001")
                .orElseThrow();
        FileToolOutput structuredOutput = (FileToolOutput) outputView.getStructuredOutput();

        Assert.assertTrue(ctx.toolOutputReader.readByInvocationId("file_tool", 101L).isPresent());
        Assert.assertEquals("file_tool", outputView.getToolName());
        Assert.assertEquals(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT, outputView.getRequestSource());
        Assert.assertEquals("session-writer-001", outputView.getSessionId());
        Assert.assertEquals("report-a.md", structuredOutput.getPrimaryFileName());
        Assert.assertEquals("https://file.example.com/preview/report-a.md", structuredOutput.getPreviewUrl());
        Assert.assertEquals("https://file.example.com/download/report-a.md", structuredOutput.getDownloadUrl());
        Assert.assertEquals(1, structuredOutput.getFileRefs().size());
        Assert.assertEquals("report-a.md", structuredOutput.getFileRefs().get(0).getFileName());
    }

    @Test
    public void shouldSupportDirectToolCallWithoutLedgerFieldsAndKeepFirstWriteWins() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .requestId("req-writer-direct-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE)
                .toolCallId("tool-call-direct-001")
                .toolName("image_generation_tool")
                .status(ExecutionLedgerConstants.STATUS_FAILED)
                .errorMsg("upstream timeout")
                .structuredOutput(ImageGenerationToolOutput.builder()
                        .prompt("sunrise over lake")
                        .mode("images")
                        .summary("upstream timeout")
                        .build())
                .build());
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .requestId("req-writer-direct-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE)
                .toolCallId("tool-call-direct-001")
                .toolName("image_generation_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(ImageGenerationToolOutput.builder()
                        .prompt("another prompt")
                        .mode("images")
                        .summary("should be ignored")
                        .build())
                .build());

        ToolOutputView outputView = ctx.toolOutputReader.readDirect("req-writer-direct-001", "tool-call-direct-001")
                .orElseThrow();
        ImageGenerationToolOutput structuredOutput = (ImageGenerationToolOutput) outputView.getStructuredOutput();

        Assert.assertNull(outputView.getSessionId());
        Assert.assertEquals(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE, outputView.getRequestSource());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), outputView.getStatus());
        Assert.assertEquals("upstream timeout", outputView.getErrorMsg());
        Assert.assertEquals("sunrise over lake", structuredOutput.getPrompt());
        Assert.assertTrue(ctx.toolOutputReader.readByInvocationId("image_generation_tool", null).isEmpty());
    }

    @Test
    public void shouldPersistPlanningStructuredOutputWithSnapshots() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .toolInvocationId(901L)
                .runId(902L)
                .requestId("req-writer-planning-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId("session-writer-planning-001")
                .toolCallId("tool-call-writer-planning-001")
                .toolName("planning")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(PlanningToolOutput.builder()
                        .command("mark_step")
                        .beforePlan(Plan.builder()
                                .title("普通 replan")
                                .steps(List.of("步骤一", "步骤二"))
                                .stepStatus(List.of("in_progress", "not_started"))
                                .notes(List.of("", ""))
                                .build())
                        .afterPlan(Plan.builder()
                                .title("普通 replan")
                                .steps(List.of("步骤一", "步骤二"))
                                .stepStatus(List.of("completed", "in_progress"))
                                .notes(List.of("已完成", ""))
                                .build())
                        .currentStep("步骤二")
                        .currentStepIndex(1)
                        .autoAdvanced(true)
                        .autoFinished(false)
                        .build())
                .build());

        PlanningToolOutput output = (PlanningToolOutput) ctx.toolOutputReader
                .readByInvocationId("planning", 901L)
                .orElseThrow();
        ToolOutputView direct = ctx.toolOutputReader
                .readDirect("req-writer-planning-001", "tool-call-writer-planning-001")
                .orElseThrow();

        Assert.assertEquals("mark_step", output.getCommand());
        Assert.assertEquals("步骤二", output.getCurrentStep());
        Assert.assertEquals(Integer.valueOf(1), output.getCurrentStepIndex());
        Assert.assertTrue(output.getAutoAdvanced());
        Assert.assertFalse(output.getAutoFinished());
        Assert.assertEquals(List.of("completed", "in_progress"), output.getAfterPlan().getStepStatus());
        Assert.assertEquals("planning", direct.getToolName());
    }

    @Test
    public void shouldPersistPlanningFinishStructuredOutput() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .toolInvocationId(903L)
                .runId(904L)
                .requestId("req-writer-planning-002")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId("session-writer-planning-002")
                .toolCallId("tool-call-writer-planning-002")
                .toolName("planning")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(PlanningToolOutput.builder()
                        .command("finish")
                        .beforePlan(Plan.builder()
                                .title("普通 replan")
                                .steps(List.of("步骤一", "步骤二"))
                                .stepStatus(List.of("completed", "in_progress"))
                                .notes(List.of("已完成", "待收口"))
                                .build())
                        .afterPlan(Plan.builder()
                                .title("普通 replan")
                                .steps(List.of("步骤一", "步骤二"))
                                .stepStatus(List.of("completed", "completed"))
                                .notes(List.of("已完成", "待收口"))
                                .build())
                        .currentStep("")
                        .currentStepIndex(null)
                        .autoAdvanced(false)
                        .autoFinished(true)
                        .build())
                .build());

        PlanningToolOutput output = (PlanningToolOutput) ctx.toolOutputReader
                .readByInvocationId("planning", 903L)
                .orElseThrow();

        Assert.assertEquals("finish", output.getCommand());
        Assert.assertTrue(output.getAutoFinished());
        Assert.assertFalse(output.getAutoAdvanced());
        Assert.assertEquals(List.of("completed", "completed"), output.getAfterPlan().getStepStatus());
        Assert.assertEquals("", output.getCurrentStep());
        Assert.assertNull(output.getCurrentStepIndex());
    }
}
