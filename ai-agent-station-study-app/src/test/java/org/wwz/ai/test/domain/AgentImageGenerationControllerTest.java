package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryBatch;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryPage;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationResult;
import org.wwz.ai.domain.agent.reactor.service.IWorkspaceImageGenerationService;
import org.wwz.ai.trigger.http.agent.AgentImageGenerationController;
import org.wwz.ai.trigger.http.agent.vo.PageRespVO;
import org.wwz.ai.trigger.http.agent.vo.WorkspaceImageGenerationReqVO;
import org.wwz.ai.trigger.http.agent.vo.WorkspaceImageGenerationRespVO;
import org.wwz.ai.trigger.http.agent.vo.WorkspaceImageHistoryBatchRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class AgentImageGenerationControllerTest {

    @Test
    public void test_generateRejectsMissingPrompt() {
        AgentImageGenerationController controller = new AgentImageGenerationController();
        ReflectionTestUtils.setField(controller, "workspaceImageGenerationService", new StubWorkspaceImageGenerationService());

        WorkspaceImageGenerationReqVO reqVO = new WorkspaceImageGenerationReqVO();

        Response<WorkspaceImageGenerationRespVO> response = controller.generate(reqVO);

        Assert.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assert.assertEquals("prompt不能为空", response.getInfo());
    }

    @Test
    public void test_generateWrapsServiceResponse() {
        AgentImageGenerationController controller = new AgentImageGenerationController();
        AtomicReference<WorkspaceImageGenerationCommand> capturedCommand = new AtomicReference<>();
        ReflectionTestUtils.setField(controller, "workspaceImageGenerationService", new StubWorkspaceImageGenerationService(capturedCommand));

        WorkspaceImageGenerationReqVO reqVO = new WorkspaceImageGenerationReqVO();
        reqVO.setRequestId("req-001");
        reqVO.setPrompt("生成一张风景图");
        reqVO.setMode("images");
        reqVO.setModel("gpt-image-2");
        reqVO.setN(1);

        Response<WorkspaceImageGenerationRespVO> response = controller.generate(reqVO);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals("req-001", response.getData().getRequestId());
        Assert.assertEquals(1, response.getData().getFileInfo().size());
        Assert.assertEquals("https://file.example.com/result.png", response.getData().getFileInfo().get(0).getPreviewUrl());
        Assert.assertNotNull(capturedCommand.get());
        Assert.assertEquals("gpt-image-2", capturedCommand.get().getModel());
    }

    @Test
    public void test_historyWrapsBatchPageWithoutDeviceHeader() {
        AgentImageGenerationController controller = new AgentImageGenerationController();
        ReflectionTestUtils.setField(controller, "workspaceImageGenerationService", new StubWorkspaceImageGenerationService());

        Response<PageRespVO<WorkspaceImageHistoryBatchRespVO>> response = controller.history(1, 10);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(1, response.getData().getTotal());
        Assert.assertEquals(1, response.getData().getList().size());
        Assert.assertEquals("history-001", response.getData().getList().get(0).getRequestId());
        Assert.assertEquals(1, response.getData().getList().get(0).getImages().size());
    }

    private static class StubWorkspaceImageGenerationService implements IWorkspaceImageGenerationService {

        private final AtomicReference<WorkspaceImageGenerationCommand> capturedCommand;

        private StubWorkspaceImageGenerationService() {
            this(null);
        }

        private StubWorkspaceImageGenerationService(AtomicReference<WorkspaceImageGenerationCommand> capturedCommand) {
            this.capturedCommand = capturedCommand;
        }

        @Override
        public WorkspaceImageGenerationResult generate(WorkspaceImageGenerationCommand command) {
            if (command == null || command.getPrompt() == null || command.getPrompt().isBlank()) {
                throw new IllegalArgumentException("prompt不能为空");
            }
            if (capturedCommand != null) {
                capturedCommand.set(command);
            }
            return WorkspaceImageGenerationResult.builder()
                    .data("生成完成")
                    .requestId(command.getRequestId())
                    .mode(command.getMode())
                    .usedFallback(false)
                    .fileInfo(List.of(
                            WorkspaceImageFile.builder()
                                    .fileName("result.png")
                                    .previewUrl("https://file.example.com/result.png")
                                    .downloadUrl("https://file.example.com/download/result.png")
                                    .mimeType("image/png")
                                    .build()
                    ))
                    .build();
        }

        @Override
        public WorkspaceImageGenerationHistoryPage queryHistory(int pageNo, int pageSize) {
            return WorkspaceImageGenerationHistoryPage.builder()
                    .total(1)
                    .list(List.of(
                            WorkspaceImageGenerationHistoryBatch.builder()
                                    .requestId("history-001")
                                    .prompt("历史图片")
                                    .mode("images")
                                    .size("1024x1024")
                                    .batchCount(1)
                                    .sourceImageCount(0)
                                    .maskImageCount(0)
                                    .usedFallback(false)
                                    .createdAt(LocalDateTime.of(2026, 4, 26, 12, 0, 0))
                                    .images(List.of(
                                            WorkspaceImageFile.builder()
                                                    .fileName("history.png")
                                                    .previewUrl("https://file.example.com/history.png")
                                                    .downloadUrl("https://file.example.com/download/history.png")
                                                    .mimeType("image/png")
                                                    .build()
                                    ))
                                    .build()
                    ))
                    .build();
        }
    }
}
