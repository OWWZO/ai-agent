package org.wwz.ai.domain.agent.runtime.llm;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LlmModelFallbackChainTest {

    @Test
    public void failedFallbackContinuesWithNextModel() {
        List<String> attemptedModels = new ArrayList<>();
        List<String> transitions = new ArrayList<>();

        String result = LlmModelFallback.executeFallbackChain(
                "primary",
                new RuntimeException("primary overloaded"),
                List.of("backup-1", "backup-2"),
                model -> {
                    attemptedModels.add(model);
                    if ("backup-1".equals(model)) {
                        return CompletableFuture.failedFuture(new RuntimeException("backup overloaded"));
                    }
                    return CompletableFuture.completedFuture("ok");
                },
                (from, to, cause) -> transitions.add(from + "->" + to)
        ).join();

        Assert.assertEquals("ok", result);
        Assert.assertEquals(List.of("backup-1", "backup-2"), attemptedModels);
        Assert.assertEquals(List.of("primary->backup-1", "backup-1->backup-2"), transitions);
    }

    @Test
    public void exhaustedFallbackChainReturnsLastError() {
        List<String> attemptedModels = new ArrayList<>();

        CompletableFuture<String> result = LlmModelFallback.executeFallbackChain(
                "primary",
                new RuntimeException("primary overloaded"),
                List.of("backup-1", "backup-2"),
                model -> {
                    attemptedModels.add(model);
                    return CompletableFuture.failedFuture(new RuntimeException(model + " overloaded"));
                },
                null
        );

        try {
            result.join();
            Assert.fail("expected fallback chain failure");
        } catch (RuntimeException error) {
            Assert.assertTrue(error.getCause().getMessage().contains("backup-2 overloaded"));
        }
        Assert.assertEquals(List.of("backup-1", "backup-2"), attemptedModels);
    }
}
