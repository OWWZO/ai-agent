package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolArgs;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.AbstractDocReadTool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文档生成 / 读取两个远端适配基类的公共协议。
 * <p>
 * 两个基类 81% 逐字相同（远端调用、fileInfo 登记、file 事件、LLM data 裁剪），
 * 这组测试锁住它们必须保持一致的行为，防止收敛公共基类时改掉任一端的语义。
 */
public class DocToolRemoteProtocolTest {

    private RecordingHttpPort http;
    private RecordingPrinter printer;
    private AgentContext context;

    @Before
    public void setUp() {
        http = new RecordingHttpPort();
        printer = new RecordingPrinter();
        context = newContext(http, printer);
    }

    @Test
    public void genInjectsRequestIdAndPostsToEndpoint() {
        GenTool tool = new GenTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-gen").toolName("doc_gen").build());

        tool.execute(ToolArgs.map("{\"title\":\"季度报告\"}"));

        Assert.assertEquals("https://autobots.example.com/v1/tool/gen", http.request.getUrl());
        Assert.assertEquals("POST", http.request.getMethod());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = JSON.parseObject(http.request.getBody(), Map.class);
        Assert.assertEquals("session-1", body.get("requestId"));
        Assert.assertEquals("季度报告", body.get("title"));
    }

    @Test
    public void readInjectsRequestIdAndWorkspaceRoot() {
        ReadTool tool = new ReadTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-read").toolName("doc_read").build());

        tool.execute(ToolArgs.map("{\"path\":\"/tmp/a.pdf\"}"));

        Assert.assertEquals("https://autobots.example.com/v1/tool/read", http.request.getUrl());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = JSON.parseObject(http.request.getBody(), Map.class);
        Assert.assertEquals("session-1", body.get("requestId"));
        // workspace_root 是领域侧注入的运行时元数据，模型不传也要带上
        Assert.assertEquals("/workspace/session-1", body.get("workspace_root"));
    }

    @Test
    public void genRegistersFileInfoAndEmitsFileEvent() {
        http.response = "{\"success\":true,\"fileInfo\":[{\"fileName\":\"out.pdf\",\"fileSize\":2048,"
                + "\"ossUrl\":\"https://oss/x.pdf\",\"domainUrl\":\"https://cdn/x.pdf\"}]}";
        GenTool tool = new GenTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-gen").toolName("doc_gen").build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(ToolArgs.map("{}"));

        Assert.assertFalse(payload.getFailed());
        Assert.assertEquals("file", printer.messageType);
        Assert.assertEquals("生成文档", printer.resultMap.get("command"));
        Assert.assertEquals("call-gen", printer.resultMap.get("toolCallId"));
        List<String> registered = context.getArtifactBindingsByToolCallId("call-gen").stream()
                .map(binding -> binding.getFile().getFileName())
                .toList();
        Assert.assertEquals("fileInfo 必须登记为 artifact", List.of("out.pdf"), registered);
    }

    @Test
    public void readRegistersFileInfoAndEmitsFileEvent() {
        http.response = "{\"success\":true,\"data\":\"正文内容\",\"fileInfo\":[{\"fileName\":\"a.pdf\","
                + "\"file_size\":512,\"oss_url\":\"https://oss/a.pdf\"}]}";
        ReadTool tool = new ReadTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-read").toolName("doc_read").build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(ToolArgs.map("{}"));

        Assert.assertFalse(payload.getFailed());
        Assert.assertEquals("file", printer.messageType);
        Assert.assertEquals("文档处理", printer.resultMap.get("command"));
        Assert.assertFalse(context.getArtifactBindingsByToolCallId("call-read").isEmpty());
    }

    @Test
    public void genTruncatesLongDataFields() {
        String longText = "x".repeat(5000);
        http.response = "{\"success\":true,\"message\":\"" + longText + "\"}";
        GenTool tool = new GenTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-gen").toolName("doc_gen").build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(ToolArgs.map("{}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
        String message = (String) data.get("message");
        Assert.assertTrue("超长字段必须截断", message.length() < 5000);
        Assert.assertTrue(message.endsWith("...[truncated]"));
    }

    @Test
    public void readTruncatesLongDataField() {
        String longText = "y".repeat(20_000);
        http.response = "{\"success\":true,\"data\":\"" + longText + "\"}";
        ReadTool tool = new ReadTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-read").toolName("doc_read").build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(ToolArgs.map("{}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
        String text = (String) data.get("data");
        Assert.assertTrue("超长 data 必须截断", text.length() < 20_000);
        Assert.assertTrue(text.endsWith("...[truncated]"));
    }

    @Test
    public void bothFailWhenCodeInterpreterUrlMissing() {
        ReactorConfig config = new ReactorConfig();
        AgentContext noUrl = newContext(http, printer, config, "");
        noUrl.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("c").toolName("t").build());

        ToolResultPayload gen = (ToolResultPayload) genToolOn(noUrl).execute(ToolArgs.map("{}"));
        ToolResultPayload read = (ToolResultPayload) readToolOn(noUrl).execute(ToolArgs.map("{}"));

