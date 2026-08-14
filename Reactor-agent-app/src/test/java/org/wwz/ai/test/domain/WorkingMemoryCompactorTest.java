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
    public void shouldNotCompactWhenUnderThreshold() {
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .contextWindow(100_000)
                .maxOutputTokens(16_000)
                .bufferTokens(13_000)
                .maxOutputReserve(20_000)
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
        // 极低阈值，强制 drop
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .contextWindow(200)
                .maxOutputTokens(50)
                .bufferTokens(50)
                .maxOutputReserve(50)
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

        // 若保留了 tool result，前面必须有对应 tool_use
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
        // 若保留 tool_result，必须仍有配对 tool_use
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
        Assert.assertTrue(wrapped.contains("This session is being continued"));
        Assert.assertTrue(wrapped.contains("Recent messages are preserved verbatim"));
        Assert.assertTrue(wrapped.contains("NOT a context-compaction"));
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
                .maxOutputTokens(400)
                .bufferTokens(400)
                .maxOutputReserve(400)
                .keepMinTokens(20)
                .keepMinTextMessages(1)
                .keepMaxTokens(400)
                .build();
        // 直接验证清洗路径；不依赖是否超阈
        List<Message> sm = compactor.trySessionMemoryCompact(messages, null, budget);
        if (sm == null) {
            // 未超阈时仍可验证 format/wrap 清洗
            String cleaned = CompactionPrompt.wrapSummaryForReinject(
                    CompactionPrompt.formatCompactSummary(polluted), true);
            Assert.assertFalse(cleaned.toLowerCase().contains("<analysis"));
            Assert.assertTrue(cleaned.contains("NOT a context-compaction"));
        } else {
            Assert.assertTrue(compactor.isCompactSummaryMessage(sm.get(0)));
            Assert.assertFalse(sm.get(0).getContent().toLowerCase().contains("<analysis"));
            Assert.assertTrue(sm.get(0).getContent().contains("NOT a context-compaction"));
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

        // start 落在 tool result 上，应回退到 assistant tool_use
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
                .maxOutputTokens(16_000)
                .bufferTokens(13_000)
                .maxOutputReserve(20_000)
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
        // threshold ≈ 1200; 多段长正文保证超阈，压缩后 notes+tail 应回落到阈值下
        CompactionBudget budget = CompactionBudget.builder()
                .enabled(true)
                .sessionMemoryEnabled(true)
                .contextWindow(2_000)
                .maxOutputTokens(400)
                .bufferTokens(400)
                .maxOutputReserve(400)
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
        Assert.assertTrue(sm.get(0).getContent().contains("NOT a context-compaction"));
        Assert.assertTrue(sm.stream().anyMatch(m -> m.getContent() != null && m.getContent().contains("latest")));
    }
}
