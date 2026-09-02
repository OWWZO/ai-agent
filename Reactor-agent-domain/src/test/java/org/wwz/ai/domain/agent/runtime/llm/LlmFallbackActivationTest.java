package org.wwz.ai.domain.agent.runtime.llm;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class LlmFallbackActivationTest {

    @Test
    public void timeoutOnPrimaryReturnsFallbackSuccess() throws Exception {
        AgentRunState state = markedState();
        List<String> attempted = new ArrayList<>();
        CompletableFuture<String> primary = new CompletableFuture<>();

        CompletableFuture<String> result = LlmModelFallback.afterPrimary(
                LlmExecutionPosition.capture(state),
                state,
                "primary",
                primary,
                List.of("backup-1"),
                model -> {
                    attempted.add(model);
                    Assert.assertEquals("subagent:DeepSearch", state.getCurrentAgentName());
                    return CompletableFuture.completedFuture("from-backup-1");
                },
                null
        );

        failOnThread(primary, "timeout-async", new TimeoutException("LLM stream timeout after 1s"));

        Assert.assertEquals("from-backup-1", result.get(2, TimeUnit.SECONDS));
        Assert.assertEquals(List.of("backup-1"), attempted);
        Assert.assertFalse(result.isCompletedExceptionally());
    }

    @Test
    public void status524OnPrimaryWalksFallbackChain() throws Exception {
        AgentRunState state = markedState();
        List<String> attempted = new ArrayList<>();
        List<String> transitions = new ArrayList<>();
        CompletableFuture<String> primary = new CompletableFuture<>();

        CompletableFuture<String> result = LlmModelFallback.afterPrimary(
                LlmExecutionPosition.capture(state),
                state,
                "primary",
                primary,
                List.of("backup-1", "backup-2"),
                model -> {
                    attempted.add(model);
                    if ("backup-1".equals(model)) {
                        return CompletableFuture.failedFuture(new RuntimeException("Unknown status code [524]"));
                    }
                    return CompletableFuture.completedFuture("from-backup-2");
                },
                (from, to, cause) -> transitions.add(from + "->" + to)
        );

        failOnThread(primary, "status-524-async", new RuntimeException("Unknown status code [524]"));

        Assert.assertEquals("from-backup-2", result.get(2, TimeUnit.SECONDS));
        Assert.assertEquals(List.of("backup-1", "backup-2"), attempted);
        Assert.assertEquals(List.of("primary->backup-1", "backup-1->backup-2"), transitions);
    }

    @Test
    public void connectionResetOnPrimaryActivatesFallback() throws Exception {
        AgentRunState state = markedState();
        AtomicReference<String> usedModel = new AtomicReference<>();
        CompletableFuture<String> primary = new CompletableFuture<>();

        CompletableFuture<String> result = LlmModelFallback.afterPrimary(
                LlmExecutionPosition.capture(state),
                state,
                "primary",
                primary,
                List.of("backup-1"),
                model -> {
                    usedModel.set(model);
                    return CompletableFuture.completedFuture("recovered");
                },
                null
        );

        failOnThread(primary, "reset-async", new IOException("Connection reset"));

        Assert.assertEquals("recovered", result.get(2, TimeUnit.SECONDS));
        Assert.assertEquals("backup-1", usedModel.get());
    }

    @Test
    public void exhaustedFallbacksReturnLastErrorNotPrimaryTimeout() throws Exception {
        AgentRunState state = markedState();
        List<String> attempted = new ArrayList<>();
        CompletableFuture<String> primary = new CompletableFuture<>();

        CompletableFuture<String> result = LlmModelFallback.afterPrimary(
                LlmExecutionPosition.capture(state),
                state,
                "primary",
                primary,
                List.of("backup-1", "backup-2"),
                model -> {
                    attempted.add(model);
                    return CompletableFuture.<String>failedFuture(new RuntimeException(model + " overloaded"));
                },
                null
        );

        failOnThread(primary, "all-fail-async", new TimeoutException("LLM stream timeout"));

        try {
            result.get(2, TimeUnit.SECONDS);
            Assert.fail("expected fallback chain failure");
        } catch (ExecutionException error) {
            Assert.assertTrue(error.getCause().getMessage().contains("backup-2 overloaded"));
            Assert.assertFalse(error.getCause() instanceof TimeoutException);
        }
        Assert.assertEquals(List.of("backup-1", "backup-2"), attempted);
    }

    @Test
    public void unauthorizedStartsFallback() throws Exception {
        AgentRunState state = markedState();
        List<String> attempted = new ArrayList<>();
        CompletableFuture<String> primary = new CompletableFuture<>();

        CompletableFuture<String> result = LlmModelFallback.afterPrimary(
                LlmExecutionPosition.capture(state),
                state,
                "primary",
                primary,
                List.of("backup-1"),
                model -> {
                    attempted.add(model);
                    return CompletableFuture.completedFuture("from-backup-auth");
                },
                null
        );

        failOnThread(primary, "auth-async", new RuntimeException("Unauthorized invalid api key"));

        Assert.assertEquals("from-backup-auth", result.get(2, TimeUnit.SECONDS));
        Assert.assertEquals(List.of("backup-1"), attempted);
    }

    @Test
    public void certificateErrorStartsFallback() throws Exception {
        AgentRunState state = markedState();
        List<String> attempted = new ArrayList<>();
        CompletableFuture<String> primary = new CompletableFuture<>();

        CompletableFuture<String> result = LlmModelFallback.afterPrimary(
                LlmExecutionPosition.capture(state),
                state,
                "primary",
                primary,
                List.of("backup-1"),
                model -> {
                    attempted.add(model);
                    return CompletableFuture.completedFuture("from-backup-cert");
                },
                null
        );

        failOnThread(primary, "cert-async", new javax.net.ssl.SSLHandshakeException(
                "PKIX path building failed: unable to find valid certification path"));

        Assert.assertEquals("from-backup-cert", result.get(2, TimeUnit.SECONDS));
        Assert.assertEquals(List.of("backup-1"), attempted);
    }

    @Test
    public void emptyFallbackListKeepsPrimaryError() throws Exception {
        AgentRunState state = markedState();
        List<String> attempted = new ArrayList<>();
        CompletableFuture<String> primary = new CompletableFuture<>();

        CompletableFuture<String> result = LlmModelFallback.afterPrimary(
                LlmExecutionPosition.capture(state),
                state,
                "primary",
                primary,
                List.of(),
                model -> {
                    attempted.add(model);
                    return CompletableFuture.completedFuture("should-not-run");
                },
                null
        );

        failOnThread(primary, "empty-list-async", new TimeoutException("LLM stream timeout"));

        try {
            result.get(2, TimeUnit.SECONDS);
            Assert.fail("expected primary timeout");
        } catch (ExecutionException error) {
            Assert.assertTrue(error.getCause() instanceof TimeoutException);
        }
        Assert.assertTrue(attempted.isEmpty());
    }

    @Test
    public void primarySuccessDoesNotTouchFallback() throws Exception {
        AgentRunState state = markedState();
        List<String> attempted = new ArrayList<>();

        String result = LlmModelFallback.afterPrimary(
                LlmExecutionPosition.capture(state),
                state,
                "primary",
                CompletableFuture.completedFuture("from-primary"),
                List.of("backup-1"),
                model -> {
                    attempted.add(model);
                    return CompletableFuture.completedFuture("from-backup");
                },
                null
        ).get(2, TimeUnit.SECONDS);

        Assert.assertEquals("from-primary", result);
        Assert.assertTrue(attempted.isEmpty());
    }

    private static AgentRunState markedState() {
        AgentRunState state = AgentRunState.builder().runId(9L).build();
        state.markExecutionPosition("subagent:DeepSearch", 3);
        return state;
    }

    private static void failOnThread(CompletableFuture<?> primary, String threadName, Throwable error) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
        try {
            executor.execute(() -> primary.completeExceptionally(error));
        } finally {
            executor.shutdown();
        }
    }
}