        Assert.assertTrue(gen.getFailed());
        Assert.assertTrue(read.getFailed());
        Assert.assertTrue(gen.getToolResult().contains("未配置"));
        Assert.assertTrue(read.getToolResult().contains("未配置"));
        Assert.assertNull("配置缺失时不得发起远端调用", http.request);
    }

    @Test
    public void bothFailOnRemoteErrorResponse() {
        http.response = "{\"success\":false,\"message\":\"boom\"}";

        ToolResultPayload gen = (ToolResultPayload) genTool().execute(ToolArgs.map("{}"));
        ToolResultPayload read = (ToolResultPayload) readTool().execute(ToolArgs.map("{}"));

        Assert.assertTrue(gen.getFailed());
        Assert.assertTrue(read.getFailed());
        Assert.assertTrue(gen.getToolResult().contains("boom"));
        Assert.assertTrue(read.getToolResult().contains("boom"));
    }

    @Test
    public void bothFailOnEmptyResponse() {
        http.response = null;

        ToolResultPayload gen = (ToolResultPayload) genTool().execute(ToolArgs.map("{}"));
        ToolResultPayload read = (ToolResultPayload) readTool().execute(ToolArgs.map("{}"));

        Assert.assertTrue(gen.getFailed());
        Assert.assertTrue(read.getFailed());
    }

    @Test
    public void bothApplyDistinctTimeouts() {
        // gen 240s / read 180s：抽取公共基类后超时必须仍由各自决定，不能被统一掉
        GenTool gen = new GenTool();
        gen.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("c1").toolName("t1").build());
        gen.execute(ToolArgs.map("{}"));
        Assert.assertEquals(Long.valueOf(240L), http.request.getReadTimeoutSeconds());
        Assert.assertEquals(Long.valueOf(270L), http.request.getCallTimeoutSeconds());

        http.request = null;
        ReadTool read = new ReadTool();
        read.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("c2").toolName("t2").build());
        read.execute(ToolArgs.map("{}"));
        Assert.assertEquals(Long.valueOf(180L), http.request.getReadTimeoutSeconds());
        Assert.assertEquals(Long.valueOf(210L), http.request.getCallTimeoutSeconds());
    }

    // ---------- fixtures ----------

    private AbstractDocGenTool genTool() {
        GenTool tool = new GenTool();
        tool.bind(context);
        return tool;
    }

    private AbstractDocReadTool readTool() {
        ReadTool tool = new ReadTool();
        tool.bind(context);
        return tool;
    }

    private AbstractDocGenTool genToolOn(AgentContext ctx) {
        GenTool tool = new GenTool();
        tool.bind(ctx);
        return tool;
    }

    private AbstractDocReadTool readToolOn(AgentContext ctx) {
        ReadTool tool = new ReadTool();
        tool.bind(ctx);
        return tool;
    }

    private AgentContext newContext(RemoteHttpPort port, Printer p) {
        return newContext(port, p, configWithUrl(), "/workspace/session-1");
    }

    private AgentContext newContext(RemoteHttpPort port, Printer p, ReactorConfig config, String workspaceRoot) {
        return AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .userId("user-1")
                .query("q")
                .printer(p)
                .workspaceRoot(workspaceRoot)
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .reactorConfig(config)
                        .remoteHttpPort(port)
                        .build())
                .build();
    }

    private ReactorConfig configWithUrl() {
        ReactorConfig config = new ReactorConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(
                config, "codeInterpreterUrl", "https://autobots.example.com");
        return config;
    }

    private static final class GenTool extends AbstractDocGenTool {
        /** agentContext 现位于 doc 包，protected 字段跨包不可见，这里开一个测试专用入口。 */
        private void bind(AgentContext ctx) {
            this.agentContext = ctx;
        }

        @Override
        public String getName() {
            return "doc_gen";
        }

        @Override
        protected String endpointPath() {
            return "/v1/tool/gen";
        }

        @Override
        protected String defaultDescription() {
            return "生成文档";
        }

        @Override
        protected Map<String, Object> defaultParams() {
            return objectSchema(new java.util.LinkedHashMap<>(), List.of());
        }
    }

    private static final class ReadTool extends AbstractDocReadTool {
        /** AbstractDocReadTool 在其它包，protected 字段不可见，这里开一个测试专用入口。 */
        private void bind(AgentContext ctx) {
            this.agentContext = ctx;
        }

        @Override
        public String getName() {
            return "doc_read";
        }

        @Override
        protected String endpointPath() {
            return "/v1/tool/read";
        }

        @Override
        protected String defaultDescription() {
            return "读取文档";
        }

        @Override
        protected Map<String, Object> defaultParams() {
            return objectSchema(new java.util.LinkedHashMap<>(), List.of());
        }
    }

    private static final class RecordingHttpPort implements RemoteHttpPort {
        private RemoteHttpRequest request;
        private String response = "{\"success\":true}";

        @Override
        public String execute(RemoteHttpRequest request) throws IOException {
            this.request = request;
            return response;
        }
    }

    private static final class RecordingPrinter implements Printer {
        private String messageType;
        private Map<String, Object> resultMap;
        private final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public void send(String messageId, String messageType, Object message,
                         String digitalEmployee, Boolean isFinal) {
            // 工具侧走的是这个 5 参重载，payload 就在 message 上
            record(messageType, message);
        }

        @Override
        public void send(String messageId, String messageType, Object message,
                         Map<String, Object> extraResultMap, String digitalEmployee,
                         Boolean isFinal) {
            record(messageType, message != null ? message : extraResultMap);
        }

        @SuppressWarnings("unchecked")
        private void record(String type, Object message) {
            this.messageType = type;
            this.resultMap = message instanceof Map<?, ?> ? (Map<String, Object>) message : null;
            events.add(resultMap);
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(org.wwz.ai.domain.agent.runtime.enums.AgentType agentType) {
        }
    }
}
