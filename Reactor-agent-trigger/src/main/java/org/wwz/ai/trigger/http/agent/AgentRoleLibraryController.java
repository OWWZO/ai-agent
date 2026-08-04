package org.wwz.ai.trigger.http.agent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.role.IFixRoleQueryService;
import org.wwz.ai.domain.agent.model.valobj.FixRoleVO;
import org.wwz.ai.trigger.http.agent.vo.FixRoleRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 固定 Agent 角色库查询入口。
 *
 * <p>角色库只提供可供前端选择的查询视图，不负责创建或修改角色。应用服务决定
 * 哪些角色当前可用，控制器只把领域值对象转换成稳定的响应 VO，避免 HTTP 契约
 * 依赖领域对象的字段结构。</p>
 */
@RestController
@RequestMapping("/api/agent/role-library")
public class AgentRoleLibraryController {

    @Resource
    private IFixRoleQueryService fixRoleQueryService;

    @GetMapping("/list")
    public Response<List<FixRoleRespVO>> list() {
        // 列表查询不携带会话状态；默认角色标识随每条记录返回，供前端选择器展示。
        List<FixRoleRespVO> roles = fixRoleQueryService.queryAvailableRoles().stream()
                .map(this::toRespVO)
                .collect(Collectors.toList());

        return Response.<List<FixRoleRespVO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(roles)
                .build();
    }

    private FixRoleRespVO toRespVO(FixRoleVO roleVO) {
        // 显式映射领域字段，确保角色库后续扩展内部属性时不会意外扩大 HTTP 返回面。
        return FixRoleRespVO.builder()
                .agentId(roleVO.getAgentId())
                .agentName(roleVO.getAgentName())
                .description(roleVO.getDescription())
                .defaultRole(roleVO.isDefaultRole())
                .build();
    }
}
