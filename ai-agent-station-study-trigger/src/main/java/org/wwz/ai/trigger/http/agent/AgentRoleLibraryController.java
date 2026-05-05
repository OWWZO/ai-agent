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
 * Fix 角色库接口
 */
@RestController
@RequestMapping("/api/agent/role-library")
public class AgentRoleLibraryController {

    @Resource
    private IFixRoleQueryService fixRoleQueryService;

    @GetMapping("/list")
    public Response<List<FixRoleRespVO>> list() {
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
        return FixRoleRespVO.builder()
                .agentId(roleVO.getAgentId())
                .agentName(roleVO.getAgentName())
                .description(roleVO.getDescription())
                .defaultRole(roleVO.isDefaultRole())
                .build();
    }
}
