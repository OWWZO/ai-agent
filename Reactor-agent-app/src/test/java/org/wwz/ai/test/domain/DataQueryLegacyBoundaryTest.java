package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.DataAgentQueryService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class DataQueryLegacyBoundaryTest {

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "org.wwz.ai.domain.agent.reactor.data",
            "org.wwz.ai.domain.agent.reactor.config.data",
            "org.wwz.ai.domain.agent.reactor.model.req.DataAgentChatReq",
            "org.wwz.ai.domain.agent.reactor.model.response.ChatDataMessage");

    @Test
    public void caseTriggerAndInfrastructureDoNotImportDataQueryLegacyPackages() throws IOException {
        for (String module : List.of("Reactor-agent-case", "Reactor-agent-trigger", "Reactor-agent-infrastructure")) {
            try (Stream<Path> files = Files.walk(BoundaryTestPaths.moduleMainJava(module))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file, StandardCharsets.UTF_8);
                    for (String forbiddenImport : FORBIDDEN_IMPORTS) {
                        Assert.assertFalse(file + " imports " + forbiddenImport, source.contains(forbiddenImport));
                    }
                }
            }
        }
    }

    @Test
    public void domainQueryServiceDoesNotAcceptTriggerOrApplicationContracts() {
        Arrays.stream(DataAgentQueryService.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .map(Class::getName)
                .forEach(parameterType -> {
                    Assert.assertFalse(parameterType, parameterType.startsWith("org.wwz.ai.trigger."));
                    Assert.assertFalse(parameterType, parameterType.startsWith("org.wwz.ai.application."));
                    Assert.assertFalse(parameterType, parameterType.endsWith("RequestVO"));
                });
    }
}
