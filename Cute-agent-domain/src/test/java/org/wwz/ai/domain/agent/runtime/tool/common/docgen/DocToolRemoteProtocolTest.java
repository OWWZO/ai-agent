package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.cute.config.CuteConfig;
import org.wwz.ai.domain.agent.runtime.CuteRuntimeDependencies;
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
        CuteConfig config = new CuteConfig();
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
    public void genAcceptsFileInfoBearingResponseWithoutSuccessKey() {
        // 生成侧特有语义：响应带 fileInfo 但没有 success 键时，guard 不得早退，
        // 随后的 success 检查因缺键而放行——文件已产出就该登记，不能被 message 文案带崩。
        http.response = "{\"message\":\"generated with notes\","
                + "\"fileInfo\":[{\"fileName\":\"out.pdf\",\"ossUrl\":\"https://oss/x.pdf\"}]}";
        GenTool tool = new GenTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-partial").toolName("doc_gen").build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(ToolArgs.map("{}"));

        Assert.assertFalse("带 fileInfo 且无 success 键的响应不得判失败", payload.getFailed());
        Assert.assertFalse(context.getArtifactBindingsByToolCallId("call-partial").isEmpty());
    }

    @Test
    public void genStillFailsWhenMessagePresentWithoutFileInfo() {
        // 与上例互补：没有 fileInfo 的 message 是真正的失败，早退必须仍然生效。
        http.response = "{\"message\":\"document_generate failed: boom\"}";
        GenTool tool = new GenTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-gen-fail").toolName("doc_gen").build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(ToolArgs.map("{}"));

        Assert.assertTrue(payload.getFailed());
        Assert.assertTrue(payload.getToolResult().contains("boom"));
    }

    @Test
    public void readHasNoFileInfoEarlyExitSoMessageAloneStillFails() {
        // 读侧没有生成侧那道 guard：单有 message 时靠 success 检查判失败。
        // 这条测试钉住两端的差异——guard 是生成侧特有的，不是公共基类的行为。
        http.response = "{\"success\":false,\"message\":\"parse failed\"}";
        ReadTool tool = new ReadTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-read-partial").toolName("doc_read").build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(ToolArgs.map("{}"));

        Assert.assertTrue(payload.getFailed());
        Assert.assertTrue(payload.getToolResult().contains("parse failed"));
    }

    @Test
    public void readKeepsStructuredDataAsObjectWhenUnderLimit() {
        // 结构化 data 未超限时保留原对象：读侧下游按 Map 消费，恒串化会让它们看到 String
        http.response = "{\"success\":true,\"data\":{\"rows\":[{\"a\":1}],\"columns\":[\"a\"]}}";
        ReadTool tool = new ReadTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-read-obj").toolName("doc_read").build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(ToolArgs.map("{}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
        Assert.assertTrue("未超限的结构化 data 必须是 Map",
                data.get("data") instanceof Map);
        Assert.assertEquals(List.of("a"), ((Map<String, Object>) data.get("data")).get("columns"));
    }

    @Test
    public void readTruncatesStructuredDataWhenOverLimit() {
        // 超限时才降级为截断后的 JSON 字符串
        StringBuilder rows = new StringBuilder("[");
        for (int i = 0; i < 400; i++) {
            if (i > 0) {
                rows.append(',');
            }
            rows.append("{\"col\":\"").append("z".repeat(100)).append("\"}");
        }
        rows.append(']');
        http.response = "{\"success\":true,\"data\":{\"rows\":" + rows + "}}";
        ReadTool tool = new ReadTool();
        tool.bind(context);
        context.bindCurrentToolArtifactSource(
                ToolArtifactSource.builder().toolCallId("call-read-big").toolName("doc_read").build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(ToolArgs.map("{}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
        Assert.assertTrue("超限的结构化 data 必须降级为字符串",
                data.get("data") instanceof String);
        Assert.assertTrue(((String) data.get("data")).endsWith("...[truncated]"));
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

    private AgentContext newContext(RemoteHttpPort port, Printer p, CuteConfig config, String workspaceRoot) {
        return AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .userId("user-1")
                .query("q")
                .printer(p)
                .workspaceRoot(workspaceRoot)
                .runtimeDependencies(CuteRuntimeDependencies.builder()
                        .cuteConfig(config)
                        .remoteHttpPort(port)
                        .build())
                .build();
    }

    private CuteConfig configWithUrl() {
        CuteConfig config = new CuteConfig();
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
