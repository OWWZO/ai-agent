package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.llm.ContextTokenTracker;
import org.wwz.ai.domain.agent.runtime.llm.LlmUsageSnapshot;
import org.wwz.ai.domain.agent.runtime.llm.PromptShape;
import org.wwz.ai.domain.agent.runtime.llm.TokenCounter;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContextTokenTrackerTest {

    private final TokenCounter counter = new TokenCounter();

    @Test
    public void providerUsagePlusDeltaMatchesAnchorFormula() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("history that must not be double counted", null));
        messages.add(Message.assistantMessage("reply already in usage", null));
        int anchor = messages.size();
        Message toolResult = Message.toolMessage("x".repeat(160), "call-1", null);
        messages.add(toolResult);

        PromptShape shape = PromptShape.functionCall(Message.systemMessage("sys", null), messages, null);
        ContextTokenTracker tracker = new ContextTokenTracker(counter);
        tracker.recordProviderUsage(usage(100, 20, 50), anchor, counter.fingerprint(shape));

        ContextTokenTracker.ContextTokenEstimate estimate = tracker.estimateCurrentContext(shape);
        int delta = counter.estimateMessageDelta(List.of(toolResult));
        Assert.assertEquals(ContextTokenTracker.SOURCE_PROVIDER_USAGE, estimate.getEstimateSource());
        Assert.assertEquals(120, estimate.getProviderContextTokens());
        Assert.assertEquals(delta, estimate.getDeltaTokens());
        Assert.assertEquals(120 + delta, estimate.getEstimatedTokens());
        Assert.assertTrue(estimate.getEstimatedTokens() >= 155 && estimate.getEstimatedTokens() <= 175);
    }

    @Test
    public void cachedPromptTokensAreNotAddedAgain() {
        PromptShape shape = PromptShape.text(Message.systemMessage("sys", null), List.of(Message.userMessage("q", null)));
        ContextTokenTracker tracker = new ContextTokenTracker(counter);
        tracker.recordProviderUsage(usage(100, 20, 80), 1, counter.fingerprint(shape));
        Assert.assertEquals(120, tracker.snapshot().getLastProviderContextTokens());
    }

    @Test
    public void messagesAfterAnchorAreCountedOnce() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage("u1", null));
        messages.add(Message.assistantMessage("a1", null));
        int anchor = messages.size();
        messages.add(Message.toolMessage("t1", "c1", null));
        messages.add(Message.toolMessage("t2", "c2", null));
        PromptShape shape = PromptShape.text(null, messages);
        ContextTokenTracker tracker = new ContextTokenTracker(counter);
        tracker.recordProviderUsage(usage(50, 10, 0), anchor, counter.fingerprint(shape));
        int delta = counter.estimateMessageDelta(messages.subList(anchor, messages.size()));
        ContextTokenTracker.ContextTokenEstimate estimate = tracker.estimateCurrentContext(shape);
        Assert.assertEquals(delta, estimate.getDeltaTokens());
        Assert.assertEquals(60 + delta, estimate.getEstimatedTokens());
    }

    @Test
    public void messagesBeforeAnchorAreNotAddedAgain() {
        List<Message> history = List.of(
                Message.userMessage("old-1 " + "h".repeat(200), null),
                Message.assistantMessage("old-2", null),
                Message.userMessage("old-3", null),
                Message.assistantMessage("old-4", null)
        );
        PromptShape shape = PromptShape.text(Message.systemMessage("sys", null), history);
        ContextTokenTracker tracker = new ContextTokenTracker(counter);
        tracker.recordProviderUsage(usage(80, 5, 0), history.size(), counter.fingerprint(shape));
        ContextTokenTracker.ContextTokenEstimate estimate = tracker.estimateCurrentContext(shape);
        Assert.assertEquals(0, estimate.getDeltaTokens());
        Assert.assertEquals(85, estimate.getEstimatedTokens());
        Assert.assertTrue(estimate.getEstimatedTokens() < counter.estimatePrompt(shape).getEstimatedTotalTokens()
                || history.size() > 2);
    }

    @Test
    public void systemFingerprintChangeFallsBackToLocalEstimate() {
        List<Message> messages = List.of(Message.userMessage("q", null));
        PromptShape original = PromptShape.functionCall(Message.systemMessage("sys-a", null), messages, null);
        PromptShape changed = PromptShape.functionCall(Message.systemMessage("sys-b-changed", null), messages, null);
        ContextTokenTracker tracker = new ContextTokenTracker(counter);
        tracker.recordProviderUsage(usage(100, 20, 0), 1, counter.fingerprint(original));
        ContextTokenTracker.ContextTokenEstimate estimate = tracker.estimateCurrentContext(changed);
        Assert.assertEquals(ContextTokenTracker.SOURCE_LOCAL_ESTIMATE, estimate.getEstimateSource());
        Assert.assertEquals(counter.estimatePrompt(changed).getEstimatedTotalTokens(), estimate.getEstimatedTokens());
    }

    @Test
    public void toolsSchemaChangeFallsBackToLocalEstimate() {
        List<Message> messages = List.of(Message.userMessage("q", null));
        ToolCollection toolsA = new ToolCollection();
        toolsA.addTool(new StubTool("a", "da", Map.of("type", "object")));
        ToolCollection toolsB = new ToolCollection();
        toolsB.addTool(new StubTool("b", "db", Map.of("type", "object", "properties", Map.of("x", Map.of("type", "string")))));
        PromptShape original = PromptShape.functionCall(Message.systemMessage("sys", null), messages, toolsA);
        PromptShape changed = PromptShape.functionCall(Message.systemMessage("sys", null), messages, toolsB);
        ContextTokenTracker tracker = new ContextTokenTracker(counter);
        tracker.recordProviderUsage(usage(100, 20, 0), 1, counter.fingerprint(original));
        ContextTokenTracker.ContextTokenEstimate estimate = tracker.estimateCurrentContext(changed);
        Assert.assertEquals(ContextTokenTracker.SOURCE_LOCAL_ESTIMATE, estimate.getEstimateSource());
    }

    @Test
    public void missingUsageUsesLocalEstimate() {
        PromptShape shape = PromptShape.text(Message.systemMessage("sys", null), List.of(Message.userMessage("q", null)));
        ContextTokenTracker tracker = new ContextTokenTracker(counter);
        tracker.recordProviderUsage(LlmUsageSnapshot.empty(), 1, counter.fingerprint(shape));
        ContextTokenTracker.ContextTokenEstimate estimate = tracker.estimateCurrentContext(shape);
        Assert.assertEquals(ContextTokenTracker.SOURCE_LOCAL_ESTIMATE, estimate.getEstimateSource());
        Assert.assertFalse(tracker.snapshot().isUsageAvailable());
    }

    @Test
    public void compactResetClearsAnchor() {
        PromptShape shape = PromptShape.text(null, List.of(Message.userMessage("q", null)));
        ContextTokenTracker tracker = new ContextTokenTracker(counter);
        tracker.recordProviderUsage(usage(100, 20, 0), 1, counter.fingerprint(shape));
        Assert.assertTrue(tracker.snapshot().isUsageAvailable());
        tracker.reset();
        Assert.assertFalse(tracker.snapshot().isUsageAvailable());
        Assert.assertEquals(0, tracker.snapshot().getLastProviderContextTokens());
        Assert.assertEquals(ContextTokenTracker.SOURCE_LOCAL_ESTIMATE,
                tracker.estimateCurrentContext(shape).getEstimateSource());
    }

    @Test
    public void parentAndChildTrackersDoNotShareState() {
        PromptShape shape = PromptShape.text(null, List.of(Message.userMessage("q", null)));
        ContextTokenTracker parentTracker = new ContextTokenTracker(counter);
        ContextTokenTracker childTracker = new ContextTokenTracker(counter);
        parentTracker.recordProviderUsage(usage(100, 20, 0), 1, counter.fingerprint(shape));
        Assert.assertTrue(parentTracker.snapshot().isUsageAvailable());
        Assert.assertFalse(childTracker.snapshot().isUsageAvailable());

        AgentContext parent = AgentContext.builder().requestId("p").sessionId("s").build();
        AgentContext child = AgentContext.builder().requestId("c").sessionId("s")
                .agentRunState(parent.getAgentRunState())
                .build();
        parent.contextTokenTracker().recordProviderUsage(usage(90, 10, 0), 1, counter.fingerprint(shape));
        Assert.assertTrue(parent.contextTokenTracker().snapshot().isUsageAvailable());
        Assert.assertFalse(child.contextTokenTracker().snapshot().isUsageAvailable());
        Assert.assertNotSame(parent.contextTokenTracker(), child.contextTokenTracker());
        Assert.assertNotSame(parent.getContextTokenTracker(), parent.getAgentRunState());
    }

    private static LlmUsageSnapshot usage(int prompt, int completion, int cached) {
        return LlmUsageSnapshot.builder()
                .promptTokens(prompt)
                .completionTokens(completion)
                .cachedPromptTokens(cached)
                .totalTokens(prompt + completion)
                .build();
    }

    private static final class StubTool implements BaseTool {
        private final String name;
        private final String description;
        private final Map<String, Object> params;

        private StubTool(String name, String description, Map<String, Object> params) {
            this.name = name;
            this.description = description;
            this.params = params;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Map<String, Object> toParams() {
            return params;
        }

        @Override
        public Object execute(Object input) {
            return "ok";
        }
    }
}
