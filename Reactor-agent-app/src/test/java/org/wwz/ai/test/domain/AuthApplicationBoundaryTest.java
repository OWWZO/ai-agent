package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.auth.IAuthApplicationService;
import org.wwz.ai.trigger.config.BaseFilterConfig;
import org.wwz.ai.trigger.http.auth.AuthController;
import org.wwz.ai.trigger.http.auth.AuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class AuthApplicationBoundaryTest {

    private static final Path AUTH_SOURCE = BoundaryTestPaths.moduleMainJava("Reactor-agent-case")
            .resolve("org/wwz/ai/application/auth");

    @Test
    public void caseAuthMustNotImportApiDtosOrHttpResponse() throws IOException {
        try (Stream<Path> sources = Files.walk(AUTH_SOURCE)) {
            List<Path> offenders = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::importsApiTypes)
                    .toList();
            Assert.assertTrue("Case Auth 不得依赖 API DTO/Response: " + offenders, offenders.isEmpty());
        }
        Assert.assertFalse(BoundaryTestPaths.moduleMainJava("Reactor-agent-api")
                .resolve("org/wwz/ai/api/IAuthService.java").toFile().exists());
    }

    @Test
    public void triggerAuthConsumersMustDependOnApplicationInterface() {
        assertHasApplicationServiceField(AuthController.class);
        assertHasApplicationServiceField(AuthenticationFilter.class);
        Assert.assertTrue(Arrays.stream(BaseFilterConfig.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("authenticationFilter"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .anyMatch(IAuthApplicationService.class::equals));
    }

    private boolean importsApiTypes(Path source) {
        try {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            return content.contains("import org.wwz.ai.api.dto.")
                    || content.contains("import org.wwz.ai.api.response.Response");
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 Auth 源文件: " + source, e);
        }
    }

    private void assertHasApplicationServiceField(Class<?> type) {
        Assert.assertTrue(type.getSimpleName() + " 应依赖 IAuthApplicationService",
                Arrays.stream(type.getDeclaredFields())
                        .anyMatch(field -> field.getType().equals(IAuthApplicationService.class)));
    }
}
