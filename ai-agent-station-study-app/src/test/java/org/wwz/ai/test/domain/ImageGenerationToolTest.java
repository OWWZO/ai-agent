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
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.dto.ImageGenerationRequest;
import org.wwz.ai.domain.agent.reactor.agent.tool.common.ImageGenerationTool;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ImageGenerationToolOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * image_generation_tool typed output 回归。
 */
public class ImageGenerationToolTest {

    @Test
    public void shouldPersistNativeOutputJsonAndArtifactsForImageGeneration() throws Exception {
        RecordingImageGenerationHandler handler = new RecordingImageGenerationHandler();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/tool/image_generation", handler);
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

            ImageGenerationToolOutput structuredOutput = (ImageGenerationToolOutput) payload.getStructuredOutput();
            Assert.assertTrue(payload.getToolResult().contains("poster.png"));
            Assert.assertNotNull(structuredOutput);
            Assert.assertFalse(payload.getFailed());
            Assert.assertEquals("生成活动海报", structuredOutput.getPrompt());
            Assert.assertFalse(structuredOutput.getFileRefs().isEmpty());
            Assert.assertEquals(List.of("file"), printer.messageTypes());
            Assert.assertEquals(1, context.getTaskProductFiles().size());
            Assert.assertEquals("poster.png", context.getTaskProductFiles().get(0).getFileName());
            Assert.assertEquals("images", handler.getLastRequest().getMode());
        } finally {
            server.stop(0);
            ReflectionTestUtils.setField(SpringContextHolder.class, "context", null);
        }
    }

    @Test
    public void shouldReuseContextImagesWhenModeIsMissing() throws Exception {
        RecordingImageGenerationHandler handler = new RecordingImageGenerationHandler();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/tool/image_generation", handler);
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            bindSpringContext(buildConfig(baseUrl));

            RecordingPrinter printer = new RecordingPrinter();
            ToolCollection toolCollection = new ToolCollection();
            AgentContext context = AgentContext.builder()
                    .requestId("req-image-002")
                    .sessionId("session-image-002")
                    .query("基于上传图片改成赛博朋克风")
                    .isStream(true)
                    .printer(printer)
                    .toolCollection(toolCollection)
                    .productFiles(new ArrayList<>(List.of(
                            File.builder()
                                    .fileName("source-image.png")
                                    .domainUrl("https://file.example.com/preview/source-image.png")
                                    .ossUrl("https://file.example.com/download/source-image.png")
                                    .isInternalFile(Boolean.FALSE)
                                    .build(),
                            File.builder()
                                    .fileName("notes.txt")
                                    .isInternalFile(Boolean.FALSE)
                                    .build()
                    )))
                    .taskProductFiles(new ArrayList<>())
                    .build();
            toolCollection.setAgentContext(context);

            ImageGenerationTool tool = new ImageGenerationTool();
            tool.setAgentContext(context);
            ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                    .sessionId(context.getSessionId())
                    .requestId(context.getRequestId())
                    .toolCallId("call-image-002")
                    .toolName("image_generation_tool")
                    .build();

            context.bindCurrentToolArtifactSource(artifactSource);
            try {
                ToolResultPayload payload = (ToolResultPayload) tool.execute(JSONObject.parseObject("""
                        {"prompt":"基于上传图片改成赛博朋克风"}
                        """));
                Assert.assertFalse(payload.getFailed());
            } finally {
                context.clearCurrentToolArtifactSource();
            }

            Assert.assertNotNull(handler.getLastRequest());
            Assert.assertNull(handler.getLastRequest().getMode());
            Assert.assertEquals(List.of("source-image.png"), handler.getLastRequest().getFileNames());
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

    private static class RecordingImageGenerationHandler implements HttpHandler {
        private final AtomicReference<ImageGenerationRequest> lastRequest = new AtomicReference<>();

        private ImageGenerationRequest getLastRequest() {
            return lastRequest.get();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            lastRequest.set(JSONObject.parseObject(new String(requestBody, StandardCharsets.UTF_8), ImageGenerationRequest.class));
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
