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
        Assert.assertTrue("生产配置必须允许 www 域名跨域携带凭证", content.contains("- https://www.owwzo.top"));
        Assert.assertTrue("生产配置必须允许根域名跨域携带凭证", content.contains("- https://owwzo.top"));
    }

    @Test
    public void shouldKeepDeploymentTemplateInSyncForVisitorCorsAndForwardHeaders() throws Exception {
        String content = Files.readString(
                resolveRepoFile("docs/dev-ops/ubuntu/server-bundle/templates/application-prod.yml.tmpl"),
                StandardCharsets.UTF_8
        );

        Assert.assertTrue("部署模板必须启用 forwarded headers 识别", content.contains("forward-headers-strategy: framework"));
        Assert.assertTrue("部署模板必须允许 www 域名跨域携带凭证", content.contains("- https://www.owwzo.top"));
        Assert.assertTrue("部署模板必须允许根域名跨域携带凭证", content.contains("- https://owwzo.top"));
    }

    @Test
    public void shouldKeepProductionEnvExampleInSyncForVisitorCorsAndForwardHeaders() throws Exception {
        String content = Files.readString(
                resolveRepoFile("docs/dev-ops/ubuntu/env/reactor-agent-prod.yml.example"),
                StandardCharsets.UTF_8
        );

        Assert.assertTrue("生产环境示例必须启用 forwarded headers 识别", content.contains("forward-headers-strategy: framework"));
        Assert.assertTrue("生产环境示例必须允许 www 域名跨域携带凭证", content.contains("- https://www.owwzo.top"));
        Assert.assertTrue("生产环境示例必须允许根域名跨域携带凭证", content.contains("- https://owwzo.top"));
    }

    @Test
    public void shouldForwardHostAndPortInNginxConfigs() throws Exception {
        assertNginxConfigContainsForwardHeaders("docs/dev-ops/ubuntu/nginx/reactor-agent.conf");
        assertNginxConfigContainsForwardHeaders("docs/dev-ops/ubuntu/server-bundle/templates/reactor-agent.conf.tmpl");
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
