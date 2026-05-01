package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolOutputView;

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
                .sessionId("session-writer-001")
                .toolCallId("tool-call-writer-001")
                .toolName("file_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(FileToolOutput.builder()
                        .command("upload")
                        .primaryFileName("report-a.md")
                        .fileRefs(List.of(ToolFileRef.builder().fileName("report-a.md").build()))
                        .build())
                .build();
        ToolOutputPersistCommand duplicate = ToolOutputPersistCommand.builder()
                .toolInvocationId(101L)
                .runId(201L)
                .requestId("req-writer-001")
                .sessionId("session-writer-001")
                .toolCallId("tool-call-writer-001")
                .toolName("file_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(FileToolOutput.builder()
                        .command("upload")
                        .primaryFileName("report-b.md")
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
        Assert.assertEquals("session-writer-001", outputView.getSessionId());
        Assert.assertEquals("report-a.md", structuredOutput.getPrimaryFileName());
        Assert.assertEquals(1, structuredOutput.getFileRefs().size());
        Assert.assertEquals("report-a.md", structuredOutput.getFileRefs().get(0).getFileName());
    }

    @Test
    public void shouldSupportDirectToolCallWithoutLedgerFieldsAndKeepFirstWriteWins() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .requestId("req-writer-direct-001")
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
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), outputView.getStatus());
        Assert.assertEquals("upstream timeout", outputView.getErrorMsg());
        Assert.assertEquals("sunrise over lake", structuredOutput.getPrompt());
        Assert.assertTrue(ctx.toolOutputReader.readByInvocationId("image_generation_tool", null).isEmpty());
    }
}
