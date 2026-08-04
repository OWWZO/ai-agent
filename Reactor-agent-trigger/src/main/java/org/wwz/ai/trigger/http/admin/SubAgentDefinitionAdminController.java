package org.wwz.ai.trigger.http.admin;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 * 可配置子 Agent 定义管理 HTTP 适配器。
 *
 * <p>该控制器把管理端输入转换为子 Agent 定义命令，并把应用服务返回的领域记录
 * 转换为响应 VO。定义的持久化、有效性校验和运行时重载由应用服务负责；控制器只
 * 保留 HTTP 路由、兼容性映射和统一响应格式。</p>
 *
 * <p>工具目录是管理端可选工具的静态白名单展示，不代表每个子 Agent 当前都能使用
 * 全部工具；最终生效范围仍由定义中的允许/禁止集合和运行时注册表共同决定。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/sub-agent-definitions")
public class SubAgentDefinitionAdminController {

    @Resource
    private SubAgentDefinitionAdminApplicationService subAgentDefinitionAdminApplicationService;

    @GetMapping("/query-list")
    public Response<List<SubAgentDefinitionRespVO>> queryList() {
        // 应用服务返回 null 时按空列表处理，保持管理端列表接口的稳定数据形状。
        List<SubAgentDefinitionRecord> list = subAgentDefinitionAdminApplicationService.listAll();
        List<SubAgentDefinitionRespVO> data = list == null
                ? List.of()
                : list.stream().map(this::toRespVO).collect(Collectors.toList());
        return success(data);
    }

    @GetMapping("/tool-catalog")
    public Response<List<String>> toolCatalog() {
        // 目录用于配置页面提示可用工具名称；它不直接修改注册表或子 Agent 定义。
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
                "code_interpreter",
                "code_execution",
                "report_tool",
                "skill_tool",
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

    @GetMapping("/{agentKey}")
    public Response<SubAgentDefinitionRespVO> get(@PathVariable("agentKey") String agentKey) {
        // 未找到定义时返回参数错误而非成功空数据，便于管理端区分不存在的 agentKey。
        return subAgentDefinitionAdminApplicationService.get(agentKey)
                .map(record -> success(toRespVO(record)))
                .orElseGet(() -> Response.<SubAgentDefinitionRespVO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("agentKey 不存在")
                        .build());
    }

    @PostMapping("/create")
    public Response<Boolean> create(@RequestBody SubAgentDefinitionUpsertReqVO request) {
        // 应用服务以 IllegalArgumentException 表达管理输入不合法，控制器将其映射为参数错误。
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
        return deleteByAgentKey(agentKey);
    }

    @DeleteMapping("/delete")
    public Response<Boolean> deleteByQuery(@RequestParam("agentKey") String agentKey) {
        return deleteByAgentKey(agentKey);
    }

    private Response<Boolean> deleteByAgentKey(String agentKey) {
        try {
            return success(subAgentDefinitionAdminApplicationService.delete(agentKey));
        } catch (IllegalArgumentException ex) {
            return fail(ex.getMessage());
        }
    }

    @PostMapping("/reload")
    public Response<Integer> reload() {
        // 重载返回实际生效的定义数量；运行时注册表的替换由应用服务完成。
        return success(subAgentDefinitionAdminApplicationService.reload());
    }

    private SubAgentDefinitionUpsertCommand toCommand(SubAgentDefinitionUpsertReqVO request) {
        // VO 中的列表在进入领域命令前去空、去首尾空白并保持插入顺序，避免配置重复项。
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
        // 响应层使用列表表达工具集合，避免把领域层 Set 的实现细节暴露给调用方。
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
        // LinkedHashSet 同时完成去重和稳定顺序，便于配置保存后的回显保持可预测。
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
        // 领域侧无集合时返回空列表，统一前端 JSON 结构，避免 null 分支扩散到页面。
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(set);
    }

    private static <T> Response<T> success(T data) {
        // 成功响应的业务结果统一放在 data 中，保持各管理端点的返回协议一致。
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private static <T> Response<T> fail(String info) {
        // 这里只转换已知的参数异常；未知运行时异常仍交由框架的全局错误处理策略处理。
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(info == null ? ResponseCode.ILLEGAL_PARAMETER.getInfo() : info)
                .build();
    }
}
