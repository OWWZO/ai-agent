package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.reactor.gateway.IReactorImageGenerationGateway;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecuteCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayRequest;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayResponse;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.impl.ImageGenerationExecutionKernelImpl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ImageGenerationExecutionKernelTest {

    @Test
    public void test_executeNormalizesGatewayRequestAndResponse() {
        IReactorImageGenerationGateway gateway = Mockito.mock(IReactorImageGenerationGateway.class);
        AtomicReference<ImageGenerationGatewayRequest> captured = new AtomicReference<>();
        Mockito.when(gateway.generate(Mockito.any(ImageGenerationGatewayRequest.class)))
                .thenAnswer(invocation -> {
                    ImageGenerationGatewayRequest request = invocation.getArgument(0);
                    captured.set(request);
                    return ImageGenerationGatewayResponse.builder()
                            .requestId(request.getRequestId())
                            .mode("images")
                            .data("生成完成")
                            .usedFallback(true)
                            .rawResponse(Map.of("traceId", "raw-1"))
                            .fileInfo(List.of(
                                    ImageGenerationGatewayFile.builder()
                                            .fileName("poster.png")
                                            .domainUrl("https://file.example.com/poster.png")
                                            .fileSize(512L)
                                            .mimeType("image/png")
                                            .build()
                            ))
                            .build();
                });

        ImageGenerationExecutionKernelImpl kernel = new ImageGenerationExecutionKernelImpl(gateway);
        ImageGenerationExecutionResult result = kernel.execute(ImageGenerationExecuteCommand.builder()
                .requestId("req-kernel-001")
                .prompt(" 生成海报 ")
                .mode("images")
                .size("")
                .n(2)
                .model("gpt-image-1")
                .build());

        Assert.assertEquals("req-kernel-001", captured.get().getRequestId());
        Assert.assertEquals("生成海报", captured.get().getPrompt());
        Assert.assertEquals("1024x1024", captured.get().getSize());
        Assert.assertEquals("gpt-image-1", captured.get().getModel());
        Assert.assertEquals(Integer.valueOf(900), captured.get().getTimeoutSeconds());
        Assert.assertEquals(Boolean.FALSE, captured.get().getStream());
        Assert.assertEquals(Integer.valueOf(2), result.getBatchCount());
        Assert.assertTrue(result.getUsedFallback());
        Assert.assertEquals("https://file.example.com/poster.png", result.getFiles().get(0).getPreviewUrl());
    }
}
