package org.wwz.ai.trigger.http.agent.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.image.IWorkspaceImageGenerationApplicationService;
import org.wwz.ai.application.agent.image.result.WorkspaceImageGenerationView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageHistoryPageView;
import org.wwz.ai.trigger.http.agent.image.mapper.WorkspaceImageVoMapper;
import org.wwz.ai.trigger.http.agent.image.vo.WorkspaceImageGenerationReqVO;
import org.wwz.ai.trigger.http.agent.image.vo.WorkspaceImageGenerationRespVO;
import org.wwz.ai.trigger.http.agent.image.vo.WorkspaceImageHistoryBatchRespVO;
import org.wwz.ai.trigger.http.agent.vo.PageRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.List;

/**
 * 生图工作台接口。
 * <p>只做 HTTP 协议适配：请求 VO -> 用例命令，用例输出 -> 响应 VO；生图编排由 Case 应用服务承接。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/image-generation")
public class AgentImageGenerationController {

    @Resource
    private IWorkspaceImageGenerationApplicationService workspaceImageGenerationApplicationService;

    @PostMapping("/generate")
    public Response<WorkspaceImageGenerationRespVO> generate(@RequestBody WorkspaceImageGenerationReqVO reqVO) {
        try {
            if (reqVO == null) {
                throw new IllegalArgumentException("请求体不能为空");
            }
            WorkspaceImageGenerationView result = workspaceImageGenerationApplicationService.generate(
                    WorkspaceImageVoMapper.toCommand(reqVO)
            );
            return Response.<WorkspaceImageGenerationRespVO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(WorkspaceImageVoMapper.toRespVO(result))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.<WorkspaceImageGenerationRespVO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("生图工作台生成失败", e);
            return Response.<WorkspaceImageGenerationRespVO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    @GetMapping("/history")
    public Response<PageRespVO<WorkspaceImageHistoryBatchRespVO>> history(@RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
                                                                          @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        try {
            WorkspaceImageHistoryPageView historyPage =
                    workspaceImageGenerationApplicationService.queryHistory(WorkspaceImageVoMapper.toQuery(pageNo, pageSize));

            List<WorkspaceImageHistoryBatchRespVO> list = historyPage.getList().stream()
                    .map(WorkspaceImageVoMapper::toHistoryRespVO)
                    .toList();
            return Response.<PageRespVO<WorkspaceImageHistoryBatchRespVO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(PageRespVO.<WorkspaceImageHistoryBatchRespVO>builder()
                            .total(historyPage.getTotal())
                            .list(list)
                            .build())
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.<PageRespVO<WorkspaceImageHistoryBatchRespVO>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("查询生图历史失败", e);
            return Response.<PageRespVO<WorkspaceImageHistoryBatchRespVO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }
}
