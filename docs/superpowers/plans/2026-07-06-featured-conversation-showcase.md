# Featured Conversation Showcase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add admin-managed featured conversation publishing with a public homepage section, public list page, and read-only public detail page that reuses the existing live conversation history replay chain.

**Architecture:** Keep publish metadata in a dedicated featured-conversation table and repository, orchestrate public/admin behavior in `Reactor-agent-case`, and reuse `ConversationHistoryReplayService` plus `hydrateConversationFromReplayFrames` for live read-only detail rendering. Public APIs remain separate from visitor-owned session APIs so owner-only semantics on `/api/agent/conversation/sessions/**` do not change.

**Tech Stack:** Java 17, Spring Boot 3.4.3, MyBatis Mapper XML, existing execution ledger/history replay services, React 19, TypeScript 5.7, React Router 7, Vitest.

---

## File Structure

### Backend

- Modify: `Reactor-agent-app/src/main/resources/db/schema.sql`
  - Add `ai_agent_featured_conversation`
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/entity/FeaturedConversation.java`
  - Domain entity for publish metadata
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationCardView.java`
  - Lightweight public card/list projection
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationPageResult.java`
  - Domain-side page wrapper to avoid leaking trigger VO types into case/domain
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationPublicDetail.java`
  - Public detail aggregate: publish head + history detail + content availability
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationAdminView.java`
  - Admin query projection
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationQueryCondition.java`
  - Admin list filtering condition
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationUpsertCommand.java`
  - Admin create/update command
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/IFeaturedConversationRepository.java`
  - Repository port
- Create: `Reactor-agent-case/src/main/java/org/wwz/ai/application/agent/featured/FeaturedConversationPublicQueryApplicationService.java`
  - Public homepage/list/detail orchestration
- Create: `Reactor-agent-case/src/main/java/org/wwz/ai/application/agent/featured/FeaturedConversationAdminApplicationService.java`
  - Admin create/update/online/offline/query orchestration
- Create: `Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/FeaturedConversationPO.java`
  - Persistence object
- Create: `Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/reactor/IFeaturedConversationDao.java`
  - MyBatis DAO
- Create: `Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/FeaturedConversationRepository.java`
  - Repository adapter
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentFeaturedConversationController.java`
  - Public controller
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/FeaturedConversationAdminController.java`
  - Admin controller
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/FeaturedConversationCardRespVO.java`
  - Public home/list item VO
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/FeaturedConversationDetailRespVO.java`
  - Public detail VO
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/vo/FeaturedConversationAdminUpsertReqVO.java`
  - Admin create/update request
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/vo/FeaturedConversationAdminQueryReqVO.java`
  - Admin query-list request
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/vo/FeaturedConversationAdminRespVO.java`
  - Admin item/detail VO
- Create: `Reactor-agent-app/src/main/resources/mybatis/mapper/featured_conversation_mapper.xml`
  - SQL mapping
- Create: `Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/FeaturedConversationPublicControllerTest.java`
  - Public API regression
- Create: `Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/FeaturedConversationAdminControllerTest.java`
  - Admin API regression
- Create: `Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/FeaturedConversationRepositoryTest.java`
  - Repository serialization and status update regression

### Frontend

- Modify: `ui/src/router/routes.ts`
  - Add featured list/detail routes
- Modify: `ui/src/router/index.tsx`
  - Register public featured pages
- Create: `ui/src/services/featuredConversation.ts`
  - Public featured-conversation API client
- Create: `ui/src/services/featuredConversation.test.ts`
  - Service contract tests
- Modify: `ui/src/pages/Home/index.tsx`
  - Fetch home featured cards and pass to `WelcomeView`
- Modify: `ui/src/pages/Home/WelcomeView.tsx`
  - Replace placeholder showcase with live featured cards
- Create: `ui/src/pages/Home/WelcomeView.test.tsx`
  - Home featured cards render test
- Modify: `ui/src/pages/Home/ConversationSidebar.tsx`
  - Add “精品对话” public navigation entry
- Create: `ui/src/pages/Home/ConversationSidebar.test.tsx`
  - Sidebar entry render test
- Create: `ui/src/pages/FeaturedConversations/index.tsx`
  - Data container for public list page
- Create: `ui/src/pages/FeaturedConversations/view.tsx`
  - Pure list-page view
- Create: `ui/src/pages/FeaturedConversations/view.test.tsx`
  - List-page render test
- Create: `ui/src/pages/FeaturedConversationDetail/index.tsx`
  - Data container for public detail page
- Create: `ui/src/pages/FeaturedConversationDetail/view.tsx`
  - Pure detail-page view + read-only transcript rendering
- Create: `ui/src/pages/FeaturedConversationDetail/view.test.tsx`
  - Detail-page render and fallback tests

---

### Task 1: Public Featured Backend Contract

**Files:**
- Create: `Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/FeaturedConversationPublicControllerTest.java`
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/entity/FeaturedConversation.java`
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationCardView.java`
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationPageResult.java`
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationPublicDetail.java`
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/IFeaturedConversationRepository.java`
- Create: `Reactor-agent-case/src/main/java/org/wwz/ai/application/agent/featured/FeaturedConversationPublicQueryApplicationService.java`
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentFeaturedConversationController.java`
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/FeaturedConversationCardRespVO.java`
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/FeaturedConversationDetailRespVO.java`

- [ ] **Step 1: Write the failing public controller regression test**

```java
package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.featured.FeaturedConversationPublicQueryApplicationService;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationCardView;
import org.wwz.ai.domain.agent.ledger.model.FeaturedConversationPublicDetail;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryDetail;
import org.wwz.ai.trigger.http.agent.AgentFeaturedConversationController;
import org.wwz.ai.trigger.http.agent.vo.FeaturedConversationCardRespVO;
import org.wwz.ai.trigger.http.agent.vo.FeaturedConversationDetailRespVO;
import org.wwz.ai.trigger.http.agent.vo.PageRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;

public class FeaturedConversationPublicControllerTest {

    @Test
    public void shouldReturnOnlineHomeCardsOnly() {
        StubFeaturedConversationPublicQueryApplicationService service = new StubFeaturedConversationPublicQueryApplicationService();
        AgentFeaturedConversationController controller = new AgentFeaturedConversationController();
        ReflectionTestUtils.setField(controller, "featuredConversationPublicQueryApplicationService", service);

        Response<List<FeaturedConversationCardRespVO>> response = controller.home(3);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals(1, response.getData().size());
        Assert.assertEquals("featured-demo-001", response.getData().get(0).getFeaturedId());
        Assert.assertNotNull(response.getData().get(0).getContentLastActiveAt());
    }

