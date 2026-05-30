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
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.ImageGenerationRequest;
import org.wwz.ai.domain.agent.runtime.tool.common.ImageGenerationTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.impl.ImageGenerationExecutionKernelImpl;
import org.wwz.ai.infrastructure.gateway.ReactorImageGenerationGateway;
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
 * image_generation_tool typed output 回归。
 */
public class ImageGenerationToolTest {

    @Test
    public void shouldUseSharedKernelAndReturnRichStructuredOutput() throws Exception {
        RecordingImageGenerationHandler handler = new RecordingImageGenerationHandler();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/tool/image_generation", handler);
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ReactorConfig reactorConfig = buildConfig(baseUrl);
            IImageGenerationExecutionKernel kernel = buildKernel(reactorConfig);

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
                    .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig, kernel))
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
                        {"prompt":"生成活动海报","n":2,"size":"1536x1024","model":"gpt-image-1"}
                        """));
            } finally {
                context.clearCurrentToolArtifactSource();
            }

            ImageGenerationToolOutput structuredOutput = (ImageGenerationToolOutput) payload.getStructuredOutput();
            Assert.assertTrue(payload.getToolResult().contains("poster.png"));
            Assert.assertNotNull(structuredOutput);
            Assert.assertFalse(payload.getFailed());
            Assert.assertEquals("生成活动海报", structuredOutput.getPrompt());
            Assert.assertEquals("1536x1024", structuredOutput.getSize());
            Assert.assertEquals(Integer.valueOf(2), structuredOutput.getBatchCount());
            Assert.assertTrue(structuredOutput.getUsedFallback());
            Assert.assertFalse(structuredOutput.getFileRefs().isEmpty());
            Assert.assertEquals(List.of("file"), printer.messageTypes());
            Assert.assertTrue(printer.lastMessage() instanceof Map<?, ?>);
            Map<?, ?> fileMessage = (Map<?, ?>) printer.lastMessage();
            Assert.assertEquals("call-image-001", fileMessage.get("toolCallId"));
            Assert.assertEquals("image_generation_tool", fileMessage.get("toolName"));
            Assert.assertEquals(1, context.getTaskProductFiles().size());
            Assert.assertEquals("poster.png", context.getTaskProductFiles().get(0).getFileName());
            Assert.assertEquals(Boolean.FALSE, handler.getLastRequest().getStream());
            Assert.assertEquals("gpt-image-1", handler.getLastRequest().getModel());
            Assert.assertEquals("session-image-001", handler.getLastRequest().getRequestId());
            Assert.assertEquals(Integer.valueOf(900), handler.getLastRequest().getTimeoutSeconds());
        } finally {
            server.stop(0);
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
            ReactorConfig reactorConfig = buildConfig(baseUrl);
            IImageGenerationExecutionKernel kernel = buildKernel(reactorConfig);

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
                    .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig, kernel))
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
            Assert.assertEquals(Boolean.FALSE, handler.getLastRequest().getStream());
            Assert.assertEquals(
                    List.of("https://file.example.com/preview/source-image.png"),
                    handler.getLastRequest().getFileNames()
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldFallbackToFileNameWhenContextImageUrlMissing() throws Exception {
        RecordingImageGenerationHandler handler = new RecordingImageGenerationHandler();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/tool/image_generation", handler);
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ReactorConfig reactorConfig = buildConfig(baseUrl);
            IImageGenerationExecutionKernel kernel = buildKernel(reactorConfig);

            RecordingPrinter printer = new RecordingPrinter();
            ToolCollection toolCollection = new ToolCollection();
            AgentContext context = AgentContext.builder()
                    .requestId("req-image-003")
                    .sessionId("session-image-003")
                    .query("沿用上一张图继续修改")
                    .isStream(true)
                    .printer(printer)
                    .toolCollection(toolCollection)
                    .productFiles(new ArrayList<>(List.of(
                            File.builder()
                                    .fileName("source-image.png")
                                    .isInternalFile(Boolean.FALSE)
                                    .build()
                    )))
                    .taskProductFiles(new ArrayList<>())
                    .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig, kernel))
                    .build();
            toolCollection.setAgentContext(context);

            ImageGenerationTool tool = new ImageGenerationTool();
            tool.setAgentContext(context);
            ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                    .sessionId(context.getSessionId())
                    .requestId(context.getRequestId())
                    .toolCallId("call-image-003")
                    .toolName("image_generation_tool")
                    .build();

            context.bindCurrentToolArtifactSource(artifactSource);
            try {
                ToolResultPayload payload = (ToolResultPayload) tool.execute(JSONObject.parseObject("""
                        {"prompt":"沿用上一张图继续修改"}
                        """));
                Assert.assertFalse(payload.getFailed());
            } finally {
                context.clearCurrentToolArtifactSource();
            }

            Assert.assertNotNull(handler.getLastRequest());
            Assert.assertEquals(Boolean.FALSE, handler.getLastRequest().getStream());
            Assert.assertEquals(List.of("source-image.png"), handler.getLastRequest().getFileNames());
        } finally {
            server.stop(0);
        }
    }

    private ReactorConfig buildConfig(String baseUrl) {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "imageGenerationUrl", baseUrl);
        ReflectionTestUtils.setField(reactorConfig, "imageGenerationToolDesc", "图片生成工具");
        return reactorConfig;
    }

    private IImageGenerationExecutionKernel buildKernel(ReactorConfig reactorConfig) {
        ReactorImageGenerationGateway gateway = new ReactorImageGenerationGateway();
        ReflectionTestUtils.setField(gateway, "reactorConfig", reactorConfig);
        return new ImageGenerationExecutionKernelImpl(gateway);
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
            byte[] body = """
                    {"data":"已生成图片文件：poster.png","requestId":"req-image-response","mode":"images","usedFallback":true,"fileInfo":[{"fileName":"poster.png","ossUrl":"https://file.example.com/poster.png","domainUrl":"https://file.example.com/preview/poster.png","fileSize":256,"mimeType":"image/png"}]}
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
        private final AtomicReference<Object> lastMessage = new AtomicReference<>();

        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
            messageTypes.add(messageType);
            lastMessage.set(message);
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
            messageTypes.add(messageType);
            lastMessage.set(message);
        }

        @Override
        public void send(String messageType, Object message) {
            messageTypes.add(messageType);
            lastMessage.set(message);
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
            messageTypes.add(messageType);
            lastMessage.set(message);
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
            messageTypes.add(messageType);
            lastMessage.set(message);
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, Boolean isFinal) {
            messageTypes.add(messageType);
            lastMessage.set(message);
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
            messageTypes.add(messageType);
            lastMessage.set(message);
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

        private Object lastMessage() {
            return lastMessage.get();
        }
    }
}
