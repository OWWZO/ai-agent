package org.wwz.ai.test.domain;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.WebFetchTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * web_fetch 工具端到端回归。
 */
public class WebFetchToolTest {

    @Test
    public void shouldCallRemoteServiceRegisterArtifactAndReturnSummaryObservation() throws Exception {
        RecordingWebFetchHandler handler = new RecordingWebFetchHandler();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/tool/web_fetch", handler);
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ReactorConfig reactorConfig = buildConfig(baseUrl);

            RecordingPrinter printer = new RecordingPrinter();
            ToolCollection toolCollection = new ToolCollection();
            AgentContext context = AgentContext.builder()
                    .requestId("req-webfetch-001")
                    .sessionId("session-webfetch-001")
                    .query("读取指定网页")
                    .printer(printer)
                    .toolCollection(toolCollection)
                    .productFiles(new ArrayList<>())
                    .taskProductFiles(new ArrayList<>())
                    .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig))
                    .build();
            toolCollection.setAgentContext(context);

            WebFetchTool tool = new WebFetchTool();
            tool.setAgentContext(context);
            ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                    .sessionId(context.getSessionId())
                    .requestId(context.getRequestId())
                    .toolCallId("call-webfetch-001")
                    .toolName("web_fetch")
                    .build();

            ToolResultPayload payload;
            context.bindCurrentToolArtifactSource(artifactSource);
            try {
                payload = (ToolResultPayload) tool.execute(Map.of(
                        "url", "https://example.com/article",
                        "timeout_seconds", 45
                ));
            } finally {
                context.clearCurrentToolArtifactSource();
            }

            Assert.assertFalse(payload.getFailed());
            Assert.assertTrue(payload.getLlmObservation().contains("网页抓取完成"));
            Assert.assertTrue(payload.getLlmObservation().contains("完整内容已保存为文件产物"));
            Assert.assertEquals(1, context.getTaskProductFiles().size());
            Assert.assertEquals("example-article.md", context.getTaskProductFiles().get(0).getFileName());
            Assert.assertEquals(List.of("file"), printer.messageTypes());
            Assert.assertNotNull(handler.getLastRequestBody());
            Assert.assertTrue(handler.getLastRequestBody().contains("\"url\":\"https://example.com/article\""));
            Assert.assertTrue(handler.getLastRequestBody().contains("\"timeoutSeconds\":45"));
        } finally {
            server.stop(0);
        }
    }

    private ReactorConfig buildConfig(String baseUrl) {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "webFetchUrl", baseUrl);
        ReflectionTestUtils.setField(reactorConfig, "webFetchToolDesc", "网页抓取工具");
        return reactorConfig;
    }

    private static class RecordingWebFetchHandler implements HttpHandler {
        private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

        private String getLastRequestBody() {
            return lastRequestBody.get();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"code":200,"requestId":"session-webfetch-001","data":{"title":"Example Article","finalUrl":"https://example.com/article","content":"这是一段网页摘要","contentFormat":"markdown","wordCount":42,"truncated":false,"contentSource":"trafilatura","metadata":{"description":"示例页面"}},"fileInfo":[{"fileName":"example-article.md","ossUrl":"https://file.example.com/download/example-article.md","domainUrl":"https://file.example.com/preview/example-article.md","fileSize":2048}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
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
