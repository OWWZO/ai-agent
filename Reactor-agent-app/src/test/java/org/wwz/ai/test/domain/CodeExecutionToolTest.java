package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.CodeExecutionTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.ArrayList;
import java.util.Map;

public class CodeExecutionToolTest {

    @Test
    public void shouldExposeUploadedArtifactUrlToLlm() {
        RemoteHttpPort httpPort = request -> """
                {"status":"ok","stdout":"chart created","stderr":"","result":null,
                 "fileInfo":[{"fileName":"chart.png","domainUrl":"https://file.example.com/preview/chart.png","ossUrl":"https://file.example.com/download/chart.png","fileSize":42}]}
                """;
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "codeInterpreterUrl", "http://reactor-tool");
        AgentContext context = AgentContext.builder()
                .requestId("req-code-001")
                .sessionId("session-code-001")
                .productFiles(new ArrayList<>())
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config, httpPort))
                .build();
        ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                .sessionId(context.getSessionId())
                .requestId(context.getRequestId())
                .toolCallId("call-code-001")
                .toolName("code_execution")
                .build();
        CodeExecutionTool tool = new CodeExecutionTool();
        tool.setAgentContext(context);

        ToolResultPayload payload;
        context.bindCurrentToolArtifactSource(artifactSource);
        try {
            payload = (ToolResultPayload) tool.execute(Map.of("source", "result = 1"));
        } finally {
            context.clearCurrentToolArtifactSource();
        }

        Assert.assertFalse(payload.getFailed());
        Assert.assertTrue(JSON.toJSONString(payload.getLlmData())
                .contains("https://file.example.com/preview/chart.png"));
        Assert.assertEquals("chart.png", context.getVisibleArtifactFiles().get(0).getFileName());
    }

    @Test
    public void shouldPreserveAbsolutePathsInOutputAndErrors() {
        String windowsPath = "C:\\Users\\WWZ\\project\\output.csv";
        String unixPath = "/home/agent/workspace/error.log";
        String resultPath = "/tmp/generated/chart.png";
        String errorPath = "D:\\runtime\\python\\script.py";
        RemoteHttpPort httpPort = request -> JSON.toJSONString(Map.of(
                "status", "error",
                "stdout", windowsPath,
                "stderr", unixPath,
                "result", resultPath,
                "error", errorPath,
                "fileInfo", java.util.List.of()));
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "codeInterpreterUrl", "http://reactor-tool");
        AgentContext context = AgentContext.builder()
                .requestId("req-code-paths")
                .sessionId("session-code-paths")
                .productFiles(new ArrayList<>())
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config, httpPort))
                .build();
        ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                .sessionId(context.getSessionId())
                .requestId(context.getRequestId())
                .toolCallId("call-code-paths")
                .toolName("code_execution")
                .build();
        CodeExecutionTool tool = new CodeExecutionTool();
        tool.setAgentContext(context);

        ToolResultPayload payload;
        context.bindCurrentToolArtifactSource(artifactSource);
        try {
            payload = (ToolResultPayload) tool.execute(Map.of("source", "result = 1"));
        } finally {
            context.clearCurrentToolArtifactSource();
        }
        Map<?, ?> data = (Map<?, ?>) payload.getLlmData();

        Assert.assertTrue(payload.getFailed());
        Assert.assertEquals(windowsPath, data.get("stdout"));
        Assert.assertEquals(unixPath, data.get("stderr"));
        Assert.assertEquals(resultPath, data.get("result"));
        Assert.assertEquals(errorPath, data.get("error"));
        Assert.assertEquals(errorPath, payload.getErrorMsg());
    }

    @Test
    public void shouldSendAgentContextWorkspaceRootToSandbox() {
        java.util.concurrent.atomic.AtomicReference<String> capturedBody = new java.util.concurrent.atomic.AtomicReference<>();
        RemoteHttpPort httpPort = request -> {
            capturedBody.set(request.getBody());
            return """
                    {"status":"ok","stdout":"","stderr":"","result":null,"fileInfo":[]}
                    """;
        };
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "codeInterpreterUrl", "http://reactor-tool");
        String workspaceRoot = java.nio.file.Path.of(
                        System.getProperty("java.io.tmpdir"), "reactor-ws-align", "session-code-001")
                .toAbsolutePath()
                .normalize()
                .toString();
        AgentContext context = AgentContext.builder()
                .requestId("req-code-001")
                .sessionId("session-code-001")
                .workspaceRoot(workspaceRoot)
                .productFiles(new ArrayList<>())
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config, httpPort))
                .build();
        ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                .sessionId(context.getSessionId())
                .requestId(context.getRequestId())
                .toolCallId("call-code-001")
                .toolName("code_execution")
                .build();
        CodeExecutionTool tool = new CodeExecutionTool();
        tool.setAgentContext(context);

        context.bindCurrentToolArtifactSource(artifactSource);
        try {
            tool.execute(Map.of("source", "result = 1"));
        } finally {
            context.clearCurrentToolArtifactSource();
        }

        com.alibaba.fastjson.JSONObject request = JSON.parseObject(capturedBody.get());
        Assert.assertEquals(workspaceRoot, request.getString("workspaceRoot"));
        Assert.assertEquals("session-code-001", request.getString("sessionId"));
        Assert.assertEquals("user:anonymous", request.getString("ownerKey"));
    }
}
