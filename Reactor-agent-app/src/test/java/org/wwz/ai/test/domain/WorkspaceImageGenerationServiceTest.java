package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryPage;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationResult;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputView;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationBatchPersistenceService;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.impl.ImageGenerationBatchPersistenceServiceImpl;
import org.wwz.ai.domain.agent.ledger.tooloutput.ToolOutputReader;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputImageGenerationDao;
import org.wwz.ai.infrastructure.reactor.service.impl.WorkspaceImageGenerationServiceImpl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class WorkspaceImageGenerationServiceTest {

    @Test
    public void test_generateUsesSharedKernelAndPersistsWorkspaceBatch() {
        WorkspaceImageGenerationServiceImpl service = new WorkspaceImageGenerationServiceImpl();
        AtomicReference<String> persistedRequestId = new AtomicReference<>();
        AtomicReference<ImageGenerationExecutionResult> persistedResult = new AtomicReference<>();
        AtomicReference<org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecuteCommand> capturedCommand = new AtomicReference<>();

        IImageGenerationExecutionKernel kernel = Mockito.mock(IImageGenerationExecutionKernel.class);
        Mockito.when(kernel.execute(Mockito.any())).thenAnswer(invocation -> {
            org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecuteCommand command = invocation.getArgument(0);
            capturedCommand.set(command);
            return ImageGenerationExecutionResult.builder()
                    .requestId(command.getRequestId())
                    .prompt(command.getPrompt())
                    .mode(command.getMode())
                    .summary("生成完成")
                    .size(command.getSize())
                    .batchCount(command.getN())
                    .sourceImageCount(command.getFileNames().size())
                    .maskImageCount(1)
                    .usedFallback(true)
                    .rawResponse(Map.of("requestId", command.getRequestId()))
                    .files(List.of(
                            WorkspaceImageFile.builder()
                                    .fileName("result-1.png")
                                    .previewUrl("https://file.example.com/result-1.png")
                                    .downloadUrl("https://file.example.com/download/result-1.png")
                                    .mimeType("image/png")
                                    .fileSize(128L)
                                    .build(),
                            WorkspaceImageFile.builder()
                                    .fileName("result-2.png")
                                    .previewUrl("https://file.example.com/result-2.png")
                                    .downloadUrl("https://file.example.com/download/result-2.png")
                                    .mimeType("image/png")
                                    .fileSize(256L)
                                    .build()
                    ))
                    .build();
        });
        IImageGenerationBatchPersistenceService persistenceService = Mockito.mock(IImageGenerationBatchPersistenceService.class);
        Mockito.doAnswer(invocation -> {
            persistedRequestId.set(invocation.getArgument(0));
            persistedResult.set(invocation.getArgument(1));
            return null;
        }).when(persistenceService).persistWorkspaceBatch(Mockito.anyString(), Mockito.any());

        ReflectionTestUtils.setField(service, "imageGenerationExecutionKernel", kernel);
        ReflectionTestUtils.setField(service, "imageGenerationBatchPersistenceService", persistenceService);

        WorkspaceImageGenerationResult result = service.generate(
                WorkspaceImageGenerationCommand.builder()
                        .requestId("req-100")
                        .prompt(" 生成一张海报 ")
                        .mode("edits")
                        .fileNames(List.of("source-1", "source-2"))
                        .maskFileNames(List.of("mask-1", ""))
                        .fileName(" poster ")
                        .model("gpt-image-2")
                        .size("")
                        .n(3)
                        .build()
        );

        Assert.assertNotNull(capturedCommand.get());
        Assert.assertEquals("req-100", capturedCommand.get().getRequestId());
        Assert.assertEquals("edits", capturedCommand.get().getMode());
        Assert.assertEquals("生成一张海报", capturedCommand.get().getPrompt());
        Assert.assertEquals("poster", capturedCommand.get().getFileName());
        Assert.assertEquals("1024x1024", capturedCommand.get().getSize());
        Assert.assertEquals(Integer.valueOf(3), capturedCommand.get().getN());
        Assert.assertEquals(Integer.valueOf(900), capturedCommand.get().getTimeoutSeconds());
        Assert.assertEquals("gpt-image-2", capturedCommand.get().getModel());

        Assert.assertEquals("req-100", persistedRequestId.get());
        Assert.assertNotNull(persistedResult.get());
        Assert.assertEquals(2, persistedResult.get().getFiles().size());

        Assert.assertEquals("req-100", result.getRequestId());
        Assert.assertEquals(2, result.getFileInfo().size());
        Assert.assertTrue(result.getUsedFallback());
        Assert.assertEquals("https://file.example.com/result-1.png", result.getFileInfo().get(0).getPreviewUrl());
        Assert.assertEquals("https://file.example.com/download/result-2.png", result.getFileInfo().get(1).getDownloadUrl());
    }

    @Test
    public void test_generateWithoutFilesDoesNotPersistHistory() {
        WorkspaceImageGenerationServiceImpl service = new WorkspaceImageGenerationServiceImpl();

        IImageGenerationExecutionKernel kernel = Mockito.mock(IImageGenerationExecutionKernel.class);
        Mockito.when(kernel.execute(Mockito.any()))
                .thenReturn(ImageGenerationExecutionResult.builder()
                        .requestId("req-empty")
                        .prompt("生成空图")
                        .mode("images")
                        .summary("空结果")
                        .files(List.of())
                        .build());
        IImageGenerationBatchPersistenceService persistenceService = Mockito.mock(IImageGenerationBatchPersistenceService.class);

        ReflectionTestUtils.setField(service, "imageGenerationExecutionKernel", kernel);
        ReflectionTestUtils.setField(service, "imageGenerationBatchPersistenceService", persistenceService);

        try {
            service.generate(
                    WorkspaceImageGenerationCommand.builder()
                            .requestId("req-empty")
                            .prompt("生成空图")
                            .mode("images")
                            .n(1)
                            .build()
            );
            Assert.fail("预期应抛出无图片结果异常");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("上游未返回可识别的图片结果", expected.getMessage());
        }

        Mockito.verify(persistenceService, Mockito.never()).persistWorkspaceBatch(Mockito.anyString(), Mockito.any());
    }

    @Test
    public void test_queryHistoryReadsWorkspaceLedgerOnly() {
        WorkspaceImageGenerationServiceImpl service = new WorkspaceImageGenerationServiceImpl();
        IToolOutputImageGenerationDao imageGenerationDao = Mockito.mock(IToolOutputImageGenerationDao.class);
        ToolOutputReader toolOutputReader = Mockito.mock(ToolOutputReader.class);

        Mockito.when(imageGenerationDao.countByRequestSource(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE)).thenReturn(2);
        Map<String, Object> newestRow = new LinkedHashMap<>();
        newestRow.put("request_id", "req-new");
        newestRow.put("request_source", ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE);
        newestRow.put("tool_call_id", ImageGenerationBatchPersistenceServiceImpl.buildWorkspaceToolCallId("req-new"));
        newestRow.put("status", ExecutionLedgerConstants.STATUS_SUCCESS);
        newestRow.put("prompt", "最新批次");
        newestRow.put("mode", "images");
        newestRow.put("size", "1024x1024");
        newestRow.put("batch_count", 2);
        newestRow.put("source_image_count", 0);
        newestRow.put("mask_image_count", 0);
        newestRow.put("used_fallback", 0);
        newestRow.put("created_at", LocalDateTime.of(2026, 4, 26, 10, 0, 0));
        Mockito.when(imageGenerationDao.queryPageByRequestSource(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE, 0, 1))
                .thenReturn(List.of(newestRow));
        Map<String, Object> oldestRow = new LinkedHashMap<>();
        oldestRow.put("request_id", "req-old");
        oldestRow.put("request_source", ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE);
        oldestRow.put("tool_call_id", ImageGenerationBatchPersistenceServiceImpl.buildWorkspaceToolCallId("req-old"));
        oldestRow.put("status", ExecutionLedgerConstants.STATUS_SUCCESS);
        oldestRow.put("prompt", "旧批次");
        oldestRow.put("mode", "edits");
        oldestRow.put("size", "1024x1024");
        oldestRow.put("batch_count", 1);
        oldestRow.put("source_image_count", 1);
        oldestRow.put("mask_image_count", 1);
        oldestRow.put("used_fallback", 1);
        oldestRow.put("created_at", LocalDateTime.of(2026, 4, 25, 9, 0, 0));
        Mockito.when(imageGenerationDao.queryPageByRequestSource(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE, 1, 1))
                .thenReturn(List.of(oldestRow));
        Mockito.when(toolOutputReader.readDirect("req-new", ImageGenerationBatchPersistenceServiceImpl.buildWorkspaceToolCallId("req-new")))
                .thenReturn(java.util.Optional.of(ToolOutputView.builder()
                        .toolName("image_generation_tool")
                        .requestId("req-new")
                        .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE)
                        .toolCallId(ImageGenerationBatchPersistenceServiceImpl.buildWorkspaceToolCallId("req-new"))
                        .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                        .createdAt(LocalDateTime.of(2026, 4, 26, 10, 0, 0))
                        .structuredOutput(ImageGenerationToolOutput.builder()
                                .prompt("最新批次")
                                .mode("images")
                                .size("1024x1024")
                                .batchCount(2)
                                .sourceImageCount(0)
                                .maskImageCount(0)
                                .usedFallback(false)
                                .fileRefs(List.of(
                                        ToolFileRef.builder().fileName("req-new-0.png").previewUrl("https://file.example.com/req-new-0.png").mimeType("image/png").build(),
                                        ToolFileRef.builder().fileName("req-new-1.png").previewUrl("https://file.example.com/req-new-1.png").mimeType("image/png").build()
                                ))
                                .build())
                        .build()));
        Mockito.when(toolOutputReader.readDirect("req-old", ImageGenerationBatchPersistenceServiceImpl.buildWorkspaceToolCallId("req-old")))
                .thenReturn(java.util.Optional.of(ToolOutputView.builder()
                        .toolName("image_generation_tool")
                        .requestId("req-old")
                        .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE)
                        .toolCallId(ImageGenerationBatchPersistenceServiceImpl.buildWorkspaceToolCallId("req-old"))
                        .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                        .createdAt(LocalDateTime.of(2026, 4, 25, 9, 0, 0))
                        .structuredOutput(ImageGenerationToolOutput.builder()
                                .prompt("旧批次")
                                .mode("edits")
                                .size("1024x1024")
                                .batchCount(1)
                                .sourceImageCount(1)
                                .maskImageCount(1)
                                .usedFallback(true)
                                .fileRefs(List.of(
                                        ToolFileRef.builder().fileName("req-old-0.png").previewUrl("https://file.example.com/req-old-0.png").mimeType("image/png").build()
                                ))
                                .build())
                        .build()));

        ReflectionTestUtils.setField(service, "toolOutputImageGenerationDao", imageGenerationDao);
        ReflectionTestUtils.setField(service, "toolOutputReader", toolOutputReader);

        WorkspaceImageGenerationHistoryPage firstPage = service.queryHistory(1, 1);
        Assert.assertEquals(2, firstPage.getTotal());
        Assert.assertEquals(1, firstPage.getList().size());
        Assert.assertEquals("req-new", firstPage.getList().get(0).getRequestId());
        Assert.assertEquals(2, firstPage.getList().get(0).getImages().size());
        Assert.assertEquals("最新批次", firstPage.getList().get(0).getPrompt());

        WorkspaceImageGenerationHistoryPage secondPage = service.queryHistory(2, 1);
        Assert.assertEquals(2, secondPage.getTotal());
        Assert.assertEquals(1, secondPage.getList().size());
        Assert.assertEquals("req-old", secondPage.getList().get(0).getRequestId());
        Assert.assertEquals(1, secondPage.getList().get(0).getImages().size());
        Assert.assertTrue(secondPage.getList().get(0).getUsedFallback());
    }
}
