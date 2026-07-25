package org.wwz.ai.trigger.http.admin;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.subagent.SubAgentDefinitionAdminApplicationService;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionRecord;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionUpsertCommand;
import org.wwz.ai.trigger.http.admin.vo.SubAgentDefinitionRespVO;
import org.wwz.ai.trigger.http.admin.vo.SubAgentDefinitionUpsertReqVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 可配置子 Agent 定义管理接口。
 */
@RestController
@RequestMapping("/api/v1/admin/sub-agent-definitions")
public class SubAgentDefinitionAdminController {

    @Resource
    private SubAgentDefinitionAdminApplicationService subAgentDefinitionAdminApplicationService;

    @GetMapping("/query-list")
    public Response<List<SubAgentDefinitionRespVO>> queryList() {
        List<SubAgentDefinitionRecord> list = subAgentDefinitionAdminApplicationService.listAll();
        List<SubAgentDefinitionRespVO> data = list == null
                ? List.of()
                : list.stream().map(this::toRespVO).collect(Collectors.toList());
        return success(data);
    }

    @GetMapping("/{agentKey}")
    public Response<SubAgentDefinitionRespVO> get(@PathVariable("agentKey") String agentKey) {
        return subAgentDefinitionAdminApplicationService.get(agentKey)
                .map(record -> success(toRespVO(record)))
                .orElseGet(() -> Response.<SubAgentDefinitionRespVO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("agentKey 不存在")
                        .build());
    }

    @GetMapping("/tool-catalog")
    public Response<List<String>> toolCatalog() {
        return success(List.of(
                "*",
                "workspace_read",
                "workspace_list",
                "workspace_glob",
                "workspace_grep",
                "workspace_write",
                "workspace_edit",
                "file_tool",
                "deep_search",
                "WebFetch",
                "WebSearch",
                "Bash",
                "PowerShell",
                "code_interpreter",
                "report_tool",
                "skill_tool",
                "script_runner_tool",
                "image_generation_tool",
                "data_analysis",
                "multimodalagent_tool",
                "TodoWrite",
                "TaskCreate",
                "TaskGet",
                "TaskUpdate",
                "TaskList"
        ));
    }

    @PostMapping("/create")
    public Response<Boolean> create(@RequestBody SubAgentDefinitionUpsertReqVO request) {
        try {
            return success(subAgentDefinitionAdminApplicationService.create(toCommand(request)));
        } catch (IllegalArgumentException ex) {
            return fail(ex.getMessage());
        }
    }

    @PutMapping("/update")
    public Response<Boolean> update(@RequestBody SubAgentDefinitionUpsertReqVO request) {
        try {
            return success(subAgentDefinitionAdminApplicationService.update(toCommand(request)));
        } catch (IllegalArgumentException ex) {
            return fail(ex.getMessage());
        }
    }

    @DeleteMapping("/{agentKey}")
    public Response<Boolean> delete(@PathVariable("agentKey") String agentKey) {
        try {
            return success(subAgentDefinitionAdminApplicationService.delete(agentKey));
        } catch (IllegalArgumentException ex) {
            return fail(ex.getMessage());
        }
    }

    @PostMapping("/reload")
    public Response<Integer> reload() {
        return success(subAgentDefinitionAdminApplicationService.reload());
    }

    private SubAgentDefinitionUpsertCommand toCommand(SubAgentDefinitionUpsertReqVO request) {
        if (request == null) {
            return null;
        }
        return SubAgentDefinitionUpsertCommand.builder()
                .agentKey(request.getAgentKey())
                .displayName(request.getDisplayName())
                .whenToUse(request.getWhenToUse())
                .systemPrompt(request.getSystemPrompt())
                .allowedTools(toSet(request.getAllowedTools()))
                .disallowedTools(toSet(request.getDisallowedTools()))
                .maxSteps(request.getMaxSteps())
                .status(request.getStatus())
                .build();
    }

    private SubAgentDefinitionRespVO toRespVO(SubAgentDefinitionRecord record) {
        if (record == null) {
            return null;
        }
        return SubAgentDefinitionRespVO.builder()
                .agentKey(record.getAgentKey())
                .displayName(record.getDisplayName())
                .whenToUse(record.getWhenToUse())
                .systemPrompt(record.getSystemPrompt())
                .allowedTools(toList(record.getAllowedTools()))
                .disallowedTools(toList(record.getDisallowedTools()))
                .maxSteps(record.getMaxSteps())
                .status(record.getStatus())
                .build();
    }

    private static Set<String> toSet(List<String> list) {
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        Set<String> set = new LinkedHashSet<>();
        for (String item : list) {
            if (item != null && !item.isBlank()) {
                set.add(item.trim());
            }
        }
        return set.isEmpty() ? null : set;
    }

    private static List<String> toList(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(set);
    }

    private static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private static <T> Response<T> fail(String info) {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(info == null ? ResponseCode.ILLEGAL_PARAMETER.getInfo() : info)
                .build();
    }
}
