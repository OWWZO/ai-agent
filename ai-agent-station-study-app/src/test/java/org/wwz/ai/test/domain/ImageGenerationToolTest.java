package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.reactor.agent.printer.Printer;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.reactor.agent.tool.common.ImageGenerationTool;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * image_generation_tool 原生 output_json 回归。
 */
public class ImageGenerationToolTest {

    @Test
    public void shouldPersistNativeOutputJsonAndArtifactsForImageGeneration() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/tool/image_generation", new ImageGenerationHandler());
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            bindSpringContext(buildConfig(baseUrl));

            RecordingPrinter printer = new RecordingPrinter();
            ToolCollection toolCollection = new ToolCollection();
            AgentContext context = AgentContext.builder()
                    .requestId("req-image-001")
                    .sessionId("session-image-001")
                    .query("生成活动海报")
                    .isStream(true)
                    .printer(printer)
                    .toolCollection(toolCollection)
                    .productFiles(new ArrayList<>())
                    .taskProductFiles(new ArrayList<>())
                    .build();
            toolCollection.setAgentContext(context);

            ImageGenerationTool tool = new ImageGenerationTool();
            tool.setAgentContext(context);
            ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                    .sessionId(context.getSessionId())
                    .requestId(context.getRequestId())
                    .toolCallId("call-image-001")
                    .toolName("image_generation_tool")
                    .build();

            ToolResultPayload payload;
            context.bindCurrentToolArtifactSource(artifactSource);
            try {
                payload = (ToolResultPayload) tool.execute(JSONObject.parseObject("""
                        {"prompt":"生成活动海报"}
                        """));
            } finally {
                context.clearCurrentToolArtifactSource();
            }

            Assert.assertTrue(payload.getToolResult().contains("poster.png"));
            Assert.assertTrue(payload.getOutputJson().contains("\"schemaVersion\":1"));
            Assert.assertTrue(payload.getOutputJson().contains("\"prompt\":\"生成活动海报\""));
            Assert.assertTrue(payload.getOutputJson().contains("\"summary\""));
            Assert.assertTrue(payload.getOutputJson().contains("\"fileInfo\""));
            Assert.assertEquals(List.of("file"), printer.messageTypes());
            Assert.assertEquals(1, context.getTaskProductFiles().size());
            Assert.assertEquals("poster.png", context.getTaskProductFiles().get(0).getFileName());
        } finally {
            server.stop(0);
            ReflectionTestUtils.setField(SpringContextHolder.class, "context", null);
        }
    }

    private ReactorConfig buildConfig(String baseUrl) {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "imageGenerationUrl", baseUrl);
        ReflectionTestUtils.setField(reactorConfig, "imageGenerationToolDesc", "图片生成工具");
        return reactorConfig;
    }

    private void bindSpringContext(ReactorConfig reactorConfig) {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        Mockito.when(applicationContext.getBean(ReactorConfig.class)).thenReturn(reactorConfig);
        ReflectionTestUtils.setField(SpringContextHolder.class, "context", applicationContext);
    }

    private static class ImageGenerationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] body = (
                    "data: {\"data\":\"已生成图片文件：poster.png\",\"fileInfo\":[{\"fileName\":\"poster.png\",\"ossUrl\":\"https://file.example.com/poster.png\",\"domainUrl\":\"https://file.example.com/preview/poster.png\",\"fileSize\":256}],\"isFinal\":true}\n\n"
                            + "data: [DONE]\n\n"
            ).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
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
        public void close() {
        }

        @Override
        public void updateAgentType(org.wwz.ai.domain.agent.reactor.agent.enums.AgentType agentType) {
        }

        private List<String> messageTypes() {
            return messageTypes;
        }
    }
}
