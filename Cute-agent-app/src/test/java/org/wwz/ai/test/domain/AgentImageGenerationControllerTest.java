package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.image.IWorkspaceImageGenerationApplicationService;
import org.wwz.ai.application.agent.image.command.WorkspaceImageGenerationCommand;
import org.wwz.ai.application.agent.image.result.WorkspaceImageFileView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageGenerationView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageHistoryBatchView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageHistoryPageView;
import org.wwz.ai.trigger.http.agent.image.AgentImageGenerationController;
import org.wwz.ai.trigger.http.agent.image.vo.WorkspaceImageGenerationReqVO;
import org.wwz.ai.trigger.http.agent.image.vo.WorkspaceImageGenerationRespVO;
import org.wwz.ai.trigger.http.agent.image.vo.WorkspaceImageHistoryBatchRespVO;
import org.wwz.ai.trigger.http.agent.vo.PageRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 生图工作台 Controller 协议适配测试。
 * <p>只验证 HTTP VO <-> 用例模型映射与错误码包装；生图编排由 Case 应用服务承接。</p>
 */
public class AgentImageGenerationControllerTest {

    @Test
    public void test_generateRejectsMissingPrompt() {
        AgentImageGenerationController controller = new AgentImageGenerationController();
        ReflectionTestUtils.setField(controller, "workspaceImageGenerationApplicationService",
                new StubWorkspaceImageGenerationApplicationService());

        WorkspaceImageGenerationReqVO reqVO = new WorkspaceImageGenerationReqVO();

        Response<WorkspaceImageGenerationRespVO> response = controller.generate(reqVO);

        Assert.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assert.assertEquals("prompt不能为空", response.getInfo());
    }

    @Test
    public void test_generateWrapsServiceResponse() {
        AgentImageGenerationController controller = new AgentImageGenerationController();
        AtomicReference<WorkspaceImageGenerationCommand> capturedCommand = new AtomicReference<>();
        ReflectionTestUtils.setField(controller, "workspaceImageGenerationApplicationService",
                new StubWorkspaceImageGenerationApplicationService(capturedCommand));

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
    }

    @Test
    public void test_historyWrapsBatchPageWithoutDeviceHeader() {
        AgentImageGenerationController controller = new AgentImageGenerationController();
        ReflectionTestUtils.setField(controller, "workspaceImageGenerationApplicationService",
                new StubWorkspaceImageGenerationApplicationService());

        Response<PageRespVO<WorkspaceImageHistoryBatchRespVO>> response = controller.history(1, 10);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(1, response.getData().getTotal());
        Assert.assertEquals(1, response.getData().getList().size());
        Assert.assertEquals("history-001", response.getData().getList().get(0).getRequestId());
        Assert.assertEquals(1, response.getData().getList().get(0).getImages().size());
    }

    private static class StubWorkspaceImageGenerationApplicationService
            implements IWorkspaceImageGenerationApplicationService {

        private final AtomicReference<WorkspaceImageGenerationCommand> capturedCommand;

        private StubWorkspaceImageGenerationApplicationService() {
            this(null);
        }

        private StubWorkspaceImageGenerationApplicationService(AtomicReference<WorkspaceImageGenerationCommand> capturedCommand) {
            this.capturedCommand = capturedCommand;
        }

        @Override
        public WorkspaceImageGenerationView generate(WorkspaceImageGenerationCommand command) {
            if (command == null || command.getPrompt() == null || command.getPrompt().isBlank()) {
                throw new IllegalArgumentException("prompt不能为空");
            }
            if (capturedCommand != null) {
                capturedCommand.set(command);
            }
            return WorkspaceImageGenerationView.builder()
                    .data("生成完成")
                    .requestId(command.getRequestId())
                    .mode(command.getMode())
                    .usedFallback(false)
                    .fileInfo(List.of(
                            WorkspaceImageFileView.builder()
                                    .fileName("result.png")
                                    .previewUrl("https://file.example.com/result.png")
                                    .downloadUrl("https://file.example.com/download/result.png")
                                    .mimeType("image/png")
                                    .build()
                    ))
                    .build();
        }

        @Override
        public WorkspaceImageHistoryPageView queryHistory(org.wwz.ai.application.agent.image.command.WorkspaceImageHistoryQuery query) {
            return WorkspaceImageHistoryPageView.builder()
                    .total(1)
                    .list(List.of(
                            WorkspaceImageHistoryBatchView.builder()
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
                                            WorkspaceImageFileView.builder()
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
