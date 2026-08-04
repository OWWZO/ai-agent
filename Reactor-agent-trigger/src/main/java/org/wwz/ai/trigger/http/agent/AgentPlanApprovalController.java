package org.wwz.ai.trigger.http.agent;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.planmode.PlanApprovalApplicationService;
import org.wwz.ai.trigger.http.agent.vo.PlanApprovalReqVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Plan Mode 计划批准接口（对标 cc-haha ExitPlanMode 人批）。
 * 与主对话 SSE 解耦：ExitPlanMode 挂起时用户通过本接口批准/拒绝。
 */
@RestController
@RequestMapping("/api/agent/plan-approval")
public class AgentPlanApprovalController {

    @Resource
    private PlanApprovalApplicationService planApprovalApplicationService;

    @PostMapping("/approve")
    public Response<Map<String, Object>> approve(@RequestBody PlanApprovalReqVO req) {
        try {
            // approvalId 是挂起的 ExitPlanMode 与用户操作之间的关联键，不能用 sessionId 猜测目标审批。
            if (req == null || StringUtils.isBlank(req.getApprovalId())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("approvalId 不能为空")
                        .build();
            }
            // editedPlanContent 和 feedback 一并交给应用服务，保留“用户修改后批准”的完整语义。
            Map<String, Object> data = planApprovalApplicationService.approve(
                    req.getApprovalId(), req.getEditedPlanContent(), req.getFeedback());
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    @PostMapping("/reject")
    public Response<Map<String, Object>> reject(@RequestBody PlanApprovalReqVO req) {
        try {
            // 拒绝同样按 approvalId 定位单次待审批计划，避免误操作同一会话中的其它轮次。
            if (req == null || StringUtils.isBlank(req.getApprovalId())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("approvalId 不能为空")
                        .build();
            }
            Map<String, Object> data = planApprovalApplicationService.reject(
                    req.getApprovalId(), req.getFeedback());
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    @GetMapping("/pending")
    public Response<List<Map<String, Object>>> pending(@RequestParam("sessionId") String sessionId) {
        try {
            // 查询是会话维度的列表操作，与 approve/reject 的单审批操作使用不同查询边界。
            List<Map<String, Object>> data = planApprovalApplicationService.listPending(sessionId);
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    @PostMapping("/cancel")
    public Response<Map<String, Object>> cancel(@RequestBody PlanApprovalReqVO req) {
        try {
            // 取消用于清理前端放弃的挂起审批，固定原因交给应用服务记录状态转换。
            Map<String, Object> data = planApprovalApplicationService.cancel(
                    req == null ? null : req.getApprovalId(), "user_cancelled");
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }
}
