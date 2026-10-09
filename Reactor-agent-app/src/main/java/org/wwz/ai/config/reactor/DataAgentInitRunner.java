package org.wwz.ai.config.reactor;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.wwz.ai.application.agent.dataquery.initialization.DataAgentInitializationApplicationService;
import org.wwz.ai.config.reactor.startup.H2SchemaBootstrap;

/**
 * Adapts Spring startup ordering to the H2 bootstrap and DataAgent initialization use case.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataAgentInitRunner implements CommandLineRunner {

    private final H2SchemaBootstrap h2SchemaBootstrap;
    private final DataAgentInitializationApplicationService initializationApplicationService;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting DataAgent startup initialization");
        h2SchemaBootstrap.initializeIfConfigured();
        initializationApplicationService.initialize(resolveEmbeddingDimension());
    }

    private int resolveEmbeddingDimension() {
        String dimension = System.getenv("TEXT_EMBEDDING_DIMENSION");
        if (StringUtils.isBlank(dimension)) {
            return 1024;
        }
        try {
            return Integer.parseInt(dimension);
        } catch (NumberFormatException e) {
            log.warn("TEXT_EMBEDDING_DIMENSION 非法，回退默认值 1024: {}", dimension);
            return 1024;
        }
    }
}
