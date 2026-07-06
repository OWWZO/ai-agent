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
 * 精品对话管理接口。
 */
@RestController
@RequestMapping("/api/v1/admin/featured-conversations")
public class FeaturedConversationAdminController {

    @Resource
    private FeaturedConversationAdminApplicationService featuredConversationAdminApplicationService;

    @PostMapping("/create")
    public Response<Boolean> create(@RequestBody FeaturedConversationAdminUpsertReqVO request) {
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
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
