package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 最终 DDD 静态守卫。
 * <p>
 * 锁定当前已成立的边界规则，防止后续批次回流。已明确登记为延期的规则不在此断言：
 * <ul>
 *     <li>Ledger DAO 仍直接返回 domain ledger entity（PO 分离未完成，R7 延期项）；</li>
 *     <li>Case 仍引用 {@code domain.agent.reactor.config.ReactorConfig}（过渡态共享配置，R3 延期项）。</li>
 * </ul>
 */
public class DddArchitectureGuardTest {

    private static final Path DOMAIN = BoundaryTestPaths.moduleMainJava("Reactor-agent-domain");
    private static final Path CASE = BoundaryTestPaths.moduleMainJava("Reactor-agent-case");
    private static final Path TRIGGER = BoundaryTestPaths.moduleMainJava("Reactor-agent-trigger");
    private static final Path INFRA = BoundaryTestPaths.moduleMainJava("Reactor-agent-infrastructure");

    private static final String[] DOMAIN_FORBIDDEN_IMPORTS = {
            "import org.apache.ibatis",
            "import com.baomidou",
            "import org.elasticsearch",
            "import io.qdrant",
            "import okhttp3",
            "import org.springframework.ai",
            "import io.modelcontextprotocol",
            "import org.springframework.web.reactive",
            "import reactor.core.publisher",
            "import org.springframework.jdbc",
            "import jakarta.servlet"
    };

    @Test
    public void domainMustNotImportTechnicalClients() throws IOException {
        List<String> offenders = scanImports(DOMAIN, DOMAIN_FORBIDDEN_IMPORTS);
        Assert.assertTrue("domain 不得 import 技术客户端/持久化实现: " + offenders, offenders.isEmpty());
    }

    @Test
    public void caseMustNotImportInfrastructure() throws IOException {
        List<String> offenders = scanImports(CASE, new String[]{"import org.wwz.ai.infrastructure"});
        Assert.assertTrue("case 不得 import infrastructure: " + offenders, offenders.isEmpty());
    }

    @Test
    public void triggerMustNotImportInfrastructure() throws IOException {
        List<String> offenders = scanImports(TRIGGER, new String[]{"import org.wwz.ai.infrastructure"});
        Assert.assertTrue("trigger 不得 import infrastructure: " + offenders, offenders.isEmpty());
    }

    @Test
    public void caseMustNotImportApiDtoOrResponse() throws IOException {
        List<String> offenders = scanImports(CASE, new String[]{
                "import org.wwz.ai.api.dto",
                "import org.wwz.ai.api.response"
        });
        Assert.assertTrue("case 不得 import api dto/response: " + offenders, offenders.isEmpty());
    }

    @Test
    public void triggerMustNotImplementLegacyApiAdminService() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(TRIGGER)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.contains("implements IAiClientApiAdminService")
                        || content.contains("implements IAiClientModelAdminService")
                        || content.contains("implements IAiClientToolMcpAdminService")) {
                    offenders.add(file.toString());
                }
            }
        }
        Assert.assertTrue("trigger 不得再 implements 旧 API Admin Service: " + offenders, offenders.isEmpty());
    }

    @Test
    public void persistenceObjectsMustNotImportDomainModel() throws IOException {
        Path poDir = INFRA.resolve("org/wwz/ai/infrastructure/dao/po");
        List<String> offenders = scanImports(poDir, new String[]{"import org.wwz.ai.domain"});
        Assert.assertTrue("PO 不得引用 domain 模型: " + offenders, offenders.isEmpty());
    }

    @Test
    public void domainMustNotOwnMapperOrBaseMapper() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(DOMAIN)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.contains("@Mapper") || content.contains("BaseMapper<")) {
                    offenders.add(file.toString());
                }
            }
        }
        Assert.assertTrue("domain 不得持有 @Mapper / BaseMapper: " + offenders, offenders.isEmpty());
    }

    private List<String> scanImports(Path root, String[] prefixes) throws IOException {
        List<String> offenders = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return offenders;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                for (String prefix : prefixes) {
                    if (content.contains(prefix)) {
                        offenders.add(file + " -> " + prefix);
                        break;
                    }
                }
            }
        }
        return offenders;
    }
}
