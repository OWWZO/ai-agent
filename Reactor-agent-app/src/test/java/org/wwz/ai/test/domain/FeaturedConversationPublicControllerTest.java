package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.featured.FeaturedConversationPublicQueryApplicationService;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryDetail;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationCardView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPageResult;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPublicDetail;
import org.wwz.ai.trigger.http.agent.AgentFeaturedConversationController;
import org.wwz.ai.trigger.http.agent.vo.FeaturedConversationCardRespVO;
import org.wwz.ai.trigger.http.agent.vo.FeaturedConversationDetailRespVO;
import org.wwz.ai.trigger.http.agent.vo.PageRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精品对话公共接口回归测试。
 */
public class FeaturedConversationPublicControllerTest {

    @Test
    public void shouldReturnOnlineHomeCardsOnly() {
        StubFeaturedConversationPublicQueryApplicationService service =
                new StubFeaturedConversationPublicQueryApplicationService();
        AgentFeaturedConversationController controller = new AgentFeaturedConversationController();
        ReflectionTestUtils.setField(
                controller,
                "featuredConversationPublicQueryApplicationService",
                service
        );

        Response<List<FeaturedConversationCardRespVO>> response = controller.home(3);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals(1, response.getData().size());
        Assert.assertEquals(
                "featured-demo-001",
                response.getData().get(0).getFeaturedId()
        );
        Assert.assertNotNull(response.getData().get(0).getContentLastActiveAt());
    }

    @Test
    public void shouldReturnPagedPublicList() {
        StubFeaturedConversationPublicQueryApplicationService service =
                new StubFeaturedConversationPublicQueryApplicationService();
        AgentFeaturedConversationController controller = new AgentFeaturedConversationController();
        ReflectionTestUtils.setField(
                controller,
                "featuredConversationPublicQueryApplicationService",
                service
        );

        Response<PageRespVO<FeaturedConversationCardRespVO>> response = controller.list(1, 20);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals(1, response.getData().getTotal());
        Assert.assertEquals(
                "featured-demo-001",
                response.getData().getList().get(0).getFeaturedId()
        );
    }

    @Test
    public void shouldReturnReadableFallbackWhenLiveContentUnavailable() {
        StubFeaturedConversationPublicQueryApplicationService service =
                new StubFeaturedConversationPublicQueryApplicationService();
        service.detail = FeaturedConversationPublicDetail.builder()
                .featuredId("featured-demo-002")
                .sessionId("session-demo-002")
                .title("异常案例")
                .summary("正文暂不可用")
                .status("ONLINE")
                .publishedAt(LocalDateTime.of(2026, 7, 6, 12, 0, 0))
                .contentLastActiveAt(null)
                .contentAvailable(false)
                .contentUnavailableReason("session_history_missing")
                .historyDetail(null)
                .build();
        AgentFeaturedConversationController controller = new AgentFeaturedConversationController();
        ReflectionTestUtils.setField(
                controller,
                "featuredConversationPublicQueryApplicationService",
                service
        );

        Response<FeaturedConversationDetailRespVO> response = controller.detail("featured-demo-002");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertFalse(response.getData().getContentAvailable());
        Assert.assertEquals(
                "session_history_missing",
                response.getData().getContentUnavailableReason()
        );
        Assert.assertNull(response.getData().getHistoryDetail());
    }

    private static final class StubFeaturedConversationPublicQueryApplicationService
            extends FeaturedConversationPublicQueryApplicationService {

        private FeaturedConversationPublicDetail detail = FeaturedConversationPublicDetail.builder()
                .featuredId("featured-demo-001")
                .sessionId("session-demo-001")
                .title("精品案例")
                .summary("公开展示的会话")
                .tags(List.of("研究", "报告"))
                .coverUrl("https://file.example.com/cover.png")
                .status("ONLINE")
                .publishedAt(LocalDateTime.of(2026, 7, 6, 10, 0, 0))
                .contentLastActiveAt(LocalDateTime.of(2026, 7, 6, 11, 0, 0))
                .contentAvailable(true)
                .historyDetail(
                        ConversationHistoryDetail.builder()
                                .sessionId("session-demo-001")
                                .title("原会话")
                                .build()
                )
                .build();

        StubFeaturedConversationPublicQueryApplicationService() {
            super(null, null, null);
        }

        @Override
        public List<FeaturedConversationCardView> queryHomeCards(int limit) {
            return List.of(
                    FeaturedConversationCardView.builder()
                            .featuredId("featured-demo-001")
                            .sessionId("session-demo-001")
                            .title("精品案例")
                            .summary("公开展示的会话")
                            .tags(List.of("研究", "报告"))
                            .coverUrl("https://file.example.com/cover.png")
                            .publishedAt(LocalDateTime.of(2026, 7, 6, 10, 0, 0))
                            .contentLastActiveAt(LocalDateTime.of(2026, 7, 6, 11, 0, 0))
                            .build()
            );
        }

        @Override
        public FeaturedConversationPageResult<FeaturedConversationCardView> queryPublicList(
                int pageNo,
                int pageSize
        ) {
            return FeaturedConversationPageResult.<FeaturedConversationCardView>builder()
                    .total(1)
                    .list(queryHomeCards(pageSize))
                    .build();
        }

        @Override
        public FeaturedConversationPublicDetail queryDetail(String featuredId) {
            return detail;
        }
    }
}
