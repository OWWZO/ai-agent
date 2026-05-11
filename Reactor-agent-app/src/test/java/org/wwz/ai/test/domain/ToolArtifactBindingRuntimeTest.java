package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactBinding;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具产物来源绑定运行时测试。
 */
public class ToolArtifactBindingRuntimeTest {

    @Test
    public void shouldMaintainCompatibilityViewsAndHideInternalFiles() {
        AgentContext context = newAgentContext();
        ToolArtifactSource source = newSource("call-file-001", "file_tool");

        context.registerGeneratedArtifact(source, createFile("scratch.txt",
                "https://file.example.com/internal/scratch.txt", "内部草稿", true));
        context.registerGeneratedArtifact(source, createFile("report.md",
                "https://file.example.com/public/report.md", "最终报告", false));

        Assert.assertEquals(2, context.getProductFiles().size());
        Assert.assertEquals(1, context.getTaskProductFiles().size());
        Assert.assertEquals(1, context.getVisibleArtifactFiles().size());
        Assert.assertEquals("report.md", context.getVisibleArtifactFiles().get(0).getFileName());
    }

    @Test
    public void shouldRegisterSourceForSyncToolExecutedByBaseAgent() {
        AgentContext context = newAgentContext();
        ToolCollection toolCollection = context.getToolCollection();
        toolCollection.addTool(new SyncArtifactTool(context));

        TestAgent agent = new TestAgent();
        agent.setContext(context);
        agent.availableTools = toolCollection;

        String result = agent.executeTool(newToolCall(
                "call-sync-001",
                "sync_artifact_tool",
                "{\"fileName\":\"deliverable.md\",\"url\":\"https://file.example.com/sync/deliverable.md\"}"
        ));

        Assert.assertTrue(result.startsWith("同步工具执行完成"));
        Assert.assertTrue(result.contains("artifactKey:call-sync-001::deliverable.md"));
        List<ToolArtifactBinding> bindings = context.getArtifactBindingsByToolCallId("call-sync-001");
        Assert.assertEquals(1, bindings.size());
        ToolArtifactBinding binding = bindings.get(0);
        Assert.assertEquals("req-artifact-001", binding.getSource().getRequestId());
        Assert.assertEquals("session-artifact-001", binding.getSource().getSessionId());
        Assert.assertEquals("call-sync-001", binding.getSource().getToolCallId());
        Assert.assertEquals("sync_artifact_tool", binding.getSource().getToolName());
        Assert.assertEquals("deliverable.md", binding.getFile().getFileName());
        Assert.assertEquals(1, context.getProductFiles().size());
        Assert.assertEquals(1, context.getTaskProductFiles().size());
    }

    @Test
    public void shouldKeepToolSourceAcrossAsyncCallbacksAndParallelExecution() {
        AgentContext context = newAgentContext();
        ToolCollection toolCollection = context.getToolCollection();
        toolCollection.addTool(new AsyncArtifactTool(context));

        TestAgent agent = new TestAgent();
        agent.setContext(context);
        agent.availableTools = toolCollection;

        Map<String, String> results = agent.executeTools(List.of(
                newToolCall(
                        "call-async-001",
                        "async_artifact_tool",
                        "{\"fileName\":\"summary.md\",\"url\":\"https://file.example.com/a/summary.md\"}"
                ),
                newToolCall(
                        "call-async-002",
                        "async_artifact_tool",
                        "{\"fileName\":\"summary.md\",\"url\":\"https://file.example.com/b/summary.md\"}"
                )
        ));

        Assert.assertTrue(results.get("call-async-001").startsWith("异步工具执行完成:call-async-001"));
        Assert.assertTrue(results.get("call-async-001").contains("artifactKey:call-async-001::summary.md"));
        Assert.assertTrue(results.get("call-async-002").startsWith("异步工具执行完成:call-async-002"));
        Assert.assertTrue(results.get("call-async-002").contains("artifactKey:call-async-002::summary.md"));
        Assert.assertEquals(1, context.getArtifactBindingsByToolCallId("call-async-001").size());
        Assert.assertEquals(1, context.getArtifactBindingsByToolCallId("call-async-002").size());
        Assert.assertEquals("https://file.example.com/a/summary.md",
                context.getArtifactBindingsByToolCallId("call-async-001").get(0).getFile().getOssUrl());
        Assert.assertEquals("https://file.example.com/b/summary.md",
                context.getArtifactBindingsByToolCallId("call-async-002").get(0).getFile().getOssUrl());
        Assert.assertEquals(2, context.getVisibleArtifactFiles().size());
    }

