package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.common.ImageGenerationTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecuteCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageFile;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * image_generation_tool typed output 回归。
 */
public class ImageGenerationToolTest {

    @Test
    public void shouldUseSharedKernelAndReturnRichStructuredOutput() {
        IImageGenerationExecutionKernel kernel = Mockito.mock(IImageGenerationExecutionKernel.class);
        ArgumentCaptor<ImageGenerationExecuteCommand> commandCaptor = ArgumentCaptor.forClass(ImageGenerationExecuteCommand.class);
        Mockito.when(kernel.execute(commandCaptor.capture())).thenReturn(ImageGenerationExecutionResult.builder()
                .requestId("session-image-001")
                .prompt("生成活动海报")
                .mode("images")
                .summary("已生成图片文件：poster.png")
                .size("1536x1024")
                .batchCount(2)
                .sourceImageCount(0)
                .maskImageCount(0)
                .usedFallback(true)
                .files(List.of(WorkspaceImageFile.builder()
                        .fileName("poster.png")
                        .ossUrl("https://file.example.com/poster.png")
                        .domainUrl("https://file.example.com/preview/poster.png")
                        .downloadUrl("https://file.example.com/poster.png")
                        .previewUrl("https://file.example.com/preview/poster.png")
                        .fileSize(256L)
                        .mimeType("image/png")
                        .build()))
                .build());

        ReactorConfig reactorConfig = buildConfig();
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
        Assert.assertTrue(payload.getLlmObservation().contains("https://file.example.com/preview/poster.png"));
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

        ImageGenerationExecuteCommand command = commandCaptor.getValue();
        Assert.assertEquals("session-image-001", command.getRequestId());
        Assert.assertEquals("gpt-image-1", command.getModel());
        Assert.assertEquals(Integer.valueOf(900), command.getTimeoutSeconds());
        Assert.assertEquals(Integer.valueOf(2), command.getN());
    }

    @Test
    public void shouldReuseContextImagesWhenModeIsMissing() {
        IImageGenerationExecutionKernel kernel = Mockito.mock(IImageGenerationExecutionKernel.class);
        ArgumentCaptor<ImageGenerationExecuteCommand> commandCaptor = ArgumentCaptor.forClass(ImageGenerationExecuteCommand.class);
        Mockito.when(kernel.execute(commandCaptor.capture())).thenReturn(ImageGenerationExecutionResult.builder()
                .requestId("session-image-002")
                .prompt("基于上传图片改成赛博朋克风")
                .mode("edits")
                .summary("图片编辑完成")
                .size("1024x1024")
                .batchCount(1)
                .sourceImageCount(1)
                .maskImageCount(0)
                .usedFallback(false)
                .files(List.of(WorkspaceImageFile.builder()
                        .fileName("edited.png")
                        .domainUrl("https://file.example.com/preview/edited.png")
                        .downloadUrl("https://file.example.com/download/edited.png")
                        .fileSize(128L)
                        .mimeType("image/png")
                        .build()))
                .build());

        ReactorConfig reactorConfig = buildConfig();
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

        Assert.assertEquals(
                List.of("https://file.example.com/preview/source-image.png"),
                commandCaptor.getValue().getFileNames()
        );
    }

    @Test
    public void shouldFallbackToFileNameWhenContextImageUrlMissing() {
        IImageGenerationExecutionKernel kernel = Mockito.mock(IImageGenerationExecutionKernel.class);
        ArgumentCaptor<ImageGenerationExecuteCommand> commandCaptor = ArgumentCaptor.forClass(ImageGenerationExecuteCommand.class);
        Mockito.when(kernel.execute(commandCaptor.capture())).thenReturn(ImageGenerationExecutionResult.builder()
                .requestId("session-image-003")
                .prompt("沿用上一张图继续修改")
                .mode("edits")
                .summary("图片编辑完成")
                .size("1024x1024")
                .batchCount(1)
                .sourceImageCount(1)
                .maskImageCount(0)
                .usedFallback(false)
                .files(List.of(WorkspaceImageFile.builder()
                        .fileName("edited.png")
                        .domainUrl("https://file.example.com/preview/edited.png")
                        .downloadUrl("https://file.example.com/download/edited.png")
                        .fileSize(128L)
                        .mimeType("image/png")
                        .build()))
                .build());

        ReactorConfig reactorConfig = buildConfig();
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

        Assert.assertEquals(List.of("source-image.png"), commandCaptor.getValue().getFileNames());
    }

    private ReactorConfig buildConfig() {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "imageGenerationToolDesc", "图片生成工具");
        ReflectionTestUtils.setField(reactorConfig, "imageGenerationBaseUrl", "https://www.micuapi.ai");
        ReflectionTestUtils.setField(reactorConfig, "imageGenerationApiKey", "test-key");
        ReflectionTestUtils.setField(reactorConfig, "imageGenerationModel", "gpt-image-2");
        return reactorConfig;
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
