package org.wwz.ai.test.domain;

import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.application.agent.featured.FeaturedConversationPublicQueryApplicationService;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.IFeaturedConversationRepository;
import org.wwz.ai.domain.agent.ledger.entity.FeaturedConversation;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.replay.ConversationHistoryReplayService;

/**
 * 精品 replay 归属与发布状态测试。
 */
public class FeaturedConversationPublicQueryApplicationServiceTest {

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectRunFromAnotherFeaturedSessionBeforeProjector() {
        IFeaturedConversationRepository featuredRepository = Mockito.mock(IFeaturedConversationRepository.class);
        ExecutionLedgerQueryService ledgerQueryService = Mockito.mock(ExecutionLedgerQueryService.class);
        ConversationHistoryReplayService replayService = Mockito.mock(ConversationHistoryReplayService.class);
        FeaturedConversationPublicQueryApplicationService service =
                new FeaturedConversationPublicQueryApplicationService(
                        featuredRepository,
                        ledgerQueryService,
                        replayService
                );

        Mockito.when(featuredRepository.queryByFeaturedId("featured-001"))
                .thenReturn(FeaturedConversation.builder()
                        .featuredId("featured-001")
                        .sessionId("session-001")
                        .status("ONLINE")
                        .build());
        Mockito.when(ledgerQueryService.queryRunSummary("request-002"))
                .thenReturn(DialogueRunView.builder()
                        .requestId("request-002")
                        .sessionId("session-002")
                        .build());

        service.queryRunReplay("featured-001", "request-002");

        Mockito.verify(replayService, Mockito.never()).queryRunReplay("request-002");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectOfflineFeaturedReplayBeforeRunLookup() {
        IFeaturedConversationRepository featuredRepository = Mockito.mock(IFeaturedConversationRepository.class);
        ExecutionLedgerQueryService ledgerQueryService = Mockito.mock(ExecutionLedgerQueryService.class);
        ConversationHistoryReplayService replayService = Mockito.mock(ConversationHistoryReplayService.class);
        FeaturedConversationPublicQueryApplicationService service =
                new FeaturedConversationPublicQueryApplicationService(
                        featuredRepository,
                        ledgerQueryService,
                        replayService
                );

        Mockito.when(featuredRepository.queryByFeaturedId("featured-offline"))
                .thenReturn(FeaturedConversation.builder()
                        .featuredId("featured-offline")
                        .sessionId("session-001")
                        .status("OFFLINE")
                        .build());

        service.queryRunReplay("featured-offline", "request-001");

        Mockito.verify(ledgerQueryService, Mockito.never()).queryRunSummary("request-001");
        Mockito.verify(replayService, Mockito.never()).queryRunReplay("request-001");
    }
}
