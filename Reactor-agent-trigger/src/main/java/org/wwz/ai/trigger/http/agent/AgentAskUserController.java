package org.wwz.ai.trigger.http.agent;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.askuser.AskUserQuestionApplicationService;
import org.wwz.ai.trigger.http.agent.vo.AskUserAnswerReqVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * AskUserQuestion 人机交互接口。
 * 与主对话 SSE 解耦：Agent 挂起等待时，用户通过本接口提交答案。
 */
@RestController
@RequestMapping("/api/agent/ask-user")
public class AgentAskUserController {

    @Resource
    private AskUserQuestionApplicationService askUserQuestionApplicationService;

    /**
     * 提交选择题答案，唤醒挂起的 Agent 工具。
     */
    @PostMapping("/answer")
    public Response<Map<String, Object>> answer(@RequestBody AskUserAnswerReqVO req) {
        try {
            // questionId 对应 Agent 当前挂起的 AskUserQuestion 工具调用，答案不能仅按 sessionId 广播。
            if (req == null || StringUtils.isBlank(req.getQuestionId())) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("questionId 不能为空")
                        .build();
            }
            // 应用服务负责写入答案并唤醒等待方，触发层只传递用户提交的选择集合。
            Map<String, Object> data = askUserQuestionApplicationService.answer(
                    req.getQuestionId(), req.getAnswers());
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

    /**
     * 查询会话下仍 pending 的问询（刷新页面恢复卡片）。
     */
    @GetMapping("/pending")
    public Response<List<Map<String, Object>>> pending(@RequestParam("sessionId") String sessionId) {
        try {
            // pending 用于页面刷新后的恢复展示，不会重新创建问询或触发 Agent 执行。
            List<Map<String, Object>> data = askUserQuestionApplicationService.listPending(sessionId);
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

    /**
     * 取消问询（用户关闭卡片 / 停止会话时）。
     */
    @PostMapping("/cancel")
    public Response<Map<String, Object>> cancel(@RequestBody AskUserAnswerReqVO req) {
        try {
            // 取消与回答共享 questionId，但使用固定原因记录为用户主动关闭，而不是误报为执行失败。
            Map<String, Object> data = askUserQuestionApplicationService.cancel(
                    req == null ? null : req.getQuestionId(), "user_cancelled");
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
