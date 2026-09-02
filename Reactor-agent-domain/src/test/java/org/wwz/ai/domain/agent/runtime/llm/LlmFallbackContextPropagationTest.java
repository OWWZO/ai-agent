package org.wwz.ai.domain.agent.runtime.llm;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class LlmFallbackContextPropagationTest {

    @Test
    public void asyncFallbackKeepsCapturedAgentNameAndStepNo() throws Exception {
        AgentRunState state = AgentRunState.builder().runId(9L).build();
        state.markExecutionPosition("subagent:DeepSearch", 3);
        LlmExecutionPosition position = LlmExecutionPosition.capture(state);

        CompletableFuture<String> primary = new CompletableFuture<>();
        List<LlmInvocationStartRecord> starts = new ArrayList<>();
        AtomicReference<String> fallbackThread = new AtomicReference<>();

        CompletableFuture<String> result = primary.handle((value, error) ->
                LlmModelFallback.recoverWithPosition(
                        position,
                        state,
                        "primary",
                        unwrap(error),
                        List.of("backup"),
                        model -> {
                            fallbackThread.set(Thread.currentThread().getName());
                            starts.add(LlmInvocationStartRecord.builder()
                                    .agentName(state.getCurrentAgentName())
                                    .stepNo(state.getCurrentStepNo())
                                    .modelName(model)
                                    .invocationSeq(state.nextInvocationSeq())
                                    .build());
                            return CompletableFuture.completedFuture("fallback-ok");
                        },
                        null
                )
        ).thenCompose(next -> next);

        failOnThread(primary, "fallback-async");
        Assert.assertEquals("fallback-ok", result.get(2, TimeUnit.SECONDS));
        Assert.assertEquals(1, starts.size());
        Assert.assertEquals("subagent:DeepSearch", starts.get(0).getAgentName());
        Assert.assertEquals(Integer.valueOf(3), starts.get(0).getStepNo());
        Assert.assertEquals("backup", starts.get(0).getModelName());
        Assert.assertEquals("fallback-async", fallbackThread.get());
    }

    @Test
    public void asyncThreadWithoutRestoreLosesAgentName() throws Exception {
        AgentRunState state = AgentRunState.builder().runId(9L).build();
        state.markExecutionPosition("subagent:DeepSearch", 3);

        CompletableFuture<String> primary = new CompletableFuture<>();
        AtomicReference<String> seenName = new AtomicReference<>("UNSET");
        CompletableFuture<String> result = primary.handle((value, error) -> {
            seenName.set(state.getCurrentAgentName());
            return CompletableFuture.completedFuture("ok");
        }).thenCompose(next -> next);

        failOnThread(primary, "lost-context-async");
        Assert.assertEquals("ok", result.get(2, TimeUnit.SECONDS));
        Assert.assertNull(seenName.get());
    }

    @Test
    public void secondStepFailureDoesNotReuseFirstFallbackSuccess() throws Exception {
        AgentRunState state = AgentRunState.builder().runId(9L).build();
        state.markExecutionPosition("subagent:DeepSearch", 1);
        LlmExecutionPosition step1 = LlmExecutionPosition.capture(state);

        List<Integer> seqs = new ArrayList<>();
        List<Integer> steps = new ArrayList<>();
        List<String> models = new ArrayList<>();

        CompletableFuture<String> firstPrimary = new CompletableFuture<>();
        CompletableFuture<String> first = firstPrimary.handle((value, error) ->
                LlmModelFallback.recoverWithPosition(
                        step1,
                        state,
                        "primary",
                        unwrap(error),
                        List.of("backup-1"),
                        model -> {
                            models.add(model);
                            steps.add(state.getCurrentStepNo());
                            seqs.add(state.nextInvocationSeq());
                            return CompletableFuture.completedFuture("step1-ok");
                        },
                        null
                )
        ).thenCompose(next -> next);
        failOnThread(firstPrimary, "step1-async");
        Assert.assertEquals("step1-ok", first.get(2, TimeUnit.SECONDS));

        state.markExecutionPosition("subagent:DeepSearch", 2);
        LlmExecutionPosition step2 = LlmExecutionPosition.capture(state);
        CompletableFuture<String> secondPrimary = new CompletableFuture<>();
        CompletableFuture<String> second = secondPrimary.handle((value, error) ->
                LlmModelFallback.recoverWithPosition(
                        step2,
                        state,
                        "primary",
                        unwrap(error),
                        List.of("backup-1", "backup-2"),
                        model -> {
                            models.add(model);
                            steps.add(state.getCurrentStepNo());
                            seqs.add(state.nextInvocationSeq());
                            return CompletableFuture.<String>failedFuture(new RuntimeException(model + " overloaded"));
                        },
                        null
                )
        ).thenCompose(next -> next);
        failOnThread(secondPrimary, "step2-async");
        try {
            second.get(2, TimeUnit.SECONDS);
            Assert.fail("expected step2 failure");
        } catch (ExecutionException error) {
            Assert.assertTrue(error.getCause().getMessage().contains("backup-2 overloaded"));
        }

        Assert.assertEquals(List.of("backup-1", "backup-1", "backup-2"), models);
        Assert.assertEquals(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(2)), steps);
        Assert.assertEquals(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3)), seqs);
    }

    private static void failOnThread(CompletableFuture<?> primary, String threadName) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
        try {
            executor.execute(() -> primary.completeExceptionally(new TimeoutException("LLM stream timeout")));
        } finally {
            executor.shutdown();
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
