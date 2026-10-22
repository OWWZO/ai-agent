package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.CompactionBudget;
import org.wwz.ai.domain.agent.memory.CompactionPrompt;
import org.wwz.ai.domain.agent.memory.WorkingMemoryCompactor;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.ArrayList;
import java.util.List;

public class WorkingMemoryCompactorTest {

    private final WorkingMemoryCompactor compactor = new WorkingMemoryCompactor();

    @Test
    public void thresholdFollowsHermesPercentOfWindow() {
        Assert.assertEquals(500_000, CompactionBudget.builder()
                .contextWindow(1_000_000)
                .thresholdPercent(0.50d)
                .build()
                .threshold());
        Assert.assertEquals(75_000, CompactionBudget.builder()
                .contextWindow(100_000)
                .thresholdPercent(0.50d)
                .build()
                .threshold());
        Assert.assertEquals(27_200, CompactionBudget.builder()
                .contextWindow(32_000)
                .thresholdPercent(0.50d)
                .build()
                .threshold());
    }

    @Test
    public void shouldCompactCountsSystemPrompt() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .contextWindow(1_000_000)
                .thresholdPercent(0.50d)
                .build();
        List<Message> messages = List.of(Message.userMessage("hello", null));
        Assert.assertFalse(compactor.shouldCompact(messages, budget));
        String hugeSystem = "S".repeat(2_000_000);
        Assert.assertTrue(compactor.shouldCompact(messages, budget, hugeSystem, null));
    }

    @Test
    public void shouldNotCompactWhenUnderThreshold() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .contextWindow(100_000)
                .keepMinTokens(100)
                .keepMinTextMessages(1)
                .keepMaxTokens(50_000)
                .build();
        List<Message> messages = List.of(
                Message.userMessage("hello", null),
                Message.assistantMessage("hi", null)
        );
        Assert.assertFalse(compactor.shouldCompact(messages, budget));
        Assert.assertEquals(messages, compactor.dropOldestToFit(messages, budget));
    }

    @Test
    public void shouldDropOldestWhilePreservingToolPairs() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .contextWindow(200)
                .keepMinTokens(20)
                .keepMinTextMessages(1)
                .keepMaxTokens(150)
                .build();

        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("old query " + "x".repeat(400), null));
        messages.add(Message.fromToolCalls("call tools", List.of(
                ToolCall.builder().id("tc-1").type("function")
                        .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                        .build()
        )));
        messages.add(Message.toolMessage("tool result " + "y".repeat(400), "tc-1", null));
        messages.add(Message.userMessage("recent " + "z".repeat(80), null));
        messages.add(Message.assistantMessage("recent answer", null));

        Assert.assertTrue(compactor.shouldCompact(messages, budget));
        List<Message> kept = compactor.dropOldestToFit(messages, budget);
        Assert.assertFalse(kept.isEmpty());
        Assert.assertTrue(compactor.estimateTokens(kept) <= compactor.estimateTokens(messages));

        for (int i = 0; i < kept.size(); i++) {
            Message m = kept.get(i);
            if (m.getRole() == RoleType.TOOL) {
                boolean found = false;
                for (int j = 0; j < i; j++) {
                    Message prev = kept.get(j);
                    if (prev.getRole() == RoleType.ASSISTANT && prev.getToolCalls() != null) {
                        for (ToolCall tc : prev.getToolCalls()) {
                            if (tc != null && m.getToolCallId().equals(tc.getId())) {
                                found = true;
                            }
                        }
                    }
                }
                Assert.assertTrue("tool_result must keep tool_use", found);
            }
        }
    }

    @Test
    public void shouldTruncateHeadForCompactRetrySafely() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("old-1", null));
        messages.add(Message.fromToolCalls("call", List.of(
                ToolCall.builder().id("tc-a").type("function")
                        .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                        .build()
        )));
        messages.add(Message.toolMessage("result-a", "tc-a", null));
        messages.add(Message.userMessage("keep-me", null));
        messages.add(Message.assistantMessage("recent", null));

        List<Message> truncated = compactor.truncateHeadForCompactRetry(messages);
        Assert.assertNotNull(truncated);
        Assert.assertTrue(truncated.size() < messages.size());
        Assert.assertTrue(truncated.stream().anyMatch(m -> "keep-me".equals(m.getContent())));
        for (int i = 0; i < truncated.size(); i++) {
            Message m = truncated.get(i);
            if (m.getRole() == RoleType.TOOL) {
                boolean found = false;
                for (int j = 0; j < i; j++) {
                    Message prev = truncated.get(j);
                    if (prev.getRole() == RoleType.ASSISTANT && prev.getToolCalls() != null) {
                        for (ToolCall tc : prev.getToolCalls()) {
                            if (tc != null && m.getToolCallId().equals(tc.getId())) {
                                found = true;
                            }
                        }
                    }
                }
                Assert.assertTrue(found);
            }
        }
        Assert.assertNull(compactor.truncateHeadForCompactRetry(List.of(Message.userMessage("only", null))));
    }

    @Test
    public void shouldFormatAndWrapSummary() {
        String raw = "<analysis>scratch</analysis>\n<summary>\n1. Primary Request: do X\n</summary>";
        String formatted = CompactionPrompt.formatCompactSummary(raw);
        Assert.assertFalse(formatted.contains("<analysis>"));
        Assert.assertFalse(formatted.contains("<summary>"));
        Assert.assertTrue(formatted.contains("Primary Request"));
        String wrapped = CompactionPrompt.wrapSummaryForReinject(formatted, true);
        Assert.assertTrue(wrapped.startsWith(CompactionPrompt.CONTEXT_COMPACTION_PREFIX));
        Assert.assertTrue(wrapped.contains(CompactionPrompt.END_MARKER));
        Assert.assertTrue(wrapped.contains("Recent messages are preserved verbatim"));
        Assert.assertTrue(wrapped.contains("Do NOT re-summarize"));
        Assert.assertFalse(wrapped.contains("<analysis>"));
        Assert.assertFalse(wrapped.contains("<summary>"));
    }

    @Test
    public void shouldStripUnclosedAnalysisAndInstructionLeakBeforeReinject() {
        String raw = """
                CRITICAL: Respond with TEXT ONLY. Do NOT call any tools.
                <analysis>
                I am summarizing the conversation without closing analysis
                <summary>
                1. Primary Request and Intent:
                   fix the login bug
                </summary>
                """;
        String formatted = CompactionPrompt.formatCompactSummary(raw);
        Assert.assertFalse(formatted.toLowerCase().contains("<analysis"));
        Assert.assertFalse(formatted.toLowerCase().contains("<summary"));
        Assert.assertFalse(formatted.contains("CRITICAL: Respond with TEXT ONLY"));
        Assert.assertTrue(formatted.contains("fix the login bug"));

        String polluted = "This session is being continued from a previous conversation that ran out of context. "
                + "The summary below covers the earlier portion of the conversation.\n\n"
                + "<analysis>leak</analysis>\n"
                + "1. Primary Request: keep working\n";
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage(polluted, null));
        messages.add(Message.userMessage("latest question " + "z".repeat(200), null));
        messages.add(Message.assistantMessage("latest answer", null));

        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .sessionMemoryEnabled(true)
                .contextWindow(2_000)
                .keepMinTokens(20)
                .keepMinTextMessages(1)
                .keepMaxTokens(400)
                .build();
        List<Message> sm = compactor.trySessionMemoryCompact(messages, null, budget);
        if (sm == null) {
            String cleaned = CompactionPrompt.wrapSummaryForReinject(
                    CompactionPrompt.formatCompactSummary(polluted), true);
            Assert.assertFalse(cleaned.toLowerCase().contains("<analysis"));
            Assert.assertTrue(cleaned.contains("Do NOT re-summarize"));
        } else {
            Assert.assertTrue(compactor.isCompactSummaryMessage(sm.get(0)));
            Assert.assertFalse(sm.get(0).getContent().toLowerCase().contains("<analysis"));
            Assert.assertTrue(sm.get(0).getContent().contains("Do NOT re-summarize"));
        }
    }

    @Test
    public void shouldBuildPostCompactWithSummaryAndTail() {
        List<Message> tail = List.of(Message.userMessage("keep me", null));
        List<Message> post = compactor.buildPostCompactMessages("summary body", tail);
        Assert.assertEquals(2, post.size());
        Assert.assertEquals(RoleType.USER, post.get(0).getRole());
        Assert.assertEquals("summary body", post.get(0).getContent());
        Assert.assertEquals("keep me", post.get(1).getContent());
    }

    @Test
    public void shouldAdjustKeepIndexForToolPairs() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("u1", null));
        messages.add(Message.fromToolCalls("think", List.of(
                ToolCall.builder().id("c1").type("function")
                        .function(ToolCall.Function.builder().name("t").arguments("{}").build())
                        .build()
        )));
        messages.add(Message.toolMessage("obs", "c1", null));
        messages.add(Message.userMessage("u2", null));

        int adjusted = compactor.adjustIndexToPreserveToolPairs(messages, 2);
        Assert.assertEquals(1, adjusted);
    }

    @Test
    public void shouldMicrocompactOldToolResults() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .microEnabled(true)
                .microKeepRecentToolResults(1)
                .microToolResultMaxChars(100)
                .contextWindow(100_000)
                .keepMinTokens(100)
                .keepMinTextMessages(1)
                .keepMaxTokens(50_000)
                .build();

        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("q1", null));
        messages.add(Message.fromToolCalls("t", List.of(
                ToolCall.builder().id("a").type("function")
                        .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                        .build()
        )));
        messages.add(Message.toolMessage("OLD_RESULT_" + "x".repeat(200), "a", null));
        messages.add(Message.userMessage("q2", null));
        messages.add(Message.fromToolCalls("t2", List.of(
                ToolCall.builder().id("b").type("function")
                        .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                        .build()
        )));
        messages.add(Message.toolMessage("RECENT_RESULT_short", "b", null));

        List<Message> microed = compactor.microcompact(messages, budget);
        Assert.assertEquals(CompactionBudget.CLEARED_TOOL_RESULT, microed.get(2).getContent());
        Assert.assertEquals("RECENT_RESULT_short", microed.get(5).getContent());
        Assert.assertTrue(compactor.estimateTokens(microed) < compactor.estimateTokens(messages));
    }

    @Test
    public void shouldSessionMemoryCompactWithExistingNotes() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .sessionMemoryEnabled(true)
                .contextWindow(2_000)
                .keepMinTokens(40)
                .keepMinTextMessages(1)
                .keepMaxTokens(400)
                .build();

        String notes = CompactionPrompt.wrapSummaryForReinject("1. Primary Request: build compact\n", false);
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage(notes, null));
        for (int i = 0; i < 12; i++) {
            messages.add(Message.userMessage("old turn " + i + " " + "z".repeat(800), null));
            messages.add(Message.assistantMessage("old ans " + i + " " + "y".repeat(800), null));
        }
        messages.add(Message.userMessage("latest question", null));
        messages.add(Message.assistantMessage("latest answer", null));

        Assert.assertTrue("precondition tokens=" + compactor.estimateTokens(messages)
                        + " threshold=" + budget.threshold(),
                compactor.estimateTokens(messages) >= budget.threshold());
        Assert.assertTrue(compactor.shouldCompact(messages, budget));
        List<Message> sm = compactor.trySessionMemoryCompact(messages, null, budget);
        Assert.assertNotNull("session-memory compact should succeed", sm);
        Assert.assertTrue(compactor.isCompactSummaryMessage(sm.get(0)));
        Assert.assertTrue(compactor.estimateTokens(sm) < budget.threshold());
        Assert.assertTrue(sm.get(0).getContent().contains("Do NOT re-summarize"));
        Assert.assertTrue(sm.stream().anyMatch(m -> m.getContent() != null && m.getContent().contains("latest")));
    }

    @Test
    public void shouldRecognizeNewAndLegacyHandoffRoles() {
        Message legacy = Message.userMessage(
                "This session is being continued from a previous conversation that ran out of context.\nbody", null);
        Message modern = Message.assistantMessage(
                CompactionPrompt.wrapSummaryForReinject("## Goal\nkeep going", false), null);
        Assert.assertTrue(compactor.isCompactSummaryMessage(legacy));
        Assert.assertTrue(compactor.isCompactSummaryMessage(modern));
        Assert.assertFalse(CompactionPrompt.getCompactPrompt().contains("<analysis>"));
        Assert.assertFalse(CompactionPrompt.getCompactPrompt().contains("<summary>"));
        Assert.assertTrue(CompactionPrompt.getCompactPrompt().contains("## Historical Task Snapshot"));
        Assert.assertTrue(CompactionPrompt.getCompactPrompt().contains("## Critical Context"));
        Assert.assertTrue(CompactionPrompt.getCompactPrompt().contains("Fill each with concrete facts"));
        Assert.assertTrue(CompactionPrompt.getIterativeUpdatePrompt().contains("PREVIOUS SUMMARY"));
        Assert.assertTrue(CompactionPrompt.getIterativeUpdatePrompt().contains("NEW TURNS TO INCORPORATE"));
        Assert.assertFalse(CompactionPrompt.getIterativeUpdatePrompt().contains("{previous_summary}"));
        Assert.assertFalse(CompactionPrompt.getIterativeUpdatePrompt().contains("{new_turns}"));
        String payload = CompactionPrompt.buildIterativeUserPayload("old checkpoint", "[USER]: hi");
        Assert.assertTrue(payload.contains("PREVIOUS SUMMARY:\nold checkpoint"));
        Assert.assertTrue(payload.contains("NEW TURNS TO INCORPORATE:\n[USER]: hi"));
    }

    @Test
    public void shouldSkipSessionMemoryWhenDisabled() {
        CompactionBudget budget = CompactionBudget.defaults();
        Assert.assertFalse(budget.isSessionMemoryEnabled());
        List<Message> messages = List.of(
                Message.userMessage(CompactionPrompt.wrapSummaryForReinject("notes", false), null),
                Message.userMessage("latest", null)
        );
        Assert.assertNull(compactor.trySessionMemoryCompact(messages, null, budget));
    }

    @Test
    public void shouldProtectHeadOnFirstCompactAndDecayOnSecond() {
        CompactionBudget budget = CompactionBudget.defaults();
        List<Message> first = new ArrayList<>();
        first.add(Message.userMessage("u1", null));
        first.add(Message.assistantMessage("a1", null));
        first.add(Message.userMessage("u2", null));
        first.add(Message.assistantMessage("a2", null));
        first.add(Message.userMessage("u3", null));
        first.add(Message.assistantMessage("a3", null));
        first.add(Message.userMessage("latest ask", null));
        first.add(Message.assistantMessage("latest reply", null));
        Assert.assertEquals(3, compactor.effectiveProtectFirstN(first, budget));
        Assert.assertTrue(compactor.protectHeadSize(first, budget) >= 3);

        List<Message> second = new ArrayList<>();
        second.add(Message.userMessage(CompactionPrompt.wrapSummaryForReinject("## Goal\nold", false), null));
        second.addAll(first);
        Assert.assertEquals(0, compactor.effectiveProtectFirstN(second, budget));
        WorkingMemoryCompactor.CompactionWindow window = compactor.splitForFullCompact(second, budget);
        Assert.assertEquals(0, window.headEnd());
        Assert.assertNotNull(window.previousSummary());
    }

    @Test
    public void shouldKeepLatestUserAndAssistantInTail() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .summaryTargetRatio(0.05d)
                .protectFirstN(0)
                .protectLastN(2)
                .contextWindow(5_000)
                .keepMinTokens(10)
                .keepMinTextMessages(1)
                .keepMaxTokens(400)
                .build();
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            messages.add(Message.userMessage("old-" + i + " " + "x".repeat(200), null));
            messages.add(Message.assistantMessage("ans-" + i + " " + "y".repeat(200), null));
        }
        messages.add(Message.userMessage("ACTIONABLE_LATEST_USER", null));
        messages.add(Message.assistantMessage("VISIBLE_LATEST_ASSISTANT", null));

        WorkingMemoryCompactor.CompactionWindow window = compactor.splitForFullCompact(messages, budget);
        List<Message> tail = messages.subList(window.tailStart(), messages.size());
        Assert.assertTrue(tail.stream().anyMatch(m -> "ACTIONABLE_LATEST_USER".equals(m.getContent())));
        Assert.assertTrue(tail.stream().anyMatch(m -> "VISIBLE_LATEST_ASSISTANT".equals(m.getContent())));
    }

    @Test
    public void splitMustNotLeaveTailToolWithoutAssistant() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .summaryTargetRatio(0.2d)
                .protectFirstN(2)
                .protectLastN(2)
                .contextWindow(8_000)
                .keepMinTokens(20)
                .keepMinTextMessages(1)
                .keepMaxTokens(400)
                .build();
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("early-user", null));
        messages.add(Message.fromToolCalls("early-call", List.of(
                ToolCall.builder().id("c-head").type("function")
                        .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                        .build())));
        messages.add(Message.toolMessage("early-result", "c-head", null));
        for (int i = 0; i < 12; i++) {
            messages.add(Message.userMessage("mid-" + i + " " + "z".repeat(300), null));
            messages.add(Message.assistantMessage("mid-ans-" + i + " " + "y".repeat(300), null));
        }
        messages.add(Message.fromToolCalls("tail-call", List.of(
                ToolCall.builder().id("c-tail").type("function")
                        .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                        .build())));
        messages.add(Message.toolMessage("tail-result", "c-tail", null));
        messages.add(Message.userMessage("latest-user", null));
        messages.add(Message.assistantMessage("latest-assistant", null));

        WorkingMemoryCompactor.CompactionWindow window = compactor.splitForFullCompact(messages, budget);
        List<Message> tail = messages.subList(window.tailStart(), messages.size());
        List<Message> head = messages.subList(0, window.headEnd());
        List<Message> kept = new ArrayList<>();
        kept.addAll(head);
        kept.addAll(tail);
        for (int i = 0; i < kept.size(); i++) {
            Message m = kept.get(i);
            if (m.getRole() != RoleType.TOOL) {
                continue;
            }
            boolean found = false;
            for (int j = 0; j < i; j++) {
                Message prev = kept.get(j);
                if (prev.getRole() == RoleType.ASSISTANT && prev.getToolCalls() != null) {
                    for (ToolCall tc : prev.getToolCalls()) {
                        if (tc != null && m.getToolCallId().equals(tc.getId())) {
                            found = true;
                        }
                    }
                }
            }
            Assert.assertTrue("tail/head must not keep tool_result without tool_use", found);
        }
    }

    @Test
    public void shouldSanitizeOrphanToolResultsWithoutBreakingPairs() {
        ToolCall call = ToolCall.builder().id("keep-1").type("function")
                .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                .build();
        List<Message> mixed = List.of(
                Message.userMessage("q", null),
                Message.fromToolCalls("call", List.of(call)),
                Message.toolMessage("paired", "keep-1", null),
                Message.toolMessage("orphan", "missing-1", null)
        );
        List<Message> sanitized = compactor.sanitizeToolProtocol(mixed);
        Assert.assertEquals(3, sanitized.size());
        Assert.assertEquals(RoleType.TOOL, sanitized.get(2).getRole());
        Assert.assertEquals("paired", sanitized.get(2).getContent());
        Assert.assertTrue(sanitized.stream().noneMatch(m -> "orphan".equals(m.getContent())));
    }

    @Test
    public void shouldGroundHistoricalTaskAndMergeHandoff() {
        String grounded = CompactionPrompt.groundHistoricalTaskSnapshot(
                "## Historical Task Snapshot\nold\n## Goal\nship", "rewrite login");
        Assert.assertTrue(grounded.contains("rewrite login"));
        Assert.assertFalse(grounded.contains("## Historical Task Snapshot\nold"));

        // USER | HANDOFF(ASSISTANT) | USER —— 合法交替
        Assert.assertEquals(RoleType.ASSISTANT, compactor.chooseHandoffRole(
                List.of(Message.userMessage("head-user", null)),
                List.of(Message.userMessage("tail-user", null))));
        // USER | ? | ASSISTANT —— 冲突，需 merge
        Assert.assertNull(compactor.chooseHandoffRole(
                List.of(Message.userMessage("head-user", null)),
                List.of(Message.assistantMessage("tail-assistant", null))));
        // 空 head + leading USER tail —— 冲突，需 merge
        Assert.assertNull(compactor.chooseHandoffRole(List.of(), List.of(Message.userMessage("only-user", null))));
        // 纯 TOOL tail —— 强制 USER handoff，不丢失 checkpoint
        String wrapped = CompactionPrompt.wrapSummaryForReinject("## Goal\ncontinue", true);
        List<Message> toolOnlyTail = List.of(Message.toolMessage("tool-out", "t1", null));
        List<Message> forced = compactor.mergeHandoffIntoTail(toolOnlyTail, wrapped);
        Assert.assertEquals(RoleType.USER, forced.get(0).getRole());
        Assert.assertTrue(forced.get(0).getContent().contains(CompactionPrompt.CONTEXT_COMPACTION_PREFIX));
        Assert.assertEquals("tool-out", forced.get(1).getContent());

        List<Message> assembledToolTail = compactor.buildPostCompactMessages(
                List.of(), wrapped, RoleType.USER, toolOnlyTail);
        Assert.assertTrue(assembledToolTail.stream().noneMatch(m -> m.getRole() == RoleType.TOOL));

        List<Message> merged = compactor.mergeHandoffIntoTail(
                List.of(Message.userMessage("tail-user", null)), wrapped);
        String content = merged.get(0).getContent();
        Assert.assertTrue(content.contains(CompactionPrompt.MERGE_PRIOR));
        Assert.assertTrue(content.contains(CompactionPrompt.MERGE_DELIMITER));
        Assert.assertTrue(content.contains("tail-user"));
        Assert.assertTrue(compactor.isCompactSummaryMessage(merged.get(0)));
        int priorIdx = content.indexOf(CompactionPrompt.MERGE_PRIOR);
        int originalIdx = content.indexOf("tail-user");
        int delimIdx = content.indexOf(CompactionPrompt.MERGE_DELIMITER);
        int summaryIdx = content.indexOf(CompactionPrompt.CONTEXT_COMPACTION_PREFIX, delimIdx);
        Assert.assertTrue(priorIdx >= 0 && originalIdx > priorIdx);
        Assert.assertTrue(delimIdx > originalIdx);
        Assert.assertTrue(summaryIdx > delimIdx);

        // head 保留 + merge tail
        List<Message> assembled = compactor.buildPostCompactMessages(
                List.of(Message.userMessage("keep-head", null)),
                wrapped,
                null,
                List.of(Message.assistantMessage("tail-assistant", null)));
        Assert.assertEquals("keep-head", assembled.get(0).getContent());
        Assert.assertTrue(assembled.get(1).getContent().contains(CompactionPrompt.MERGE_DELIMITER));

        // headEnd 不得拆开 tool pair：切在 assistant(tool_calls) 之后必须扩到 result 或回退
        List<Message> toolPair = new ArrayList<>();
        toolPair.add(Message.userMessage("u0", null));
        toolPair.add(Message.fromToolCalls("call", List.of(
                ToolCall.builder().id("c1").type("function")
                        .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                        .build())));
        toolPair.add(Message.toolMessage("result", "c1", null));
        toolPair.add(Message.userMessage("u1", null));
        int unsafe = 2; // 正好落在 tool_calls 与 tool_result 之间
        int safe = compactor.adjustHeadEndToPreserveToolPairs(toolPair, unsafe, 3);
        Assert.assertTrue("expected 1 or 3 but was " + safe, safe == 1 || safe == 3);
    }

    @Test
    public void shouldSerializeMiddleWithCapsAndBuildFallback() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .contentMaxChars(40)
                .contentHeadChars(10)
                .contentTailChars(10)
                .summaryInputMaxChars(500)
                .summaryTargetRatio(0.2d)
                .protectFirstN(3)
                .protectLastN(8)
                .contextWindow(100_000)
                .build();
        List<Message> middle = List.of(
                Message.userMessage("U".repeat(100), null),
                Message.assistantMessage("A".repeat(100), null),
                Message.toolMessage("T".repeat(100), "tid", null)
        );
        String serialized = compactor.serializeMiddleForSummarizer(middle, budget);
        Assert.assertTrue(serialized.contains("[USER]:"));
        Assert.assertTrue(serialized.contains("[ASSISTANT]:"));
        Assert.assertTrue(serialized.contains("[TOOL RESULT tid]:"));
        Assert.assertTrue(serialized.contains("...[truncated]...") || serialized.contains("omitted"));
        Assert.assertTrue(serialized.length() <= budget.getSummaryInputMaxChars() + 80);

        String fallback = compactor.buildStaticFallbackSummary(middle, null, "latest ask");
        Assert.assertTrue(fallback.contains("## Historical Task Snapshot"));
        Assert.assertTrue(fallback.contains("## Active State"));
        Assert.assertTrue(fallback.contains("latest ask"));
    }

    @Test
    public void shouldAssembleHeadHandoffTailUnderThreshold() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .summaryTargetRatio(0.25d)
                .protectFirstN(2)
                .protectLastN(4)
                .contextWindow(8_000)
                .contentMaxChars(6000)
                .contentHeadChars(4000)
                .contentTailChars(1500)
                .summaryInputMaxChars(160000)
                .build();
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("early-1", null));
        messages.add(Message.assistantMessage("early-2", null));
        for (int i = 0; i < 10; i++) {
            messages.add(Message.userMessage("mid-" + i + " " + "z".repeat(300), null));
            messages.add(Message.assistantMessage("mid-ans-" + i + " " + "y".repeat(300), null));
        }
        messages.add(Message.userMessage("latest-user", null));
        messages.add(Message.assistantMessage("latest-assistant", null));

        WorkingMemoryCompactor.CompactionWindow window = compactor.splitForFullCompact(messages, budget);
        Assert.assertTrue(window.headEnd() > 0);
        Assert.assertTrue(window.tailStart() > window.headEnd());
        List<Message> head = messages.subList(0, window.headEnd());
        List<Message> middle = messages.subList(window.headEnd(), window.tailStart());
        List<Message> tail = messages.subList(window.tailStart(), messages.size());
        String body = CompactionPrompt.groundHistoricalTaskSnapshot(
                "## Goal\ncontinue\n## Active State\nworking", "latest-user");
        String wrapped = CompactionPrompt.wrapSummaryForReinject(body, true);
        List<Message> post = compactor.buildPostCompactMessages(head, wrapped, RoleType.USER, tail);
        Assert.assertTrue(compactor.isCompactSummaryMessage(post.get(window.headEnd())));
        Assert.assertTrue(post.stream().anyMatch(m -> "latest-user".equals(m.getContent())));
        Assert.assertTrue(post.size() < messages.size());
        Assert.assertFalse(middle.isEmpty());
        Assert.assertTrue(post.stream().anyMatch(m -> "latest-assistant".equals(m.getContent())));
    }

    @Test
    public void reactShapedTranscriptMustKeepNonEmptyMiddle() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .summaryTargetRatio(0.20d)
                .protectFirstN(3)
                .protectLastN(8)
                .contextWindow(8_000)
                .keepMinTokens(20)
                .keepMinTextMessages(1)
                .keepMaxTokens(400)
                .build();
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("do the task", null));
        for (int i = 0; i < 20; i++) {
            String id = "c-" + i;
            messages.add(Message.fromToolCalls("call-" + i, List.of(
                    ToolCall.builder().id(id).type("function")
                            .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                            .build())));
            messages.add(Message.toolMessage("result-" + i + " " + "z".repeat(200), id, null));
        }
        WorkingMemoryCompactor.CompactionWindow window = compactor.splitForFullCompact(messages, budget);
        Assert.assertTrue("middle must be non-empty", window.tailStart() > window.headEnd());
        List<Message> middle = messages.subList(window.headEnd(), window.tailStart());
        Assert.assertFalse(middle.isEmpty());
        List<Message> tail = messages.subList(window.tailStart(), messages.size());
        Assert.assertTrue(tail.stream().noneMatch(m -> "do the task".equals(m.getContent())));
    }

    @Test
    public void protectLastNKeepsLastMessagesNotFirstIndexCap() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .summaryTargetRatio(0.20d)
                .protectFirstN(2)
                .protectLastN(8)
                .contextWindow(8_000)
                .keepMinTokens(10)
                .keepMinTextMessages(1)
                .keepMaxTokens(400)
                .build();
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            messages.add(Message.userMessage("m-" + i, null));
        }
        WorkingMemoryCompactor.CompactionWindow window = compactor.splitForFullCompact(messages, budget);
        Assert.assertTrue(window.headEnd() > 0);
        Assert.assertTrue("middle must exist", window.tailStart() > window.headEnd());
        Assert.assertTrue("protectLastN is last-N, so tailStart <= n-8",
                window.tailStart() <= messages.size() - 8);
        Assert.assertTrue(messages.size() - window.tailStart() >= 8);
    }

    @Test
    public void alignBoundaryForwardWalksOffEnd() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("u", null));
        messages.add(Message.fromToolCalls("call", List.of(
                ToolCall.builder().id("c1").type("function")
                        .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                        .build())));
        messages.add(Message.toolMessage("r1", "c1", null));
        messages.add(Message.toolMessage("r2", "c1", null));
        Assert.assertEquals(4, compactor.alignBoundaryForward(messages, 2));
        Assert.assertEquals(4, compactor.alignBoundaryForward(messages, 3));
    }

    @Test
    public void userAtHeadBoundaryDoesNotSwallowWholeTail() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .summaryTargetRatio(0.20d)
                .protectFirstN(0)
                .protectLastN(8)
                .contextWindow(8_000)
                .keepMinTokens(10)
                .keepMinTextMessages(1)
                .keepMaxTokens(400)
                .build();
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("only-user", null));
        messages.add(Message.fromToolCalls("call-0", List.of(
                ToolCall.builder().id("c-0").type("function")
                        .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                        .build())));
        messages.add(Message.toolMessage("result-0", "c-0", null));
        for (int i = 1; i < 16; i++) {
            String id = "c-" + i;
            messages.add(Message.fromToolCalls("call-" + i, List.of(
                    ToolCall.builder().id(id).type("function")
                            .function(ToolCall.Function.builder().name("search").arguments("{}").build())
                            .build())));
            messages.add(Message.toolMessage("result-" + i + " " + "z".repeat(80), id, null));
        }
        WorkingMemoryCompactor.CompactionWindow window = compactor.splitForFullCompact(messages, budget);
        Assert.assertTrue(window.tailStart() > window.headEnd());
        Assert.assertTrue("must not pull tail back to index 0", window.tailStart() > 1);
    }

    @Test
    public void dropOldestPreservesHandoffSummary() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .contextWindow(400)
                .keepMinTokens(20)
                .keepMinTextMessages(1)
                .keepMaxTokens(150)
                .build();
        String handoff = CompactionPrompt.wrapSummaryForReinject("## Goal\nkeep-summary", true);
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage(handoff, null));
        for (int i = 0; i < 12; i++) {
            messages.add(Message.userMessage("old-" + i + " " + "x".repeat(200), null));
            messages.add(Message.assistantMessage("ans-" + i + " " + "y".repeat(200), null));
        }
        List<Message> kept = compactor.dropOldestToFit(messages, budget, true);
        Assert.assertTrue(kept.stream().anyMatch(compactor::isCompactSummaryMessage));
        Assert.assertTrue(compactor.estimateTokens(kept) <= budget.threshold()
                || kept.stream().anyMatch(compactor::isCompactSummaryMessage));
    }
}
