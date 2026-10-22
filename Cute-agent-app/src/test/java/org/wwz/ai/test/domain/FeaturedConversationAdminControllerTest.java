package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.featured.FeaturedConversationAdminApplicationService;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationAdminView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPageResult;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationQueryCondition;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationUpsertCommand;
import org.wwz.ai.trigger.http.admin.FeaturedConversationAdminController;
import org.wwz.ai.trigger.http.admin.vo.FeaturedConversationAdminQueryReqVO;
import org.wwz.ai.trigger.http.admin.vo.FeaturedConversationAdminRespVO;
import org.wwz.ai.trigger.http.admin.vo.FeaturedConversationAdminUpsertReqVO;
import org.wwz.ai.trigger.http.agent.vo.PageRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精品对话管理接口回归测试。
 */
public class FeaturedConversationAdminControllerTest {

    @Test
    public void shouldCreateFeaturedConversationFromExistingSession() {
        StubFeaturedConversationAdminApplicationService service =
                new StubFeaturedConversationAdminApplicationService();
        FeaturedConversationAdminController controller = new FeaturedConversationAdminController();
        ReflectionTestUtils.setField(
                controller,
                "featuredConversationAdminApplicationService",
                service
        );

        FeaturedConversationAdminUpsertReqVO request = FeaturedConversationAdminUpsertReqVO.builder()
                .sessionId("session-admin-001")
                .title("精品标题")
                .summary("精品摘要")
                .tags(List.of("写作", "案例"))
                .coverUrl("https://file.example.com/cover.png")
                .sortOrder(100)
                .operator("admin")
                .build();

        Response<Boolean> response = controller.create(request);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertTrue(response.getData());
        Assert.assertEquals("session-admin-001", service.lastCommand.getSessionId());
    }

    @Test
    public void shouldOfflineFeaturedConversationImmediately() {
        StubFeaturedConversationAdminApplicationService service =
                new StubFeaturedConversationAdminApplicationService();
        FeaturedConversationAdminController controller = new FeaturedConversationAdminController();
        ReflectionTestUtils.setField(
                controller,
                "featuredConversationAdminApplicationService",
                service
        );

        Response<Boolean> response = controller.offline("featured-admin-001", "admin");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals("OFFLINE", service.lastStatus);
    }

    @Test
    public void shouldQueryAdminListWithStatusFilter() {
        StubFeaturedConversationAdminApplicationService service =
                new StubFeaturedConversationAdminApplicationService();
        FeaturedConversationAdminController controller = new FeaturedConversationAdminController();
        ReflectionTestUtils.setField(
                controller,
                "featuredConversationAdminApplicationService",
                service
        );

        FeaturedConversationAdminQueryReqVO request = FeaturedConversationAdminQueryReqVO.builder()
                .status("ONLINE")
                .pageNo(1)
                .pageSize(10)
                .build();

        Response<PageRespVO<FeaturedConversationAdminRespVO>> response = controller.queryList(request);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals(1, response.getData().getList().size());
        Assert.assertEquals("ONLINE", response.getData().getList().get(0).getStatus());
    }

    private static final class StubFeaturedConversationAdminApplicationService
            extends FeaturedConversationAdminApplicationService {

        private FeaturedConversationUpsertCommand lastCommand;
        private String lastStatus;

        StubFeaturedConversationAdminApplicationService() {
            super(null, null);
        }

        @Override
        public boolean create(FeaturedConversationUpsertCommand command) {
            this.lastCommand = command;
            return true;
        }

        @Override
        public boolean update(FeaturedConversationUpsertCommand command) {
            this.lastCommand = command;
            return true;
        }

        @Override
        public boolean online(String featuredId, String operator) {
            this.lastStatus = "ONLINE";
            return true;
        }

        @Override
        public boolean offline(String featuredId, String operator) {
            this.lastStatus = "OFFLINE";
            return true;
        }

        @Override
        public FeaturedConversationPageResult<FeaturedConversationAdminView> queryList(
                FeaturedConversationQueryCondition condition
        ) {
            return FeaturedConversationPageResult.<FeaturedConversationAdminView>builder()
                    .total(1)
                    .list(List.of(
                            FeaturedConversationAdminView.builder()
                                    .featuredId("featured-admin-001")
                                    .sessionId("session-admin-001")
                                    .title("精品标题")
                                    .summary("精品摘要")
                                    .tags(List.of("写作", "案例"))
                                    .coverUrl("https://file.example.com/cover.png")
                                    .sortOrder(100)
                                    .status(condition.getStatus())
                                    .publishedAt(LocalDateTime.of(2026, 7, 6, 14, 0, 0))
                                    .updatedAt(LocalDateTime.of(2026, 7, 6, 15, 0, 0))
                                    .build()
                    ))
                    .build();
        }
    }
}
