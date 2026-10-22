package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ScriptRunnerToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputPersistCommand;

import java.util.List;

/**
 * 已退役 rich tool 输出表的持久化边界测试。
 */
public class RetiredToolOutputPersistenceTest {

    @Test
    public void shouldSkipRetiredToolOutputs() {
        ExecutionLedgerFixtureFactory.LedgerTestContext context =
                ExecutionLedgerFixtureFactory.newLedgerTestContext();

        write(context, "file_tool", FileToolOutput.builder().command("upload").build(), 1L);
        write(context, "planning", PlanningToolOutput.builder().command("create").build(), 2L);
        write(context, "report_tool", ReportToolOutput.builder().summary("summary").build(), 3L);
        write(context, "script_runner_tool", ScriptRunnerToolOutput.builder().summary("summary").build(), 4L);

        String[] retiredTools = {"file_tool", "planning", "report_tool", "script_runner_tool"};
        for (int index = 0; index < retiredTools.length; index++) {
            Assert.assertTrue(context.toolOutputReader
                    .readByInvocationId(retiredTools[index], index + 1L)
                    .isEmpty());
            Assert.assertTrue(context.toolOutputReader
                    .readDirect("retired-request", "retired-call-" + (index + 1))
                    .isEmpty());
        }
    }

    private void write(ExecutionLedgerFixtureFactory.LedgerTestContext context,
                       String toolName,
                       Object output,
                       long invocationId) {
        context.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .toolInvocationId(invocationId)
                .requestId("retired-request")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId("retired-session")
                .toolCallId("retired-call-" + invocationId)
                .toolName(toolName)
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput((org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput) output)
                .build());
    }
}
