package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.wwz.ai.domain.agent.memory.CompactionBudget;
import org.wwz.ai.domain.agent.memory.CompactionPrompt;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.WorkingMemoryCompactionEvent;
import org.wwz.ai.domain.agent.memory.WorkingMemoryCompactor;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.llm.ContextTokenTracker;
import org.wwz.ai.domain.agent.runtime.llm.PromptShape;
import org.wwz.ai.domain.agent.runtime.llm.TokenCounter;
import org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryCompactionDao;
import org.wwz.ai.infrastructure.reactor.service.impl.SessionContextCompactionServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class SessionContextCompactionOutcomeTest {

    @Test
    public void structuredCheckpointAndFailureFallbackAreStable() {
        Assert.assertTrue(CompactionPrompt.getCompactPrompt().contains("## Historical Task Snapshot"));
        Assert.assertFalse(CompactionPrompt.getCompactPrompt().contains("<analysis>"));
        Assert.assertTrue(CompactionPrompt.getIterativeUpdatePrompt().contains("PREVIOUS SUMMARY"));
        Assert.assertTrue(CompactionPrompt.getIterativeUpdatePrompt().contains("NEW TURNS TO INCORPORATE"));
        Assert.assertTrue(CompactionPrompt.getCompactPrompt().contains("## Blocked"));
        Assert.assertFalse(CompactionPrompt.getIterativeUpdatePrompt().contains("{new_turns}"));
        String wrapped = CompactionPrompt.wrapSummaryForReinject("## Goal\nship it", true);
        Assert.assertTrue(wrapped.startsWith(CompactionPrompt.CONTEXT_COMPACTION_PREFIX));
        Assert.assertTrue(wrapped.contains(CompactionPrompt.END_MARKER));
        Assert.assertEquals("permanent",
                SessionContextCompactionServiceImpl.classifyFailure(new IllegalStateException("401 quota")));
        Assert.assertEquals("transient",
                SessionContextCompactionServiceImpl.classifyFailure(new IllegalStateException("timeout")));
        Assert.assertEquals("transient",
                SessionContextCompactionServiceImpl.classifyFailure(new TimeoutException("summarizer exceeded")));
        Assert.assertEquals(SessionContextCompactionServiceImpl.STRATEGY_FULL_LLM, "full-llm");
        Assert.assertEquals(SessionContextCompactionServiceImpl.STRATEGY_LOCAL_FALLBACK, "local-fallback");
        Assert.assertEquals(SessionContextCompactionServiceImpl.STRATEGY_ABORT_UNCHANGED, "abort-unchanged");
        Assert.assertEquals(SessionContextCompactionServiceImpl.STRATEGY_DROP_OLDEST, "drop-oldest");
    }

    @Test
    public void staticFallbackProducesCheckpoint() {
        WorkingMemoryCompactor compactor = new WorkingMemoryCompactor();
        String fallback = compactor.buildStaticFallbackSummary(
                List.of(Message.userMessage("old request", null)), null, "latest request");
        Assert.assertTrue(fallback.contains("## Historical Task Snapshot"));
        Assert.assertTrue(fallback.contains("## Active State"));
        Assert.assertFalse(fallback.isBlank());
        Assert.assertFalse(CompactionBudget.defaults().isSessionMemoryEnabled());
    }

    @Test
    public void applyIfNeededUsesFullLlmStrategy() {
        AtomicReference<WorkingMemoryCompactionEvent> audited = new AtomicReference<>();
        List<Message> input = oversizedMessages();
        String handoff = CompactionPrompt.wrapSummaryForReinject("## Goal\ndone\n## Active State\nok", true);
        List<Message> compactResult = List.of(
                Message.userMessage(handoff, null),
                Message.userMessage("latest", null),
                Message.assistantMessage("reply", null)
        );
        RecordingService service = new RecordingService(audited, compactResult, null);
        List<Message> out = service.applyIfNeeded("s1", "main", "req-1", input);
        Assert.assertTrue(out.stream().anyMatch(m -> m.getContent() != null
                && m.getContent().contains(CompactionPrompt.CONTEXT_COMPACTION_PREFIX)));
        Assert.assertEquals(SessionContextCompactionServiceImpl.STRATEGY_FULL_LLM, audited.get().getStrategy());
        Assert.assertEquals(WorkingMemoryCompactionEvent.STATUS_SUCCESS, audited.get().getStatus().intValue());
    }

    @Test
    public void applyIfNeededUsesLocalFallbackOnTransientFailure() {
        AtomicReference<WorkingMemoryCompactionEvent> audited = new AtomicReference<>();
        String handoff = CompactionPrompt.wrapSummaryForReinject("## Goal\nfallback", true);
        List<Message> smallFallback = List.of(
                Message.userMessage(handoff, null),
                Message.userMessage("latest", null));
        RecordingService service = new RecordingService(audited, null, new IllegalStateException("502 timeout"));
        service.fallbackResult = smallFallback;
        List<Message> out = service.applyIfNeeded("s1", "main", "req-2", oversizedMessages());
        Assert.assertTrue(out.stream().anyMatch(m -> m.getContent() != null
                && m.getContent().contains(CompactionPrompt.CONTEXT_COMPACTION_PREFIX)));
        Assert.assertEquals(SessionContextCompactionServiceImpl.STRATEGY_LOCAL_FALLBACK, audited.get().getStrategy());
    }

    @Test
    public void applyIfNeededAbortsUnchangedOnPermanentFailure() {
        AtomicReference<WorkingMemoryCompactionEvent> audited = new AtomicReference<>();
        List<Message> input = oversizedMessages();
        Message marker = input.get(0);
        RecordingService service = new RecordingService(audited, null, new IllegalStateException("401 insufficient_quota"));
        List<Message> out = service.applyIfNeeded("s1", "main", "req-3", input);
        Assert.assertSame(marker, out.get(0));
        Assert.assertEquals(input.size(), out.size());
        Assert.assertFalse(out.get(0).getContent().contains("[memory"));
        Assert.assertEquals(SessionContextCompactionServiceImpl.STRATEGY_ABORT_UNCHANGED, audited.get().getStrategy());
        Assert.assertEquals(WorkingMemoryCompactionEvent.STATUS_FAILED, audited.get().getStatus().intValue());
    }

    @Test
    public void applyIfNeededUsesDropOldestWhenCompactStillOverThreshold() {
        AtomicReference<WorkingMemoryCompactionEvent> audited = new AtomicReference<>();
        List<Message> stillHuge = oversizedMessages();
        RecordingService service = new RecordingService(audited, stillHuge, null);
        List<Message> out = service.applyIfNeeded("s1", "main", "req-4", oversizedMessages());
        Assert.assertTrue(out.size() < stillHuge.size());
        Assert.assertEquals("full-llm+drop-oldest", audited.get().getStrategy());
    }

    @Test
    public void dropOldestAfterFullLlmKeepsHandoff() {
        AtomicReference<WorkingMemoryCompactionEvent> audited = new AtomicReference<>();
        String handoff = CompactionPrompt.wrapSummaryForReinject("## Goal\nkeep-me\n## Active State\nok", true);
        List<Message> stillHuge = new ArrayList<>();
        stillHuge.add(Message.userMessage(handoff, null));
        stillHuge.addAll(oversizedMessages());
        RecordingService service = new RecordingService(audited, stillHuge, null);
        List<Message> out = service.applyIfNeeded("s1", "main", "req-5", oversizedMessages());
        Assert.assertTrue(out.stream().anyMatch(m -> m.getContent() != null
                && m.getContent().contains(CompactionPrompt.CONTEXT_COMPACTION_PREFIX)));
        Assert.assertEquals("full-llm+drop-oldest", audited.get().getStrategy());
    }

    @Test
    public void applyIfNeededUsesLocalFallbackWhenCircuitOpen() {
        AtomicReference<WorkingMemoryCompactionEvent> audited = new AtomicReference<>();
        RecordingService service = new RecordingService(audited, null, new IllegalStateException("network blip"));
        String handoff = CompactionPrompt.wrapSummaryForReinject("## Goal\ncircuit", true);
        service.fallbackResult = List.of(Message.userMessage(handoff, null), Message.userMessage("latest", null));
        List<Message> input = oversizedMessages();
        service.applyIfNeeded("s-circuit", "main", "r1", input);
        service.applyIfNeeded("s-circuit", "main", "r2", input);
        service.applyIfNeeded("s-circuit", "main", "r3", input);
        audited.set(null);
        service.failWith = null;
        service.compactResult = null;
        service.forceCircuit = true;
        List<Message> out = service.applyIfNeeded("s-circuit", "main", "r4", input);
        Assert.assertTrue(out.stream().anyMatch(m -> m.getContent() != null
                && m.getContent().contains(CompactionPrompt.CONTEXT_COMPACTION_PREFIX)));
        Assert.assertEquals(SessionContextCompactionServiceImpl.STRATEGY_LOCAL_FALLBACK, audited.get().getStrategy());
    }

    @Test
    public void firstStepCompactsWhenSystemPromptIsHugeAndHistoryIsSmall() {
        AtomicReference<WorkingMemoryCompactionEvent> audited = new AtomicReference<>();
        String handoff = CompactionPrompt.wrapSummaryForReinject("## Goal\nhuge-system\n## Active State\nok", true);
        List<Message> compactResult = List.of(
                Message.userMessage(handoff, null),
                Message.userMessage("hi", null)
        );
        RecordingService service = new RecordingService(audited, compactResult, null);
        List<Message> smallHistory = List.of(Message.userMessage("hi", null));
        PromptShape shape = PromptShape.functionCall(
                Message.systemMessage("S".repeat(8_000), null),
                smallHistory,
                null);
        List<Message> out = service.applyIfNeededMidRun(
                "s-huge-sys", "main", "req-sys", smallHistory, null, shape, null);
        Assert.assertTrue(out.stream().anyMatch(m -> m.getContent() != null
                && m.getContent().contains(CompactionPrompt.CONTEXT_COMPACTION_PREFIX)));
        Assert.assertEquals(SessionContextCompactionServiceImpl.STRATEGY_FULL_LLM, audited.get().getStrategy());
    }

    @Test
    public void midRunUsesProviderUsageSnapshotInsteadOfReestimatingHistory() {
        AtomicReference<WorkingMemoryCompactionEvent> audited = new AtomicReference<>();
        RecordingService service = new RecordingService(audited, oversizedMessages(), null);
        List<Message> huge = oversizedMessages();
        TokenCounter counter = new TokenCounter();
        PromptShape shape = PromptShape.functionCall(null, huge, null);
        ContextTokenTracker.Snapshot snapshot = ContextTokenTracker.Snapshot.builder()
                .usageAvailable(true)
                .lastProviderContextTokens(100)
                .anchorMessageCount(huge.size())
                .promptShapeFingerprint(counter.fingerprint(shape))
                .build();
        List<Message> out = service.applyIfNeededMidRun(
                "s-usage", "main", "req-usage", huge, null, shape, snapshot);
        Assert.assertEquals(huge.size(), out.size());
        Assert.assertNull(audited.get());
    }

    @Test
    public void midRunTokenDecisionDoesNotQueryWorkingMemory() {
        SessionWorkingMemoryService workingMemory = mock(SessionWorkingMemoryService.class);
        AtomicInteger daoWrites = new AtomicInteger();
        IWorkingMemoryCompactionDao dao = event -> {
            daoWrites.incrementAndGet();
            return 1;
        };
        SessionContextCompactionServiceImpl service = new SessionContextCompactionServiceImpl(
                RecordingService.nullProvider(),
                workingMemory,
                dao,
                true, true, false, false,
                0.50d, 20, 1, 200,
                3, 0.2d, 4000, 5, 8000,
                true, true, false, true);
        List<Message> small = List.of(Message.userMessage("hi", null));
        PromptShape shape = PromptShape.text(null, small);
        service.applyIfNeededMidRun("s-mem", "main", "req-mem", small, null, shape, null);
        verify(workingMemory, never()).loadReadyMessages(anyString(), anyString(), anyString());
        verify(workingMemory, never()).replaceReadyProjection(anyString(), anyString(), anyString(), any());
        Assert.assertEquals(0, daoWrites.get());
    }

    private static List<Message> oversizedMessages() {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            messages.add(Message.userMessage("old-user-" + i + " " + "x".repeat(400), null));
            messages.add(Message.assistantMessage("old-assistant-" + i + " " + "y".repeat(400), null));
        }
        messages.add(Message.userMessage("latest-user", null));
        messages.add(Message.assistantMessage("latest-assistant", null));
        return messages;
    }

    private static final class RecordingService extends SessionContextCompactionServiceImpl {
        private final AtomicReference<WorkingMemoryCompactionEvent> audited;
        private List<Message> compactResult;
        private List<Message> fallbackResult;
        private Exception failWith;
        private boolean forceCircuit;

        private RecordingService(AtomicReference<WorkingMemoryCompactionEvent> audited,
                                 List<Message> compactResult,
                                 Exception failWith) {
            super(nullProvider(), null, recordingDao(audited),
                    true, true, false, false,
                    0.50d, 20, 1, 200,
                    3, 0.2d, 4000, 5, 8000,
                    false, true, false, true);
            this.audited = audited;
            this.compactResult = compactResult;
            this.failWith = failWith;
        }

        @Override
        protected List<Message> localFallback(List<Message> messages, CompactionBudget budget) {
            if (fallbackResult != null) {
                return fallbackResult;
            }
            return super.localFallback(messages, budget);
        }

        @Override
        protected CompactionBudget resolveBudget() {
            return CompactionBudget.builder()
                    .enabled(true)
                    .llmEnabled(true)
                    .microEnabled(false)
                    .sessionMemoryEnabled(false)
                    .contextWindow(2_000)
                    .thresholdPercent(0.50d)
                    .keepMinTokens(40)
                    .keepMinTextMessages(1)
                    .keepMaxTokens(400)
                    .maxConsecutiveFailures(3)
                    .temperature(0.2d)
                    .messageContentCharLimit(4000)
                    .summaryTargetRatio(0.20d)
                    .protectFirstN(2)
                    .protectLastN(4)
                    .contentMaxChars(6000)
                    .contentHeadChars(4000)
                    .contentTailChars(1500)
                    .summaryInputMaxChars(160000)
                    .summarizerTimeoutSeconds(120)
                    .microKeepRecentToolResults(5)
                    .microToolResultMaxChars(8000)
                    .build();
        }

        @Override
        protected List<Message> fullCompact(String sessionId, String requestId, List<Message> messages, CompactionBudget budget)
                throws Exception {
            if (forceCircuit) {
                throw new IllegalStateException("should not call llm when circuit open");
            }
            if (failWith != null) {
                throw failWith;
            }
            return compactResult;
        }

        static ObjectProvider<ReactorRuntimeDependencies> nullProvider() {
            return new ObjectProvider<>() {
                @Override
                public ReactorRuntimeDependencies getObject() {
                    throw new IllegalStateException("runtime deps unavailable in unit test");
                }

                @Override
                public ReactorRuntimeDependencies getObject(Object... args) {
                    return getObject();
                }

                @Override
                public ReactorRuntimeDependencies getIfAvailable() {
                    return null;
                }

                @Override
                public ReactorRuntimeDependencies getIfUnique() {
                    return null;
                }
            };
        }

        private static IWorkingMemoryCompactionDao recordingDao(AtomicReference<WorkingMemoryCompactionEvent> audited) {
            return new IWorkingMemoryCompactionDao() {
                @Override
                public int insertEvent(WorkingMemoryCompactionEvent event) {
                    audited.set(event);
                    return 1;
                }
            };
        }
    }
}
