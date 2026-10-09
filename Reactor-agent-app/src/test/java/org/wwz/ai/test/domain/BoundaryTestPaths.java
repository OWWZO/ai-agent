package org.wwz.ai.test.domain;

import java.nio.file.Files;
import java.nio.file.Path;

final class BoundaryTestPaths {

    private static final String APP_MODULE = "Reactor-agent-app";

    private BoundaryTestPaths() {
    }

    static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isRegularFile(current.resolve(APP_MODULE).resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法从当前工作目录向上定位 Reactor-agent 仓库根目录");
    }

    static Path moduleRoot(String moduleName) {
        Path moduleRoot = projectRoot().resolve(moduleName);
        if (!Files.isRegularFile(moduleRoot.resolve("pom.xml"))) {
            throw new IllegalStateException("未找到 Maven 模块: " + moduleRoot);
        }
        return moduleRoot;
    }

    static Path moduleMainJava(String moduleName) {
        return requireDirectory(moduleRoot(moduleName).resolve("src/main/java"));
    }

    static Path requireDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("未找到预期目录: " + directory);
        }
        return directory;
    }
}
