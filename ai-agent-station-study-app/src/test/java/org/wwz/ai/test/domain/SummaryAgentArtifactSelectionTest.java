package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.SummaryAgent;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SummaryAgent 文件来源解析测试。
 */
public class SummaryAgentArtifactSelectionTest {

    @Test
    public void shouldSelectExactArtifactKeyAndExcludeInternalArtifactsFromPrompt() {
        AgentContext context = newAgentContext(buildReactorConfig());
        context.registerGeneratedArtifact(newSource("call-deep-001", "deep_search"),
                createFile("summary.md", "https://file.example.com/deep/summary.md", "搜索摘要", false));
        context.registerGeneratedArtifact(newSource("call-report-001", "report_tool"),
                createFile("summary.md", "https://file.example.com/report/summary.md", "最终报告", false));
        context.registerGeneratedArtifact(newSource("call-deep-001", "deep_search"),
                createFile("scratch.txt", "https://file.example.com/deep/scratch.txt", "内部草稿", true));

        SummaryAgent summaryAgent = new SummaryAgent(context);
        LLM llm = Mockito.mock(LLM.class);
        ReflectionTestUtils.setField(summaryAgent, "llm", llm);

        Mockito.when(llm.ask(Mockito.eq(context), Mockito.anyList(), Mockito.anyList(), Mockito.eq(true), Mockito.any()))
                .thenAnswer(invocation -> {
                    List<Message> systemMessages = invocation.getArgument(2);
                    String prompt = systemMessages.get(0).getContent();
                    Assert.assertTrue(prompt.contains("artifactKey:call-deep-001::summary.md"));
                    Assert.assertTrue(prompt.contains("artifactKey:call-report-001::summary.md"));
                    Assert.assertFalse(prompt.contains("artifactKey:call-deep-001::scratch.txt"));
                    return CompletableFuture.completedFuture("""
                            总结完成
                            $$$
                            call-report-001::summary.md
                            """);
                });

        TaskSummaryResult result = summaryAgent.summaryTaskResult(
                List.of(Message.assistantMessage("工具执行完成", null)),
                "请输出最终总结"
        );

        Assert.assertEquals("总结完成", result.getTaskSummary());
        Assert.assertEquals(1, result.getFiles().size());
        Assert.assertEquals("https://file.example.com/report/summary.md", result.getFiles().get(0).getOssUrl());
    }

    @Test
    public void shouldNotFallbackToFilenameOnlyMatching() {
        AgentContext context = newAgentContext(buildReactorConfig());
        context.registerGeneratedArtifact(newSource("call-deep-001", "deep_search"),
                createFile("summary.md", "https://file.example.com/deep/summary.md", "搜索摘要", false));
        context.registerGeneratedArtifact(newSource("call-report-001", "report_tool"),
                createFile("summary-final.md", "https://file.example.com/report/summary-final.md", "最终报告", false));

        SummaryAgent summaryAgent = new SummaryAgent(context);
        LLM llm = Mockito.mock(LLM.class);
        ReflectionTestUtils.setField(summaryAgent, "llm", llm);

        Mockito.when(llm.ask(Mockito.eq(context), Mockito.anyList(), Mockito.anyList(), Mockito.eq(true), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture("""
                        总结完成
                        $$$
                        summary.md
                        """));

        TaskSummaryResult result = summaryAgent.summaryTaskResult(
                List.of(Message.assistantMessage("工具执行完成", null)),
                "请输出最终总结"
        );

        Assert.assertEquals("总结完成", result.getTaskSummary());
        Assert.assertTrue(result.getFiles() == null || result.getFiles().isEmpty());
    }

    private ReactorConfig buildReactorConfig() {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "summarySystemPrompt",
                "任务历史:\n{{taskHistory}}\n文件上下文:\n{{fileNameDesc}}\n用户问题:{{query}}");
        ReflectionTestUtils.setField(reactorConfig, "summaryModelName", "summary-test-model");
        ReflectionTestUtils.setField(reactorConfig, "summaryTemperature", 0.2D);
        ReflectionTestUtils.setField(reactorConfig, "messageSizeLimit", 2000);
        ReflectionTestUtils.setField(reactorConfig, "llmSettingsMap", Map.of(
                "summary-test-model",
                LLMSettings.builder()
                        .model("summary-test-model")
                        .baseUrl("https://llm.example.com")
                        .interfaceUrl("/v1/chat/completions")
                        .apiKey("test-key")
                        .functionCallType("function_call")
                        .maxTokens(4096)
                        .maxInputTokens(8192)
                        .temperature(0.0)
                        .build()
        ));
        return reactorConfig;
    }

    private AgentContext newAgentContext(ReactorConfig reactorConfig) {
        return AgentContext.builder()
                .requestId("req-summary-001")
                .sessionId("session-summary-001")
                .query("总结报告")
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig))
                .build();
    }

    private ToolArtifactSource newSource(String toolCallId, String toolName) {
        return ToolArtifactSource.builder()
                .requestId("req-summary-001")
                .sessionId("session-summary-001")
                .toolCallId(toolCallId)
                .toolName(toolName)
                .build();
    }

    private File createFile(String fileName, String url, String description, boolean internalFile) {
        return File.builder()
                .fileName(fileName)
                .ossUrl(url)
                .domainUrl(url)
                .description(description)
                .isInternalFile(internalFile)
                .build();
    }
}
