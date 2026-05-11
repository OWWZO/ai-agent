package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.common.MultiModalAgent;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.MultimodalAgentToolOutput;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多模态知识检索工具测试。
 */
public class MultiModalAgentToolTest {

    @Test
    public void shouldConsumeMragSseAndUploadMarkdownArtifact() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/tool/mragQuery", new MlagQueryHandler());
        server.createContext("/v1/file_tool/upload_file", new UploadFileHandler());
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ReactorConfig reactorConfig = buildConfig(baseUrl);

            RecordingPrinter printer = new RecordingPrinter();
            ToolCollection toolCollection = new ToolCollection();
            toolCollection.setDigitalEmployees(JSONObject.parseObject("{\"multimodalagent_tool\":\"知识顾问\"}"));

            AgentContext agentContext = AgentContext.builder()
                    .requestId("req-mrag-001")
                    .sessionId("session-mrag-001")
                    .query("总结多模态检索核心能力")
                    .isStream(true)
                    .printer(printer)
                    .toolCollection(toolCollection)
                    .productFiles(new ArrayList<>())
                    .taskProductFiles(new ArrayList<>())
                    .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig))
                    .build();
            toolCollection.setAgentContext(agentContext);

            MultiModalAgent multiModalAgent = new MultiModalAgent();
            multiModalAgent.setAgentContext(agentContext);
            ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                    .sessionId(agentContext.getSessionId())
                    .requestId(agentContext.getRequestId())
                    .toolCallId("call-mrag-001")
                    .toolName("multimodalagent_tool")
                    .build();

            ToolResultPayload payload;
            agentContext.bindCurrentToolArtifactSource(artifactSource);
            try {
                payload = (ToolResultPayload) multiModalAgent.execute(JSONObject.parseObject("""
                        {"question":"总结多模态检索核心能力"}
                        """));
            } finally {
                agentContext.clearCurrentToolArtifactSource();
            }

            String result = payload.getToolResult();
            MultimodalAgentToolOutput structuredOutput = (MultimodalAgentToolOutput) payload.getStructuredOutput();
            Assert.assertTrue(result.contains("多模态检索会先召回图文片段。"));
            Assert.assertTrue(result.contains("![图片](https://img.example.com/mrag.png)"));
            Assert.assertNotNull(structuredOutput);
            Assert.assertFalse(payload.getFailed());
            Assert.assertTrue(structuredOutput.getMarkdownContent().contains("![图片]"));
            Assert.assertFalse(structuredOutput.getFileRefs().isEmpty());
            Assert.assertEquals("markdown", printer.messageTypes().get(printer.messageTypes().size() - 1));
            Assert.assertEquals(
                    List.of("knowledge"),
                    printer.messageTypes()
                            .subList(0, printer.messageTypes().size() - 1)
                            .stream()
                            .distinct()
                            .collect(Collectors.toList()));
            Assert.assertEquals(1, agentContext.getTaskProductFiles().size());
            Assert.assertTrue(agentContext.getTaskProductFiles().get(0).getFileName().endsWith(".md"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldReturnExplicitFailureWhenQuestionBlank() {
        ReactorConfig reactorConfig = buildConfig("http://127.0.0.1:1601");

        MultiModalAgent multiModalAgent = new MultiModalAgent();
        multiModalAgent.setAgentContext(AgentContext.builder()
                .requestId("req-mrag-blank")
                .sessionId("session-mrag-blank")
                .query("空问题")
                .isStream(true)
                .printer(new RecordingPrinter())
                .toolCollection(new ToolCollection())
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig))
                .build());

        ToolResultPayload payload = (ToolResultPayload) multiModalAgent.execute(JSONObject.parseObject("""
                {"question":"   "}
                """));

        Assert.assertEquals("multimodalagent_tool 执行失败：question 不能为空。", payload.getToolResult());
        Assert.assertTrue(payload.getFailed());
    }

    private ReactorConfig buildConfig(String baseUrl) {
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMessageInterval("{\"knowledge\":\"1,1\"}");
        ReflectionTestUtils.setField(reactorConfig, "multiModalAgentUrl", baseUrl);
        ReflectionTestUtils.setField(reactorConfig, "codeInterpreterUrl", baseUrl);
        ReflectionTestUtils.setField(reactorConfig, "multiModalAgentDesc", "多模态知识检索工具");
        return reactorConfig;
    }

    private static class MlagQueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] body = (
                    "data: {\"id\":\"chatcmpl-mrag-001\",\"choices\":[{\"delta\":{\"content\":\"多模态检索会先召回图文片段。\"},\"finishReason\":null,\"index\":0}]}\n\n"
                            + "data: {\"id\":\"chatcmpl-mrag-001\",\"choices\":[{\"delta\":{\"content\":\"最终结果支持 Markdown 图片引用。\\n\\n![图片](https://img.example.com/mrag.png)\"},\"finishReason\":\"stop\",\"index\":0}]}\n\n"
                            + "data: [DONE]\n\n"
            ).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static class UploadFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] response = """
                    {"requestId":"session-mrag-001","ossUrl":"https://file.example.com/summary.md","domainUrl":"https://file.example.com/preview/summary.md","fileName":"summary.md","fileSize":128}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        }
    }

    private static class RecordingPrinter implements Printer {
        private final List<String> messageTypes = new ArrayList<>();

        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
            messageTypes.add(messageType);
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
            messageTypes.add(messageType);
        }

        @Override
        public void send(String messageType, Object message) {
            messageTypes.add(messageType);
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
            messageTypes.add(messageType);
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
            messageTypes.add(messageType);
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, Boolean isFinal) {
            messageTypes.add(messageType);
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
            messageTypes.add(messageType);
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(org.wwz.ai.domain.agent.runtime.enums.AgentType agentType) {
        }

        private List<String> messageTypes() {
            return messageTypes;
        }
    }
}