    @Test
    public void shouldAppendOnlyCurrentToolArtifactsToToolResult() {
        AgentContext context = newAgentContext();
        context.registerGeneratedArtifact(newSource("call-attach-001", "deep_search"),
                createFile("summary.md", "https://file.example.com/deep/summary.md", "搜索摘要", false));
        context.registerGeneratedArtifact(newSource("call-attach-002", "report_tool"),
                createFile("summary.md", "https://file.example.com/report/summary.md", "报告成品", false));

        TestAgent agent = new TestAgent();
        agent.setContext(context);

        String enriched = agent.exposeAttachToolArtifactSummary("工具执行完成", "call-attach-002");

        Assert.assertTrue(enriched.contains("artifactKey:call-attach-002::summary.md"));
        Assert.assertFalse(enriched.contains("artifactKey:call-attach-001::summary.md"));
        Assert.assertEquals("纯文本结果", agent.exposeAttachToolArtifactSummary("纯文本结果", "missing-call"));
    }

    private AgentContext newAgentContext() {
        ToolCollection toolCollection = new ToolCollection();
        AgentContext context = AgentContext.builder()
                .requestId("req-artifact-001")
                .sessionId("session-artifact-001")
                .query("验证工具产物来源绑定")
                .toolCollection(toolCollection)
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .build();
        toolCollection.setAgentContext(context);
        return context;
    }

    private ToolArtifactSource newSource(String toolCallId, String toolName) {
        return ToolArtifactSource.builder()
                .requestId("req-artifact-001")
                .sessionId("session-artifact-001")
                .toolCallId(toolCallId)
                .toolName(toolName)
                .build();
    }

    private ToolCall newToolCall(String id, String toolName, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .function(ToolCall.Function.builder()
                        .name(toolName)
                        .arguments(arguments)
                        .build())
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

    /**
     * 暴露受保护方法，避免为测试引入额外业务逻辑。
     */
    private static class TestAgent extends BaseAgent {
        @Override
        public String step() {
            return "";
        }

        private String exposeAttachToolArtifactSummary(String result, String toolCallId) {
            return attachToolArtifactSummary(result, toolCallId);
        }
    }

    /**
     * 模拟同步产文件工具。
     */
    private static class SyncArtifactTool implements BaseTool {
        private final AgentContext agentContext;

        private SyncArtifactTool(AgentContext agentContext) {
            this.agentContext = agentContext;
        }

        @Override
        public String getName() {
            return "sync_artifact_tool";
        }

        @Override
        public String getDescription() {
            return "测试用同步产文件工具";
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object execute(Object input) {
            Map<String, Object> params = (Map<String, Object>) input;
            ToolArtifactSource source = agentContext.requireCurrentToolArtifactSource(getName());
            File file = File.builder()
                    .fileName(String.valueOf(params.get("fileName")))
                    .ossUrl(String.valueOf(params.get("url")))
                    .domainUrl(String.valueOf(params.get("url")))
                    .description("同步工具交付文件")
                    .isInternalFile(false)
                    .build();
            agentContext.registerGeneratedArtifact(source, file);
            return "同步工具执行完成";
        }
    }

    /**
     * 模拟异步回调产文件工具，验证跨线程显式传递来源快照。
     */
    private static class AsyncArtifactTool implements BaseTool {
        private final AgentContext agentContext;

        private AsyncArtifactTool(AgentContext agentContext) {
            this.agentContext = agentContext;
        }

        @Override
        public String getName() {
            return "async_artifact_tool";
        }

        @Override
        public String getDescription() {
            return "测试用异步产文件工具";
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object execute(Object input) {
            Map<String, Object> params = (Map<String, Object>) input;
            ToolArtifactSource source = agentContext.requireCurrentToolArtifactSource(getName());
            String fileName = String.valueOf(params.get("fileName"));
            String url = String.valueOf(params.get("url"));

            CompletableFuture<String> future = new CompletableFuture<>();
            Thread worker = new Thread(() -> {
                try {
                    File file = File.builder()
                            .fileName(fileName)
                            .ossUrl(url)
                            .domainUrl(url)
                            .description("异步工具交付文件:" + source.getToolCallId())
                            .isInternalFile(false)
                            .build();
                    agentContext.registerGeneratedArtifact(source, file);
                    future.complete("异步工具执行完成:" + source.getToolCallId());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            worker.start();
            return future.join();
        }
    }
}
