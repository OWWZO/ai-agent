package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
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

public class AgentImageGenerationControllerTest {

    @Test
    public void test_generateRejectsMissingDeviceId() {
        AgentImageGenerationController controller = new AgentImageGenerationController();
        ReflectionTestUtils.setField(controller, "workspaceImageGenerationService", new StubWorkspaceImageGenerationService());

        WorkspaceImageGenerationReqVO reqVO = new WorkspaceImageGenerationReqVO();
        reqVO.setPrompt("生成一张风景图");

        Response<WorkspaceImageGenerationRespVO> response = controller.generate(new MockHttpServletRequest(), reqVO);

        Assert.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assert.assertEquals("X-Device-Id header is required", response.getInfo());
    }

    @Test
    public void test_generateWrapsServiceResponse() {
        AgentImageGenerationController controller = new AgentImageGenerationController();
        ReflectionTestUtils.setField(controller, "workspaceImageGenerationService", new StubWorkspaceImageGenerationService());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Device-Id", "device-001");

        WorkspaceImageGenerationReqVO reqVO = new WorkspaceImageGenerationReqVO();
        reqVO.setRequestId("req-001");
        reqVO.setPrompt("生成一张风景图");
        reqVO.setMode("images");
        reqVO.setN(1);

        Response<WorkspaceImageGenerationRespVO> response = controller.generate(request, reqVO);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals("req-001", response.getData().getRequestId());
        Assert.assertEquals(1, response.getData().getFileInfo().size());
        Assert.assertEquals("https://file.example.com/result.png", response.getData().getFileInfo().get(0).getPreviewUrl());
    }

    @Test
    public void test_historyWrapsBatchPage() {
        AgentImageGenerationController controller = new AgentImageGenerationController();
        ReflectionTestUtils.setField(controller, "workspaceImageGenerationService", new StubWorkspaceImageGenerationService());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Device-Id", "device-001");

        Response<PageRespVO<WorkspaceImageHistoryBatchRespVO>> response = controller.history(request, 1, 10);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(1, response.getData().getTotal());
        Assert.assertEquals(1, response.getData().getList().size());
        Assert.assertEquals("history-001", response.getData().getList().get(0).getRequestId());
        Assert.assertEquals(1, response.getData().getList().get(0).getImages().size());
    }

    private static class StubWorkspaceImageGenerationService implements IWorkspaceImageGenerationService {

        @Override
        public WorkspaceImageGenerationResult generate(String deviceId, WorkspaceImageGenerationCommand command) {
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
        public WorkspaceImageGenerationHistoryPage queryHistory(String deviceId, int pageNo, int pageSize) {
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