    @Test
    public void shouldReturnPagedPublicList() {
        StubFeaturedConversationPublicQueryApplicationService service = new StubFeaturedConversationPublicQueryApplicationService();
        AgentFeaturedConversationController controller = new AgentFeaturedConversationController();
        ReflectionTestUtils.setField(controller, "featuredConversationPublicQueryApplicationService", service);

        Response<PageRespVO<FeaturedConversationCardRespVO>> response = controller.list(1, 20);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals(1, response.getData().getTotal());
        Assert.assertEquals("featured-demo-001", response.getData().getList().get(0).getFeaturedId());
    }

    @Test
    public void shouldReturnReadableFallbackWhenLiveContentUnavailable() {
        StubFeaturedConversationPublicQueryApplicationService service = new StubFeaturedConversationPublicQueryApplicationService();
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
        ReflectionTestUtils.setField(controller, "featuredConversationPublicQueryApplicationService", service);

        Response<FeaturedConversationDetailRespVO> response = controller.detail("featured-demo-002");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertFalse(response.getData().getContentAvailable());
        Assert.assertEquals("session_history_missing", response.getData().getContentUnavailableReason());
        Assert.assertNull(response.getData().getHistoryDetail());
    }

    private static final class StubFeaturedConversationPublicQueryApplicationService extends FeaturedConversationPublicQueryApplicationService {
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
                .historyDetail(ConversationHistoryDetail.builder().sessionId("session-demo-001").title("原会话").build())
                .build();

        StubFeaturedConversationPublicQueryApplicationService() {
            super(null, null, null);
        }

        @Override
        public List<FeaturedConversationCardView> queryHomeCards(int limit) {
            return List.of(FeaturedConversationCardView.builder()
                    .featuredId("featured-demo-001")
                    .sessionId("session-demo-001")
                    .title("精品案例")
                    .summary("公开展示的会话")
                    .tags(List.of("研究", "报告"))
                    .coverUrl("https://file.example.com/cover.png")
                    .publishedAt(LocalDateTime.of(2026, 7, 6, 10, 0, 0))
                    .contentLastActiveAt(LocalDateTime.of(2026, 7, 6, 11, 0, 0))
                    .build());
        }

        @Override
        public FeaturedConversationPageResult<FeaturedConversationCardView> queryPublicList(int pageNo, int pageSize) {
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl Reactor-agent-app test -Dtest=FeaturedConversationPublicControllerTest -DskipTests=false`

Expected: FAIL with compilation errors like `package org.wwz.ai.application.agent.featured does not exist` and `cannot find symbol AgentFeaturedConversationController`.

- [ ] **Step 3: Add the shared domain model and repository port**

```java
// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/entity/FeaturedConversation.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversation {
    private Long id;
    private String featuredId;
    private String sessionId;
    private String title;
    private String summary;
    private String coverResourceKey;
    private String coverUrl;
    private List<String> tags;
    private Integer sortOrder;
    private String status;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}

// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationCardView.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationCardView {
    private String featuredId;
    private String sessionId;
    private String title;
    private String summary;
    private String coverUrl;
    private List<String> tags;
    private LocalDateTime publishedAt;
    private LocalDateTime contentLastActiveAt;
}

// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationPageResult.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationPageResult<T> {
    private int total;
    private List<T> list;
}

// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationPublicDetail.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationPublicDetail {
    private String featuredId;
    private String sessionId;
    private String title;
    private String summary;
    private String coverUrl;
    private List<String> tags;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime contentLastActiveAt;
    private boolean contentAvailable;
    private String contentUnavailableReason;
    private ConversationHistoryDetail historyDetail;
}

// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/IFeaturedConversationRepository.java
public interface IFeaturedConversationRepository {
    FeaturedConversation queryByFeaturedId(String featuredId);
    List<FeaturedConversation> queryOnlineList(int offset, int limit);
    int countOnline();
}
```

- [ ] **Step 4: Implement the public application service, controller, and response VOs**

```java
// Reactor-agent-case/src/main/java/org/wwz/ai/application/agent/featured/FeaturedConversationPublicQueryApplicationService.java
@Service
@RequiredArgsConstructor
public class FeaturedConversationPublicQueryApplicationService {

    private final IFeaturedConversationRepository featuredConversationRepository;
    private final ExecutionLedgerQueryService executionLedgerQueryService;
    private final ConversationHistoryReplayService conversationHistoryReplayService;

    public List<FeaturedConversationCardView> queryHomeCards(int limit) {
        return featuredConversationRepository.queryOnlineList(0, Math.max(1, limit)).stream()
                .map(this::toCardView)
                .toList();
    }

    public FeaturedConversationPageResult<FeaturedConversationCardView> queryPublicList(int pageNo, int pageSize) {
        int normalizedPageNo = Math.max(1, pageNo);
        int normalizedPageSize = Math.max(1, pageSize);
        int offset = (normalizedPageNo - 1) * normalizedPageSize;
        return FeaturedConversationPageResult.<FeaturedConversationCardView>builder()
                .total(featuredConversationRepository.countOnline())
                .list(featuredConversationRepository.queryOnlineList(offset, normalizedPageSize).stream()
                        .map(this::toCardView)
                        .toList())
                .build();
    }

    public FeaturedConversationPublicDetail queryDetail(String featuredId) {
        FeaturedConversation featured = featuredConversationRepository.queryByFeaturedId(featuredId);
        if (featured == null || !"ONLINE".equalsIgnoreCase(featured.getStatus())) {
            return null;
        }
        ConversationHistoryDetail historyDetail = conversationHistoryReplayService.queryConversationHistory(featured.getSessionId());
        DialogueSessionView session = executionLedgerQueryService.querySession(featured.getSessionId());
        return FeaturedConversationPublicDetail.builder()
                .featuredId(featured.getFeaturedId())
                .sessionId(featured.getSessionId())
                .title(featured.getTitle())
                .summary(featured.getSummary())
                .coverUrl(featured.getCoverUrl())
                .tags(featured.getTags())
                .status(featured.getStatus())
                .publishedAt(featured.getPublishedAt())
                .contentLastActiveAt(session == null ? null : session.getLastActiveAt())
                .contentAvailable(historyDetail != null)
                .contentUnavailableReason(historyDetail == null ? "session_history_missing" : null)
                .historyDetail(historyDetail)
                .build();
    }

    private FeaturedConversationCardView toCardView(FeaturedConversation featured) {
        DialogueSessionView session = executionLedgerQueryService.querySession(featured.getSessionId());
        return FeaturedConversationCardView.builder()
                .featuredId(featured.getFeaturedId())
                .sessionId(featured.getSessionId())
                .title(featured.getTitle())
                .summary(featured.getSummary())
                .coverUrl(featured.getCoverUrl())
                .tags(featured.getTags())
                .publishedAt(featured.getPublishedAt())
                .contentLastActiveAt(session == null ? null : session.getLastActiveAt())
                .build();
    }
}

// Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentFeaturedConversationController.java
@RestController
@RequestMapping("/api/agent/featured-conversations")
public class AgentFeaturedConversationController {

    @Resource
    private FeaturedConversationPublicQueryApplicationService featuredConversationPublicQueryApplicationService;

    @GetMapping("/home")
    public Response<List<FeaturedConversationCardRespVO>> home(@RequestParam(name = "limit", defaultValue = "6") Integer limit) {
        List<FeaturedConversationCardRespVO> cards = featuredConversationPublicQueryApplicationService.queryHomeCards(limit == null ? 6 : limit)
                .stream().map(this::toCardRespVO).toList();
        return Response.<List<FeaturedConversationCardRespVO>>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo()).data(cards).build();
    }

    @GetMapping
    public Response<PageRespVO<FeaturedConversationCardRespVO>> list(@RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                                     @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize) {
        FeaturedConversationPageResult<FeaturedConversationCardView> page = featuredConversationPublicQueryApplicationService.queryPublicList(pageNo, pageSize);
        return Response.<PageRespVO<FeaturedConversationCardRespVO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(PageRespVO.<FeaturedConversationCardRespVO>builder()
                        .total(page.getTotal())
                        .list(page.getList().stream().map(this::toCardRespVO).toList())
                        .build())
                .build();
    }

    @GetMapping("/{featuredId}")
    public Response<FeaturedConversationDetailRespVO> detail(@PathVariable("featuredId") String featuredId) {
        FeaturedConversationPublicDetail detail = featuredConversationPublicQueryApplicationService.queryDetail(featuredId);
        return Response.<FeaturedConversationDetailRespVO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(toDetailRespVO(detail))
                .build();
    }

    private FeaturedConversationCardRespVO toCardRespVO(FeaturedConversationCardView card) {
        if (card == null) {
            return null;
        }
        return FeaturedConversationCardRespVO.builder()
                .featuredId(card.getFeaturedId())
                .title(card.getTitle())
                .summary(card.getSummary())
                .coverUrl(card.getCoverUrl())
                .tags(card.getTags() == null ? List.of() : card.getTags())
                .publishedAt(card.getPublishedAt())
                .contentLastActiveAt(card.getContentLastActiveAt())
                .build();
    }

    private FeaturedConversationDetailRespVO toDetailRespVO(FeaturedConversationPublicDetail detail) {
        if (detail == null) {
            return null;
        }
        return FeaturedConversationDetailRespVO.builder()
                .featuredId(detail.getFeaturedId())
                .sessionId(detail.getSessionId())
                .title(detail.getTitle())
                .summary(detail.getSummary())
                .coverUrl(detail.getCoverUrl())
                .tags(detail.getTags() == null ? List.of() : detail.getTags())
                .publishedAt(detail.getPublishedAt())
                .contentLastActiveAt(detail.getContentLastActiveAt())
                .contentAvailable(detail.isContentAvailable())
                .contentUnavailableReason(detail.getContentUnavailableReason())
                .historyDetail(detail.getHistoryDetail())
                .build();
    }
}
```

- [ ] **Step 5: Run the public controller test to verify it passes**

Run: `mvn -pl Reactor-agent-app test -Dtest=FeaturedConversationPublicControllerTest -DskipTests=false`

Expected: PASS with `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/FeaturedConversationPublicControllerTest.java \
        Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/entity/FeaturedConversation.java \
        Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationCardView.java \
        Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationPageResult.java \
        Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationPublicDetail.java \
        Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/IFeaturedConversationRepository.java \
        Reactor-agent-case/src/main/java/org/wwz/ai/application/agent/featured/FeaturedConversationPublicQueryApplicationService.java \
        Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentFeaturedConversationController.java \
        Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/FeaturedConversationCardRespVO.java \
        Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/FeaturedConversationDetailRespVO.java
git commit -m "feat: add public featured conversation query flow"
```

### Task 2: Admin Featured Backend Contract

**Files:**
- Create: `Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/FeaturedConversationAdminControllerTest.java`
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationAdminView.java`
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationQueryCondition.java`
- Create: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationUpsertCommand.java`
- Modify: `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/IFeaturedConversationRepository.java`
- Create: `Reactor-agent-case/src/main/java/org/wwz/ai/application/agent/featured/FeaturedConversationAdminApplicationService.java`
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/FeaturedConversationAdminController.java`
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/vo/FeaturedConversationAdminUpsertReqVO.java`
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/vo/FeaturedConversationAdminQueryReqVO.java`
- Create: `Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/vo/FeaturedConversationAdminRespVO.java`

- [ ] **Step 1: Write the failing admin controller regression test**

```java
public class FeaturedConversationAdminControllerTest {

    @Test
    public void shouldCreateFeaturedConversationFromExistingSession() {
        StubFeaturedConversationAdminApplicationService service = new StubFeaturedConversationAdminApplicationService();
        FeaturedConversationAdminController controller = new FeaturedConversationAdminController();
        ReflectionTestUtils.setField(controller, "featuredConversationAdminApplicationService", service);

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
        StubFeaturedConversationAdminApplicationService service = new StubFeaturedConversationAdminApplicationService();
        FeaturedConversationAdminController controller = new FeaturedConversationAdminController();
        ReflectionTestUtils.setField(controller, "featuredConversationAdminApplicationService", service);

        Response<Boolean> response = controller.offline("featured-admin-001", "admin");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals("OFFLINE", service.lastStatus);
    }

    @Test
    public void shouldQueryAdminListWithStatusFilter() {
        StubFeaturedConversationAdminApplicationService service = new StubFeaturedConversationAdminApplicationService();
        FeaturedConversationAdminController controller = new FeaturedConversationAdminController();
        ReflectionTestUtils.setField(controller, "featuredConversationAdminApplicationService", service);

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
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl Reactor-agent-app test -Dtest=FeaturedConversationAdminControllerTest -DskipTests=false`

Expected: FAIL with compilation errors like `cannot find symbol FeaturedConversationAdminController`.

- [ ] **Step 3: Extend the repository port and add admin commands/views**

```java
// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationQueryCondition.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationQueryCondition {
    private String status;
    private String sessionId;
    private String title;
    private int offset;
    private int limit;
}

// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationUpsertCommand.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationUpsertCommand {
    private String featuredId;
    private String sessionId;
    private String title;
    private String summary;
    private String coverResourceKey;
    private String coverUrl;
    private List<String> tags;
    private Integer sortOrder;
    private String operator;
}

// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationAdminView.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationAdminView {
    private String featuredId;
    private String sessionId;
    private String title;
    private String summary;
    private List<String> tags;
    private String coverUrl;
    private Integer sortOrder;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}

// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/IFeaturedConversationRepository.java
FeaturedConversation queryBySessionId(String sessionId);
FeaturedConversationPageResult<FeaturedConversationAdminView> queryAdminList(FeaturedConversationQueryCondition condition);
boolean upsert(FeaturedConversationUpsertCommand command);
boolean updateStatus(String featuredId, String status, String operator);
```

- [ ] **Step 4: Implement the admin application service and admin controller**

```java
// Reactor-agent-case/src/main/java/org/wwz/ai/application/agent/featured/FeaturedConversationAdminApplicationService.java
@Service
@RequiredArgsConstructor
public class FeaturedConversationAdminApplicationService {

    private final IFeaturedConversationRepository featuredConversationRepository;
    private final ExecutionLedgerQueryService executionLedgerQueryService;

    public boolean create(FeaturedConversationUpsertCommand command) {
        if (executionLedgerQueryService.querySession(command.getSessionId()) == null) {
            throw new IllegalArgumentException("sessionId 对应会话不存在");
        }
        return featuredConversationRepository.upsert(FeaturedConversationUpsertCommand.builder()
                .featuredId("featured_" + command.getSessionId())
                .sessionId(command.getSessionId())
                .title(command.getTitle())
                .summary(command.getSummary())
                .coverResourceKey(command.getCoverResourceKey())
                .coverUrl(command.getCoverUrl())
                .tags(command.getTags())
                .sortOrder(command.getSortOrder())
                .operator(command.getOperator())
                .build());
    }

    public boolean update(FeaturedConversationUpsertCommand command) {
        if (featuredConversationRepository.queryByFeaturedId(command.getFeaturedId()) == null) {
            throw new IllegalArgumentException("featuredId 不存在");
        }
        return featuredConversationRepository.upsert(command);
    }

    public boolean online(String featuredId, String operator) {
        return featuredConversationRepository.updateStatus(featuredId, "ONLINE", operator);
    }

    public boolean offline(String featuredId, String operator) {
        return featuredConversationRepository.updateStatus(featuredId, "OFFLINE", operator);
    }

    public FeaturedConversationPageResult<FeaturedConversationAdminView> queryList(FeaturedConversationQueryCondition condition) {
        return featuredConversationRepository.queryAdminList(condition);
    }
}

// Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/FeaturedConversationAdminController.java
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
    public Response<Boolean> online(@PathVariable("featuredId") String featuredId,
                                    @RequestParam("operator") String operator) {
        return success(featuredConversationAdminApplicationService.online(featuredId, operator));
    }

    @PostMapping("/offline/{featuredId}")
    public Response<Boolean> offline(@PathVariable("featuredId") String featuredId,
                                     @RequestParam("operator") String operator) {
        return success(featuredConversationAdminApplicationService.offline(featuredId, operator));
    }

    @PostMapping("/query-list")
    public Response<PageRespVO<FeaturedConversationAdminRespVO>> queryList(@RequestBody FeaturedConversationAdminQueryReqVO request) {
        FeaturedConversationPageResult<FeaturedConversationAdminView> page = featuredConversationAdminApplicationService.queryList(
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
                        .total(page.getTotal())
                        .list(page.getList().stream().map(this::toRespVO).toList())
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
```

- [ ] **Step 5: Run the admin controller test to verify it passes**

Run: `mvn -pl Reactor-agent-app test -Dtest=FeaturedConversationAdminControllerTest -DskipTests=false`

Expected: PASS with `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/FeaturedConversationAdminControllerTest.java \
        Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationAdminView.java \
        Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationQueryCondition.java \
        Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/FeaturedConversationUpsertCommand.java \
        Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/ledger/IFeaturedConversationRepository.java \
        Reactor-agent-case/src/main/java/org/wwz/ai/application/agent/featured/FeaturedConversationAdminApplicationService.java \
        Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/FeaturedConversationAdminController.java \
        Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/vo/FeaturedConversationAdminUpsertReqVO.java \
        Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/vo/FeaturedConversationAdminQueryReqVO.java \
        Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/vo/FeaturedConversationAdminRespVO.java
git commit -m "feat: add admin featured conversation flow"
```

### Task 3: Featured Conversation Persistence Wiring

**Files:**
- Create: `Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/FeaturedConversationRepositoryTest.java`
- Modify: `Reactor-agent-app/src/main/resources/db/schema.sql`
- Create: `Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/FeaturedConversationPO.java`
- Create: `Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/reactor/IFeaturedConversationDao.java`
- Create: `Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/FeaturedConversationRepository.java`
- Create: `Reactor-agent-app/src/main/resources/mybatis/mapper/featured_conversation_mapper.xml`

- [ ] **Step 1: Write a failing repository adapter test around tag serialization and status updates**

```java
public class FeaturedConversationRepositoryTest {

    @Test
    public void shouldSerializeTagsWhenUpsertingAndRestoreThemWhenQuerying() {
        InMemoryFeaturedConversationDao dao = new InMemoryFeaturedConversationDao();
        FeaturedConversationRepository repository = new FeaturedConversationRepository(dao);

        repository.upsert(FeaturedConversationUpsertCommand.builder()
                .featuredId("featured-storage-001")
                .sessionId("session-storage-001")
                .title("精品案例")
                .summary("摘要")
                .tags(List.of("研究", "报告"))
                .sortOrder(10)
                .operator("admin")
                .build());

        FeaturedConversation conversation = repository.queryByFeaturedId("featured-storage-001");

        Assert.assertEquals(List.of("研究", "报告"), conversation.getTags());
        Assert.assertEquals("OFFLINE", conversation.getStatus());
    }

    @Test
    public void shouldSwitchStatusWithoutMutatingSessionBinding() {
        InMemoryFeaturedConversationDao dao = new InMemoryFeaturedConversationDao();
        FeaturedConversationRepository repository = new FeaturedConversationRepository(dao);
        dao.seed("featured-storage-002", "session-storage-002", "[\"写作\"]", "ONLINE");

        repository.updateStatus("featured-storage-002", "OFFLINE", "admin");

        FeaturedConversation conversation = repository.queryByFeaturedId("featured-storage-002");
        Assert.assertEquals("OFFLINE", conversation.getStatus());
        Assert.assertEquals("session-storage-002", conversation.getSessionId());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl Reactor-agent-app test -Dtest=FeaturedConversationRepositoryTest -DskipTests=false`

Expected: FAIL with compilation errors like `cannot find symbol FeaturedConversationRepository`.

- [ ] **Step 3: Add the schema, PO, DAO, and repository adapter**

```sql
-- Reactor-agent-app/src/main/resources/db/schema.sql
CREATE TABLE IF NOT EXISTS ai_agent_featured_conversation (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    featured_id        VARCHAR(64)  NOT NULL COMMENT '公共精品ID',
    session_id         VARCHAR(64)  NOT NULL COMMENT '原会话ID',
    title              VARCHAR(256) NOT NULL COMMENT '展示标题',
    summary            TEXT         NULL COMMENT '展示摘要',
    cover_resource_key VARCHAR(512) NULL COMMENT '封面资源key',
    cover_url          VARCHAR(1024) NULL COMMENT '封面预览地址',
    tags_json          JSON         NULL COMMENT '标签数组',
    sort_order         INT          NOT NULL DEFAULT 0 COMMENT '排序值',
    status             VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE',
    published_by       VARCHAR(64)  NULL COMMENT '发布人',
    published_at       DATETIME(3)  NULL COMMENT '发布时间',
    updated_by         VARCHAR(64)  NULL COMMENT '最后更新人',
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_featured_conversation_featured_id (featured_id),
    UNIQUE KEY uk_featured_conversation_session_id (session_id),
    KEY idx_featured_conversation_status_sort (status, deleted, sort_order DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='精品会话发布表';
```

```java
// Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/FeaturedConversationPO.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationPO {
    private Long id;
    private String featuredId;
    private String sessionId;
    private String title;
    private String summary;
    private String coverResourceKey;
    private String coverUrl;
    private String tagsJson;
    private Integer sortOrder;
    private String status;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private Integer deleted;
}

// Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/reactor/IFeaturedConversationDao.java
@Mapper
public interface IFeaturedConversationDao {
    int upsert(FeaturedConversationPO po);
    FeaturedConversationPO queryByFeaturedId(@Param("featuredId") String featuredId);
    FeaturedConversationPO queryBySessionId(@Param("sessionId") String sessionId);
    List<FeaturedConversationPO> queryOnlineList(@Param("offset") int offset, @Param("limit") int limit);
    Integer countOnline();
    int updateStatus(@Param("featuredId") String featuredId,
                     @Param("status") String status,
                     @Param("updatedBy") String updatedBy,
                     @Param("publishedAt") LocalDateTime publishedAt,
                     @Param("updatedAt") LocalDateTime updatedAt);
    List<FeaturedConversationPO> queryAdminList(FeaturedConversationQueryCondition condition);
    Integer countAdminList(FeaturedConversationQueryCondition condition);
}

// Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/FeaturedConversationRepository.java
@Repository
@RequiredArgsConstructor
public class FeaturedConversationRepository implements IFeaturedConversationRepository {
    private static final TypeReference<List<String>> TAGS_TYPE = new TypeReference<>() {};
    private final IFeaturedConversationDao featuredConversationDao;

    @Override
    public boolean upsert(FeaturedConversationUpsertCommand command) {
        LocalDateTime now = LocalDateTime.now();
        FeaturedConversationPO existing = featuredConversationDao.queryByFeaturedId(command.getFeaturedId());
        return featuredConversationDao.upsert(FeaturedConversationPO.builder()
                .featuredId(command.getFeaturedId())
                .sessionId(command.getSessionId())
                .title(command.getTitle())
                .summary(command.getSummary())
                .coverResourceKey(command.getCoverResourceKey())
                .coverUrl(command.getCoverUrl())
                .tagsJson(JSON.toJSONString(command.getTags()))
                .sortOrder(command.getSortOrder())
                .status(existing == null ? "OFFLINE" : existing.getStatus())
                .publishedBy(existing == null ? command.getOperator() : existing.getPublishedBy())
                .publishedAt(existing == null ? null : existing.getPublishedAt())
                .updatedBy(command.getOperator())
                .updatedAt(now)
                .build()) > 0;
    }
}
```

- [ ] **Step 4: Add the mapper XML**

```xml
<!-- Reactor-agent-app/src/main/resources/mybatis/mapper/featured_conversation_mapper.xml -->
<mapper namespace="org.wwz.ai.infrastructure.dao.reactor.IFeaturedConversationDao">

    <resultMap id="featuredConversationResultMap" type="org.wwz.ai.infrastructure.dao.po.FeaturedConversationPO">
        <id property="id" column="id"/>
        <result property="featuredId" column="featured_id"/>
        <result property="sessionId" column="session_id"/>
        <result property="title" column="title"/>
        <result property="summary" column="summary"/>
        <result property="coverResourceKey" column="cover_resource_key"/>
        <result property="coverUrl" column="cover_url"/>
        <result property="tagsJson" column="tags_json"/>
        <result property="sortOrder" column="sort_order"/>
        <result property="status" column="status"/>
        <result property="publishedBy" column="published_by"/>
        <result property="publishedAt" column="published_at"/>
        <result property="updatedBy" column="updated_by"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <insert id="upsert">
        INSERT INTO ai_agent_featured_conversation (
            featured_id, session_id, title, summary, cover_resource_key, cover_url,
            tags_json, sort_order, status, published_by, published_at, updated_by, updated_at, deleted
        ) VALUES (
            #{featuredId}, #{sessionId}, #{title}, #{summary}, #{coverResourceKey}, #{coverUrl},
            #{tagsJson}, #{sortOrder}, #{status}, #{publishedBy}, #{publishedAt}, #{updatedBy}, #{updatedAt}, 0
        )
        ON DUPLICATE KEY UPDATE
            title = VALUES(title),
            summary = VALUES(summary),
            cover_resource_key = VALUES(cover_resource_key),
            cover_url = VALUES(cover_url),
            tags_json = VALUES(tags_json),
            sort_order = VALUES(sort_order),
            updated_by = VALUES(updated_by),
            updated_at = VALUES(updated_at),
            deleted = 0
    </insert>
</mapper>
```

- [ ] **Step 5: Run repository + controller tests and then compile the app**

Run:

```bash
mvn -pl Reactor-agent-app test -Dtest=FeaturedConversationRepositoryTest,FeaturedConversationPublicControllerTest,FeaturedConversationAdminControllerTest -DskipTests=false
mvn -pl Reactor-agent-app -am compile -DskipTests
```

Expected:

1. All featured-conversation tests PASS
2. Compile succeeds without mapper namespace or missing-bean errors

- [ ] **Step 6: Commit**

```bash
git add Reactor-agent-app/src/main/resources/db/schema.sql \
        Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/FeaturedConversationRepositoryTest.java \
        Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/FeaturedConversationPO.java \
        Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/reactor/IFeaturedConversationDao.java \
        Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/FeaturedConversationRepository.java \
        Reactor-agent-app/src/main/resources/mybatis/mapper/featured_conversation_mapper.xml
git commit -m "feat: persist featured conversation metadata"
```

### Task 4: Frontend Featured Service and Routing

**Files:**
- Modify: `ui/src/router/routes.ts`
- Modify: `ui/src/router/index.tsx`
- Create: `ui/src/services/featuredConversation.ts`
- Create: `ui/src/services/featuredConversation.test.ts`

- [ ] **Step 1: Write the failing frontend service test**

```typescript
import { describe, expect, it, vi } from "vitest";

const getMock = vi.fn();

vi.mock("./index", () => ({
  default: {
    get: getMock,
  },
}));

describe("featuredConversation service", () => {
  it("requests home cards from the dedicated public endpoint", async () => {
    getMock.mockResolvedValueOnce([]);
    const { featuredConversationApi } = await import("./featuredConversation");

    await featuredConversationApi.listHome(6);

    expect(getMock).toHaveBeenCalledWith("/api/agent/featured-conversations/home", {
      limit: 6,
    });
  });

  it("requests list page with pageNo and pageSize", async () => {
    getMock.mockResolvedValueOnce({ total: 0, list: [] });
    const { featuredConversationApi } = await import("./featuredConversation");

    await featuredConversationApi.list({ pageNo: 2, pageSize: 12 });

    expect(getMock).toHaveBeenCalledWith("/api/agent/featured-conversations", {
      pageNo: 2,
      pageSize: 12,
    });
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd ui && pnpm test -- src/services/featuredConversation.test.ts`

Expected: FAIL with `Failed to resolve import "./featuredConversation"`.

- [ ] **Step 3: Implement the service and route constants**

```typescript
// ui/src/services/featuredConversation.ts
import api from "./index";
import type { ConversationHistoryDetail } from "./agentConversation";

export interface FeaturedConversationCard {
  featuredId: string;
  title: string;
  summary: string;
  coverUrl?: string;
  tags: string[];
  publishedAt?: string;
  contentLastActiveAt?: string;
}

export interface FeaturedConversationDetail extends FeaturedConversationCard {
  sessionId: string;
  contentAvailable: boolean;
  contentUnavailableReason?: string;
  historyDetail: ConversationHistoryDetail | null;
}

export interface FeaturedConversationPage {
  total: number;
  list: FeaturedConversationCard[];
}

export const featuredConversationApi = {
  listHome: (limit = 6) =>
    api.get<FeaturedConversationCard[]>("/api/agent/featured-conversations/home", { limit }) as unknown as Promise<FeaturedConversationCard[]>,
  list: (params: { pageNo: number; pageSize: number }) =>
    api.get<FeaturedConversationPage>("/api/agent/featured-conversations", params) as unknown as Promise<FeaturedConversationPage>,
  detail: (featuredId: string) =>
    api.get<FeaturedConversationDetail>(`/api/agent/featured-conversations/${featuredId}`) as unknown as Promise<FeaturedConversationDetail>,
};

// ui/src/router/routes.ts
export const ROUTES = {
  HOME: "/",
  FEATURED_CONVERSATIONS: "/featured-conversations",
  FEATURED_CONVERSATION_DETAIL: "/featured-conversations/:featuredId",
  WORKSPACE: "/workspace",
  WORKSPACE_MRAG: "/workspace/mrag",
  WORKSPACE_IMAGE_GENERATION: "/workspace/image-generation",
  NOT_FOUND: "*",
} as const;
```

- [ ] **Step 4: Register the new list/detail routes**

```tsx
// ui/src/router/index.tsx
const FeaturedConversations = React.lazy(() => import("@/pages/FeaturedConversations"));
const FeaturedConversationDetail = React.lazy(() => import("@/pages/FeaturedConversationDetail"));

{
  path: ROUTES.FEATURED_CONVERSATIONS,
  element: (
    <Suspense fallback={<Loading loading={true} className="h-full" />}>
      <FeaturedConversations />
    </Suspense>
  ),
},
{
  path: ROUTES.FEATURED_CONVERSATION_DETAIL,
  element: (
    <Suspense fallback={<Loading loading={true} className="h-full" />}>
      <FeaturedConversationDetail />
    </Suspense>
  ),
},
```

- [ ] **Step 5: Run the service test and a full frontend type build**

Run:

```bash
cd ui
pnpm test -- src/services/featuredConversation.test.ts
pnpm build
```

Expected:

1. `featuredConversation.test.ts` PASS
2. TypeScript build succeeds with the new routes and service types

- [ ] **Step 6: Commit**

```bash
git add ui/src/router/routes.ts \
        ui/src/router/index.tsx \
        ui/src/services/featuredConversation.ts \
        ui/src/services/featuredConversation.test.ts
git commit -m "feat: add featured conversation frontend service and routes"
```

### Task 5: Homepage Cards, Sidebar Entry, and Public List Page

**Files:**
- Modify: `ui/src/pages/Home/index.tsx`
- Modify: `ui/src/pages/Home/WelcomeView.tsx`
- Create: `ui/src/pages/Home/WelcomeView.test.tsx`
- Modify: `ui/src/pages/Home/ConversationSidebar.tsx`
- Create: `ui/src/pages/Home/ConversationSidebar.test.tsx`
- Create: `ui/src/pages/FeaturedConversations/index.tsx`
- Create: `ui/src/pages/FeaturedConversations/view.tsx`
- Create: `ui/src/pages/FeaturedConversations/view.test.tsx`

- [ ] **Step 1: Write the failing presentational tests**

```tsx
// ui/src/pages/Home/WelcomeView.test.tsx
describe("WelcomeView featured cards", () => {
  it("renders featured section and view-all link when cards are provided", () => {
    const baseProps = {
      currentConversation: {
        id: "conversation-1",
        sessionId: "session-1",
        title: "新对话",
        productType: "chat",
        deepThink: false,
        role: null,
        createdAt: 0,
        updatedAt: 0,
        chatTitle: "",
        chatList: [],
        dataChatList: [],
      } as unknown as CHAT.ConversationHistory,
      product: {
        type: "chat",
        name: "聊天",
        placeholder: "请输入问题",
        img: "icon-chat",
        color: "text-[#4040FF]",
      } as unknown as CHAT.Product,
      displayOutput: {
        type: "chat",
        name: "聊天",
        placeholder: "请输入问题",
        img: "icon-chat",
        color: "text-[#4040FF]",
      } as unknown as CHAT.Product,
      currentConversationRole: null,
      fixRoles: [],
      visitorUsername: "visitor",
      videoModalOpen: undefined,
      onSelectionChange: () => {},
      onRoleSelect: () => {},
      onSend: () => {},
      onSendQuestion: () => {},
      onOpenVideo: () => {},
      onCloseVideo: () => {},
    };
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <WelcomeView
          featuredCards={[
            {
              featuredId: "featured-home-001",
              title: "精品案例",
              summary: "公开展示的会话",
              coverUrl: "https://file.example.com/cover.png",
              tags: ["研究"],
              publishedAt: "2026-07-06T10:00:00",
              contentLastActiveAt: "2026-07-06T11:00:00",
            },
          ]}
          {...baseProps}
        />
      </MemoryRouter>
    );

    expect(html).toContain("精品对话");
    expect(html).toContain("查看全部");
    expect(html).toContain("精品案例");
  });
});

// ui/src/pages/Home/ConversationSidebar.test.tsx
describe("ConversationSidebar", () => {
  it("renders the featured conversations navigation entry", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ConversationSidebar
          activeView="chat"
          recentSessions={[]}
          recentSessionsLoading={false}
          onNewChat={() => {}}
          onSelectSession={() => {}}
          onChangeView={() => {}}
          onOpenFeaturedConversations={() => {}}
        />
      </MemoryRouter>
    );

    expect(html).toContain("精品对话");
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
cd ui
pnpm test -- src/pages/Home/WelcomeView.test.tsx src/pages/Home/ConversationSidebar.test.tsx
```

Expected: FAIL because `featuredCards` and `onOpenFeaturedConversations` props do not exist yet.

- [ ] **Step 3: Implement homepage featured cards and sidebar entry**

```tsx
// ui/src/pages/Home/index.tsx
const [featuredCards, setFeaturedCards] = useState<FeaturedConversationCard[]>([]);

useEffect(() => {
  featuredConversationApi
    .listHome(6)
    .then((cards) => setFeaturedCards(cards || []))
    .catch((error) => {
      console.error("加载精品对话失败", error);
      setFeaturedCards([]);
    });
}, []);

<ConversationSidebar
  activeView={activeView}
  recentSessions={displayedRecentSessions}
  recentSessionsLoading={recentSessionsLoading}
  selectedSessionId={currentConversation.sessionId}
  visitorUsername={visitorBootstrap?.username}
  onNewChat={createNewChat}
  onSelectSession={handleSelectRecentSession}
  onChangeView={setActiveView}
  onOpenFeaturedConversations={() => navigate(ROUTES.FEATURED_CONVERSATIONS)}
/>

<WelcomeView
  currentConversation={currentConversation}
  product={product}
  displayOutput={displayOutput}
  currentConversationRole={currentConversationRole}
  fixRoles={fixRoles}
  visitorUsername={visitorBootstrap?.username}
  videoModalOpen={videoModalOpen}
  onSelectionChange={handleInputSelectionChange}
  onRoleSelect={handleRoleSelect}
  onSend={changeInputInfo}
  onSendQuestion={toSendMessage}
  onOpenVideo={setVideoModalOpen}
  onCloseVideo={() => setVideoModalOpen(undefined)}
  featuredCards={featuredCards}
/>

// ui/src/pages/Home/WelcomeView.tsx
{props.featuredCards.length > 0 && (
  <div className="mx-auto mt-8 w-full max-w-[1120px] pb-20">
    <div className="mb-6 flex items-center justify-between">
      <div>
        <h2 className="text-[22px] font-medium text-[var(--chat-text)]">精品对话</h2>
        <p className="mt-1 text-[13px] text-[var(--chat-text-soft)]">查看管理员精选的公开案例</p>
      </div>
      <Link to={ROUTES.FEATURED_CONVERSATIONS} className="text-[13px] font-medium text-[var(--primary)]">
        查看全部
      </Link>
    </div>
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {props.featuredCards.map((card) => (
        <Link key={card.featuredId} to={`/featured-conversations/${card.featuredId}`} className="rounded-[20px] border border-[var(--chat-border)] bg-[var(--chat-surface)] p-4">
          <div className="line-clamp-1 text-[15px] font-medium text-[var(--chat-text)]">{card.title}</div>
          <div className="mt-2 line-clamp-2 text-[13px] text-[var(--chat-text-soft)]">{card.summary}</div>
        </Link>
      ))}
    </div>
  </div>
)}

// ui/src/pages/Home/ConversationSidebar.tsx
type ConversationSidebarProps = {
  activeView: SidebarView;
  recentSessions: ConversationSessionItem[];
  recentSessionsLoading: boolean;
  selectedSessionId?: string;
  visitorUsername?: string;
  onNewChat: () => void;
  onSelectSession: (session: ConversationSessionItem) => void;
  onChangeView: (view: SidebarView) => void;
  onOpenFeaturedConversations: () => void;
};

<button
  type="button"
  onClick={onOpenFeaturedConversations}
  className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-[13px] text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-soft)]/50 hover:text-[var(--chat-text)]"
>
  <Star className="h-4 w-4" />
  <span>精品对话</span>
</button>
```

- [ ] **Step 4: Add the public list page**

```tsx
// ui/src/pages/FeaturedConversations/index.tsx
export default function FeaturedConversationsPage() {
  const [pageNo, setPageNo] = useState(1);
  const [page, setPage] = useState<FeaturedConversationPage>({ total: 0, list: [] });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    featuredConversationApi
      .list({ pageNo, pageSize: 20 })
      .then((data) => setPage(data))
      .finally(() => setLoading(false));
  }, [pageNo]);

  return <FeaturedConversationsView page={page} loading={loading} pageNo={pageNo} onPageChange={setPageNo} />;
}

// ui/src/pages/FeaturedConversations/view.tsx
export function FeaturedConversationsView(props: {
  page: FeaturedConversationPage;
  loading: boolean;
  pageNo: number;
  onPageChange: (pageNo: number) => void;
}) {
  return (
    <div className="mx-auto w-full max-w-[1120px] px-6 py-8">
      <div className="mb-6">
        <h1 className="text-[28px] font-medium text-[var(--chat-text)]">精品对话</h1>
        <p className="mt-2 text-[14px] text-[var(--chat-text-soft)]">浏览管理员公开的案例会话</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {props.page.list.map((card) => (
          <Link key={card.featuredId} to={`/featured-conversations/${card.featuredId}`} className="rounded-[20px] border border-[var(--chat-border)] bg-[var(--chat-surface)] p-4">
            <div className="line-clamp-1 text-[15px] font-medium text-[var(--chat-text)]">{card.title}</div>
            <div className="mt-2 line-clamp-2 text-[13px] text-[var(--chat-text-soft)]">{card.summary}</div>
          </Link>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Run the new frontend tests and lint**

Run:

```bash
cd ui
pnpm test -- src/pages/Home/WelcomeView.test.tsx src/pages/Home/ConversationSidebar.test.tsx src/pages/FeaturedConversations/view.test.tsx
pnpm lint
```

Expected:

1. Three render tests PASS
2. ESLint passes with the new props and route links

- [ ] **Step 6: Commit**

```bash
git add ui/src/pages/Home/index.tsx \
        ui/src/pages/Home/WelcomeView.tsx \
        ui/src/pages/Home/WelcomeView.test.tsx \
        ui/src/pages/Home/ConversationSidebar.tsx \
        ui/src/pages/Home/ConversationSidebar.test.tsx \
        ui/src/pages/FeaturedConversations/index.tsx \
        ui/src/pages/FeaturedConversations/view.tsx \
        ui/src/pages/FeaturedConversations/view.test.tsx
git commit -m "feat: add featured conversation entry and public list page"
```

### Task 6: Read-Only Featured Detail Page

**Files:**
- Create: `ui/src/pages/FeaturedConversationDetail/index.tsx`
- Create: `ui/src/pages/FeaturedConversationDetail/view.tsx`
- Create: `ui/src/pages/FeaturedConversationDetail/view.test.tsx`

- [ ] **Step 1: Write the failing detail-page render test**

```tsx
describe("FeaturedConversationDetailView", () => {
  it("renders published time, content last active time, and read-only transcript", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <FeaturedConversationDetailView
          loading={false}
          detail={{
            featuredId: "featured-detail-001",
            sessionId: "session-detail-001",
            title: "精品详情",
            summary: "只读案例",
            tags: ["研究"],
            coverUrl: "",
            publishedAt: "2026-07-06T10:00:00",
            contentLastActiveAt: "2026-07-06T11:00:00",
            contentAvailable: true,
            contentUnavailableReason: "",
            historyDetail: {
              sessionId: "session-detail-001",
              title: "原会话",
              status: "SUCCESS",
              outputStyle: "chat",
              deepThink: false,
              role: null,
              runCount: 1,
              finishedRunCount: 1,
              failedRunCount: 0,
              startedAt: "2026-07-06T10:00:00",
              lastActiveAt: "2026-07-06T11:00:00",
              runs: [],
            },
          }}
        />
      </MemoryRouter>
    );

    expect(html).toContain("发布时间");
    expect(html).toContain("内容最近更新");
    expect(html).not.toContain("希望 Reactor 为你做哪些任务呢");
  });

  it("renders a readable fallback when live content is unavailable", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <FeaturedConversationDetailView
          loading={false}
          detail={{
            featuredId: "featured-detail-002",
            sessionId: "session-detail-002",
            title: "异常案例",
            summary: "正文暂不可用",
            tags: [],
            coverUrl: "",
            publishedAt: "2026-07-06T10:00:00",
            contentLastActiveAt: "",
            contentAvailable: false,
            contentUnavailableReason: "session_history_missing",
            historyDetail: null,
          }}
        />
      </MemoryRouter>
    );

    expect(html).toContain("正文暂不可用");
    expect(html).toContain("session_history_missing");
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd ui && pnpm test -- src/pages/FeaturedConversationDetail/view.test.tsx`

Expected: FAIL with `Failed to resolve import "@/pages/FeaturedConversationDetail/view"`.

- [ ] **Step 3: Implement the detail container and read-only view**

```tsx
// ui/src/pages/FeaturedConversationDetail/index.tsx
export default function FeaturedConversationDetailPage() {
  const { featuredId = "" } = useParams();
  const [detail, setDetail] = useState<FeaturedConversationDetail | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!featuredId) {
      return;
    }
    setLoading(true);
    featuredConversationApi
      .detail(featuredId)
      .then((data) => setDetail(data))
      .finally(() => setLoading(false));
  }, [featuredId]);

  return <FeaturedConversationDetailView loading={loading} detail={detail} />;
}

// ui/src/pages/FeaturedConversationDetail/view.tsx
export function FeaturedConversationDetailView(props: {
  loading: boolean;
  detail: FeaturedConversationDetail | null;
}) {
  const conversation = props.detail?.historyDetail
    ? hydrateConversationFromReplayFrames(props.detail.historyDetail)
    : null;

  return (
    <div className="mx-auto w-full max-w-[980px] px-6 py-8">
      {props.detail ? (
        <>
          <div className="mb-8 border-b border-[var(--chat-border)] pb-6">
            <h1 className="text-[28px] font-medium text-[var(--chat-text)]">{props.detail.title}</h1>
            <p className="mt-3 text-[14px] text-[var(--chat-text-soft)]">{props.detail.summary}</p>
            <div className="mt-4 flex flex-wrap gap-4 text-[12px] text-[var(--chat-text-muted)]">
              <span>发布时间：{props.detail.publishedAt || "-"}</span>
              <span>内容最近更新：{props.detail.contentLastActiveAt || "-"}</span>
            </div>
          </div>

          {!props.detail.contentAvailable || !conversation ? (
            <div className="rounded-[20px] border border-[var(--chat-border)] bg-[var(--chat-surface)] p-6">
              <div className="text-[16px] font-medium text-[var(--chat-text)]">正文暂不可用</div>
              <div className="mt-2 text-[13px] text-[var(--chat-text-soft)]">{props.detail.contentUnavailableReason}</div>
            </div>
          ) : conversation.productType === "dataAgent" && !conversation.deepThink ? (
            conversation.dataChatList.map((chat, index) => <DataDialogue key={`${conversation.id}-${index}`} chat={chat} />)
          ) : (
            conversation.chatList.map((chat) => (
              <Dialogue
                key={chat.requestId}
                chat={chat}
                streamingThought=""
                deepThink={conversation.deepThink}
                changeTask={() => {}}
                changeFile={() => {}}
                changePlan={() => {}}
                onRegenerate={() => {}}
              />
            ))
          )}
        </>
      ) : null}
    </div>
  );
}
```

- [ ] **Step 4: Run the detail-page test and a targeted replay utility regression**

Run:

```bash
cd ui
pnpm test -- src/pages/FeaturedConversationDetail/view.test.tsx src/utils/conversationHistory.test.ts
```

Expected:

1. `FeaturedConversationDetail` tests PASS
2. Existing history hydration tests still PASS, proving the detail page reuses the same replay model safely

- [ ] **Step 5: Final verification**

Run:

```bash
mvn -pl Reactor-agent-app test -Dtest=FeaturedConversationPublicControllerTest,FeaturedConversationAdminControllerTest -DskipTests=false
cd ui && pnpm test -- src/services/featuredConversation.test.ts src/pages/Home/WelcomeView.test.tsx src/pages/Home/ConversationSidebar.test.tsx src/pages/FeaturedConversations/view.test.tsx src/pages/FeaturedConversationDetail/view.test.tsx
cd ui && pnpm build
```

Expected:

1. Backend featured-conversation tests PASS
2. Frontend featured-conversation tests PASS
3. Frontend production build succeeds

- [ ] **Step 6: Commit**

```bash
git add ui/src/pages/FeaturedConversationDetail/index.tsx \
        ui/src/pages/FeaturedConversationDetail/view.tsx \
        ui/src/pages/FeaturedConversationDetail/view.test.tsx
git commit -m "feat: add read-only featured conversation detail page"
```
