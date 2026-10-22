package org.wwz.ai.trigger.http.admin;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.featured.FeaturedConversationAdminApplicationService;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationAdminView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPageResult;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationQueryCondition;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationUpsertCommand;
import org.wwz.ai.trigger.http.admin.vo.FeaturedConversationAdminQueryReqVO;
import org.wwz.ai.trigger.http.admin.vo.FeaturedConversationAdminRespVO;
import org.wwz.ai.trigger.http.admin.vo.FeaturedConversationAdminUpsertReqVO;
import org.wwz.ai.trigger.http.agent.vo.PageRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 精品对话管理 HTTP 适配器。
 *
 * <p>控制器负责把管理端 VO 转换为应用层命令或查询条件，并把领域查询视图转换为
 * 前端分页 VO；精品对话的创建、更新、上下线和查询规则由应用服务负责。这样 HTTP
 * 字段名或分页表达变化时，不会把展示模型传播到领域层。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/featured-conversations")
public class FeaturedConversationAdminController {

    @Resource
    private FeaturedConversationAdminApplicationService featuredConversationAdminApplicationService;

    @PostMapping("/create")
    public Response<Boolean> create(@RequestBody FeaturedConversationAdminUpsertReqVO request) {
        // 写入端点只完成 VO 到命令的边界转换，实际校验、持久化和审计由应用服务处理。
        return success(featuredConversationAdminApplicationService.create(toCommand(request)));
    }

    @PutMapping("/update")
    public Response<Boolean> update(@RequestBody FeaturedConversationAdminUpsertReqVO request) {
        return success(featuredConversationAdminApplicationService.update(toCommand(request)));
    }

    @PostMapping("/online/{featuredId}")
    public Response<Boolean> online(
            @PathVariable("featuredId") String featuredId,
            @RequestParam("operator") String operator
    ) {
        // 上线和下线是显式状态迁移，操作人作为审计信息传入应用服务。
        return success(featuredConversationAdminApplicationService.online(featuredId, operator));
    }

    @PostMapping("/offline/{featuredId}")
    public Response<Boolean> offline(
            @PathVariable("featuredId") String featuredId,
            @RequestParam("operator") String operator
    ) {
        return success(featuredConversationAdminApplicationService.offline(featuredId, operator));
    }

    @PostMapping("/query-list")
    public Response<PageRespVO<FeaturedConversationAdminRespVO>> queryList(
            @RequestBody FeaturedConversationAdminQueryReqVO request
    ) {
        // HTTP 页码从 1 开始，领域查询使用 offset/limit；这里统一钳制页码和页大小，
        // 防止非法分页参数把展示层的约定带入领域模型。
        FeaturedConversationPageResult<FeaturedConversationAdminView> page =
                featuredConversationAdminApplicationService.queryList(
                        FeaturedConversationQueryCondition.builder()
                                .status(request.getStatus())
                                .sessionId(request.getSessionId())
                                .title(request.getTitle())
                                .offset((Math.max(1, request.getPageNo()) - 1) * Math.max(1, request.getPageSize()))
                                .limit(Math.max(1, request.getPageSize()))
                                .build()
                );

        return Response.<PageRespVO<FeaturedConversationAdminRespVO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(PageRespVO.<FeaturedConversationAdminRespVO>builder()
                        .total(page == null ? 0 : page.getTotal())
                        .list(page == null || CollectionUtils.isEmpty(page.getList())
                                ? List.of()
                                : page.getList().stream().map(this::toRespVO).collect(Collectors.toList()))
                        .build())
                .build();
    }

    private FeaturedConversationUpsertCommand toCommand(FeaturedConversationAdminUpsertReqVO request) {
        // 命令只承载业务输入，封面资源键和 URL 同时保留以兼容现有资源引用方式。
        return FeaturedConversationUpsertCommand.builder()
                .featuredId(request.getFeaturedId())
                .sessionId(request.getSessionId())
                .title(request.getTitle())
                .summary(request.getSummary())
                .coverResourceKey(request.getCoverResourceKey())
                .coverUrl(request.getCoverUrl())
                .tags(request.getTags())
                .sortOrder(request.getSortOrder())
                .operator(request.getOperator())
                .build();
    }

    private FeaturedConversationAdminRespVO toRespVO(FeaturedConversationAdminView item) {
        // 查询视图到响应 VO 的映射不回传领域对象，避免领域模型成为 HTTP 契约。
        if (item == null) {
            return null;
        }
        return FeaturedConversationAdminRespVO.builder()
                .featuredId(item.getFeaturedId())
                .sessionId(item.getSessionId())
                .title(item.getTitle())
                .summary(item.getSummary())
                .tags(item.getTags())
                .coverUrl(item.getCoverUrl())
                .sortOrder(item.getSortOrder())
                .status(item.getStatus())
                .publishedAt(item.getPublishedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private Response<Boolean> success(boolean data) {
        // 精品对话管理写操作沿用统一响应码，业务布尔值放在 data 中表达执行结果。
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
