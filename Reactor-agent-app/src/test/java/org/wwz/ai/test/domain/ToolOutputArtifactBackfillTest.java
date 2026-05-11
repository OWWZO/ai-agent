package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputView;
import org.wwz.ai.infrastructure.dao.reactor.IArtifactLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputCodeInterpreterDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputDataAnalysisDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputDeepSearchDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputFileToolDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputImageGenerationDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputMultimodalAgentDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputPlanningDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputReportToolDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputScriptRunnerDao;
import org.wwz.ai.infrastructure.tooloutput.ToolOutputReaderImpl;
import org.wwz.ai.infrastructure.tooloutput.ToolOutputWriterImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * rich output 文件引用改由 artifact 账本承载后的回归测试。
 */
public class ToolOutputArtifactBackfillTest {

    @Test
    public void shouldHydrateFileRefsFromArtifactLedgerWhenReadingRichOutput() {
        IToolOutputDeepSearchDao deepSearchDao = Mockito.mock(IToolOutputDeepSearchDao.class);
        IToolOutputFileToolDao fileToolDao = Mockito.mock(IToolOutputFileToolDao.class);
        IToolOutputCodeInterpreterDao codeInterpreterDao = Mockito.mock(IToolOutputCodeInterpreterDao.class);
        IToolOutputReportToolDao reportToolDao = Mockito.mock(IToolOutputReportToolDao.class);
        IToolOutputDataAnalysisDao dataAnalysisDao = Mockito.mock(IToolOutputDataAnalysisDao.class);
        IToolOutputMultimodalAgentDao multimodalAgentDao = Mockito.mock(IToolOutputMultimodalAgentDao.class);
        IToolOutputImageGenerationDao imageGenerationDao = Mockito.mock(IToolOutputImageGenerationDao.class);
        IToolOutputScriptRunnerDao scriptRunnerDao = Mockito.mock(IToolOutputScriptRunnerDao.class);
        IToolOutputPlanningDao planningDao = Mockito.mock(IToolOutputPlanningDao.class);
        IArtifactLedgerDao artifactLedgerDao = Mockito.mock(IArtifactLedgerDao.class);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tool_invocation_id", 501L);
        row.put("run_id", 601L);
        row.put("request_id", "req-artifact-reader-001");
        row.put("request_source", ExecutionLedgerConstants.REQUEST_SOURCE_AGENT);
        row.put("session_id", "session-artifact-reader-001");
        row.put("tool_call_id", "tool-call-artifact-reader-001");
        row.put("status", ExecutionLedgerConstants.STATUS_SUCCESS);
        row.put("file_type", "markdown");
        row.put("summary", "summary");
        row.put("content", "# report");
        Mockito.when(reportToolDao.queryByToolInvocationId(501L)).thenReturn(row);
        Mockito.when(artifactLedgerDao.queryOutputArtifactsByToolInvocationId(501L)).thenReturn(List.of(
                artifact("tool-call-artifact-reader-001", "report.md", "https://download/report.md", "https://preview/report.md")
        ));

        ToolOutputReaderImpl reader = new ToolOutputReaderImpl(
                deepSearchDao,
                fileToolDao,
                codeInterpreterDao,
                reportToolDao,
                dataAnalysisDao,
                multimodalAgentDao,
                imageGenerationDao,
                scriptRunnerDao,
                planningDao,
                artifactLedgerDao
        );

        ReportToolOutput output = (ReportToolOutput) reader.readByInvocationId("report_tool", 501L).orElseThrow();

        Assert.assertEquals(1, output.getFileRefs().size());
        Assert.assertEquals("report.md", output.getFileRefs().get(0).getFileName());
        Assert.assertEquals("https://download/report.md", output.getFileRefs().get(0).getDownloadUrl());
        Assert.assertEquals("https://preview/report.md", output.getFileRefs().get(0).getPreviewUrl());
    }

