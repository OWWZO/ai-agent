package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 生产 visitor CORS 与反向代理配置回归测试。
 */
public class ProductionVisitorCorsConfigTest {

    @Test
    public void shouldConfigureProductionVisitorAllowedOriginsAndForwardHeaders() throws Exception {
        String content = Files.readString(
                new ClassPathResource("application-prod.yml").getFile().toPath(),
                StandardCharsets.UTF_8
        );

        Assert.assertTrue("生产配置必须启用 forwarded headers 识别", content.contains("forward-headers-strategy: framework"));
        Assert.assertTrue("生产配置必须通过环境变量注入允许来源", content.contains("${PUBLIC_ORIGIN:http://localhost:3000}"));
    }

    @Test
    public void shouldKeepDockerComposeAsTheDeploymentEntryPoint() throws Exception {
        String content = Files.readString(
                resolveRepoFile("docker-compose.yml"),
                StandardCharsets.UTF_8
        );

        Assert.assertTrue("Compose 必须编排 Java Backend", content.contains("reactor-backend:"));
        Assert.assertTrue("Compose 必须编排 reactor-tool API", content.contains("reactor-tool:"));
        Assert.assertTrue("Compose 必须编排 sandbox 进程", content.contains("reactor-sandbox:"));
        Assert.assertTrue("Compose 必须编排前端反代", content.contains("frontend:"));
    }

    @Test
    public void shouldForwardHostAndPortInDockerNginxConfig() throws Exception {
        assertNginxConfigContainsForwardHeaders("docker/nginx.conf");
    }

    private void assertNginxConfigContainsForwardHeaders(String path) throws Exception {
        String content = Files.readString(resolveRepoFile(path), StandardCharsets.UTF_8);

        Assert.assertTrue(path + " 必须透传 X-Forwarded-Host", content.contains("proxy_set_header X-Forwarded-Host $host;"));
        Assert.assertTrue(path + " 必须透传 X-Forwarded-Port $server_port;", content.contains("proxy_set_header X-Forwarded-Port $server_port;"));
    }

    private Path resolveRepoFile(String relativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        Assert.fail("未找到仓库文件: " + relativePath);
        return null;
    }
}
