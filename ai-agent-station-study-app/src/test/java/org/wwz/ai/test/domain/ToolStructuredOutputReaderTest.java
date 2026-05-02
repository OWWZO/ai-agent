package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DeepSearchStage;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DeepSearchToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolOutputView;

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
                .structuredOutput(DeepSearchToolOutput.builder()
                        .query("AI 芯片供应链")
                        .answerSummary("timeout")
                        .stages(List.of(DeepSearchStage.builder().stage("extend").queries(List.of("AI 芯片供应链")).build()))
                        .build())
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
}