    @Test
    public void shouldFallbackToRunAndToolCallWhenDirectLookupHasNoInvocationId() {
        IToolOutputDeepSearchDao deepSearchDao = Mockito.mock(IToolOutputDeepSearchDao.class);
        IToolOutputFileToolDao fileToolDao = Mockito.mock(IToolOutputFileToolDao.class);
        IToolOutputCodeInterpreterDao codeInterpreterDao = Mockito.mock(IToolOutputCodeInterpreterDao.class);
        IToolOutputReportToolDao reportToolDao = Mockito.mock(IToolOutputReportToolDao.class);
        IToolOutputDataAnalysisDao dataAnalysisDao = Mockito.mock(IToolOutputDataAnalysisDao.class);
        IToolOutputMultimodalAgentDao multimodalAgentDao = Mockito.mock(IToolOutputMultimodalAgentDao.class);
        IToolOutputImageGenerationDao imageGenerationDao = Mockito.mock(IToolOutputImageGenerationDao.class);
        IToolOutputScriptRunnerDao scriptRunnerDao = Mockito.mock(IToolOutputScriptRunnerDao.class);
        IToolOutputPlanningDao planningDao = Mockito.mock(IToolOutputPlanningDao.class);
        IArtifactLedgerDao artifactLedgerDao = Mockito.mock(IArtifactLedgerDao.class);
        Mockito.when(deepSearchDao.queryByRequestToolCall(Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        Mockito.when(codeInterpreterDao.queryByRequestToolCall(Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        Mockito.when(reportToolDao.queryByRequestToolCall(Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        Mockito.when(dataAnalysisDao.queryByRequestToolCall(Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        Mockito.when(multimodalAgentDao.queryByRequestToolCall(Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        Mockito.when(imageGenerationDao.queryByRequestToolCall(Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        Mockito.when(scriptRunnerDao.queryByRequestToolCall(Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        Mockito.when(planningDao.queryByRequestToolCall(Mockito.anyString(), Mockito.anyString())).thenReturn(null);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("request_id", "req-artifact-reader-002");
        row.put("request_source", ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE);
        row.put("session_id", "session-artifact-reader-002");
        row.put("tool_call_id", "tool-call-artifact-reader-002");
        row.put("status", ExecutionLedgerConstants.STATUS_SUCCESS);
        row.put("command", "get");
        row.put("primary_file_name", "analysis.xlsx");
        Mockito.when(fileToolDao.queryByRequestToolCall("req-artifact-reader-002", "tool-call-artifact-reader-002"))
                .thenReturn(row);
        Mockito.when(artifactLedgerDao.queryOutputArtifactsByRequestIdAndToolCallId("req-artifact-reader-002", "tool-call-artifact-reader-002"))
                .thenReturn(List.of(
                        artifact("tool-call-artifact-reader-002", "analysis.xlsx", "https://download/analysis.xlsx", "https://preview/analysis.xlsx")
                ));

        ToolOutputReaderImpl reader = new ToolOutputReaderImpl(
                deepSearchDao,
                fileToolDao,
                codeInterpreterDao,
                reportToolDao,
                dataAnalysisDao,
                multimodalAgentDao,
                imageGenerationDao,
                scriptRunnerDao,
                planningDao,
                artifactLedgerDao
        );

        ToolOutputView outputView = reader.readDirect("req-artifact-reader-002", "tool-call-artifact-reader-002").orElseThrow();
        FileToolOutput output = (FileToolOutput) outputView.getStructuredOutput();

        Assert.assertEquals(1, output.getFileRefs().size());
        Assert.assertEquals("analysis.xlsx", output.getFileRefs().get(0).getFileName());
        Assert.assertEquals(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE, outputView.getRequestSource());
    }

    @Test
    public void shouldNotPersistFileRefsJsonForFileBasedRichTools() {
        IToolOutputDeepSearchDao deepSearchDao = Mockito.mock(IToolOutputDeepSearchDao.class);
        IToolOutputFileToolDao fileToolDao = Mockito.mock(IToolOutputFileToolDao.class);
        IToolOutputCodeInterpreterDao codeInterpreterDao = Mockito.mock(IToolOutputCodeInterpreterDao.class);
        IToolOutputReportToolDao reportToolDao = Mockito.mock(IToolOutputReportToolDao.class);
        IToolOutputDataAnalysisDao dataAnalysisDao = Mockito.mock(IToolOutputDataAnalysisDao.class);
        IToolOutputMultimodalAgentDao multimodalAgentDao = Mockito.mock(IToolOutputMultimodalAgentDao.class);
        IToolOutputImageGenerationDao imageGenerationDao = Mockito.mock(IToolOutputImageGenerationDao.class);
        IToolOutputScriptRunnerDao scriptRunnerDao = Mockito.mock(IToolOutputScriptRunnerDao.class);
        IToolOutputPlanningDao planningDao = Mockito.mock(IToolOutputPlanningDao.class);
        Mockito.when(fileToolDao.insert(Mockito.anyMap())).thenReturn(1);
        Mockito.when(reportToolDao.insert(Mockito.anyMap())).thenReturn(1);

        ToolOutputWriterImpl writer = new ToolOutputWriterImpl(
                deepSearchDao,
                fileToolDao,
                codeInterpreterDao,
                reportToolDao,
                dataAnalysisDao,
                multimodalAgentDao,
                imageGenerationDao,
                scriptRunnerDao,
                planningDao
        );

        writer.write(ToolOutputPersistCommand.builder()
                .toolInvocationId(701L)
                .runId(801L)
                .requestId("req-artifact-writer-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId("session-artifact-writer-001")
                .toolCallId("tool-call-artifact-writer-001")
                .toolName("file_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(FileToolOutput.builder()
                        .command("get")
                        .primaryFileName("report.md")
                        .fileRefs(List.of(ToolFileRef.builder().fileName("report.md").build()))
                        .build())
                .build());
        writer.write(ToolOutputPersistCommand.builder()
                .toolInvocationId(702L)
                .runId(802L)
                .requestId("req-artifact-writer-002")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId("session-artifact-writer-002")
                .toolCallId("tool-call-artifact-writer-002")
                .toolName("report_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(ReportToolOutput.builder()
                        .fileType("markdown")
                        .summary("summary")
                        .content("# report")
                        .fileRefs(List.of(ToolFileRef.builder().fileName("report.md").build()))
                        .build())
                .build());

        ArgumentCaptor<Map<String, Object>> fileToolCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> reportToolCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(fileToolDao).insert(fileToolCaptor.capture());
        Mockito.verify(reportToolDao).insert(reportToolCaptor.capture());

        Assert.assertFalse(fileToolCaptor.getValue().containsKey("fileRefsJson"));
        Assert.assertFalse(reportToolCaptor.getValue().containsKey("fileRefsJson"));
    }

    private ArtifactRecord artifact(String toolCallId,
                                    String fileName,
                                    String downloadUrl,
                                    String previewUrl) {
        return ArtifactRecord.builder()
                .runId(1L)
                .requestId("workspace-req-" + toolCallId)
                .toolInvocationId(1L)
                .toolCallId(toolCallId)
                .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                .sourceName("report_tool")
                .fileName(fileName)
                .storageKey(downloadUrl)
                .downloadUrl(downloadUrl)
                .previewUrl(previewUrl)
                .mimeType("application/octet-stream")
                .build();
    }
}
