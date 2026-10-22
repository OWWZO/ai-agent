package org.wwz.ai.application.catalog.api;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.catalog.model.AiClientApiConfig;
import org.wwz.ai.domain.agent.catalog.port.IAiClientApiConfigRepository;
import org.wwz.ai.domain.agent.catalog.port.ICatalogModelRuntimePort;

import java.util.List;
import java.util.Optional;

/**
 * API 配置用例使用 fake repository 的独立测试。
 */
public class ApiConfigApplicationServiceTest {

    @Test
    public void shouldPersistCommandAndInvalidateModelCatalog() {
        InMemoryApiRepository repository = new InMemoryApiRepository();
        RecordingRuntime runtime = new RecordingRuntime();
        ApiConfigApplicationService service = new ApiConfigApplicationService(repository, runtime);

        ApplicationResult<Boolean> result = service.create(new ApiConfigCommand(
                null,
                "dashscope",
                "https://example.test",
                "secret",
                "/chat/completions",
                "/embeddings",
                1));

        Assert.assertEquals("0000", result.code());
        Assert.assertTrue(result.data());
        Assert.assertEquals("dashscope", repository.inserted.getApiId());
        Assert.assertEquals(1, runtime.invalidations);
    }

    @Test
    public void shouldRejectDuplicateApiIdBeforeInsert() {
        InMemoryApiRepository repository = new InMemoryApiRepository();
        repository.existing = AiClientApiConfig.builder().apiId("dashscope").build();
        ApiConfigApplicationService service = new ApiConfigApplicationService(repository, new RecordingRuntime());

        ApplicationResult<Boolean> result = service.create(new ApiConfigCommand(
                null, "dashscope", "https://example.test", "secret", null, null, 1));

        Assert.assertEquals("0002", result.code());
        Assert.assertFalse(result.data());
        Assert.assertNull(repository.inserted);
    }

    private static final class RecordingRuntime implements ICatalogModelRuntimePort {
        private int invalidations;

        @Override
        public List<org.wwz.ai.domain.agent.catalog.model.CatalogModel> listUserSelectableModels() {
            return List.of();
        }

        @Override
        public void invalidateAll() {
            invalidations++;
        }
    }

    private static final class InMemoryApiRepository implements IAiClientApiConfigRepository {
        private AiClientApiConfig inserted;
        private AiClientApiConfig existing;

        @Override
        public boolean insert(AiClientApiConfig config) {
            inserted = config;
            return true;
        }

        @Override
        public boolean updateById(AiClientApiConfig config) {
            return true;
        }

        @Override
        public boolean updateByApiId(AiClientApiConfig config) {
            return true;
        }

        @Override
        public boolean deleteById(Long id) {
            return true;
        }

        @Override
        public boolean deleteByApiId(String apiId) {
            return true;
        }

        @Override
        public Optional<AiClientApiConfig> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<AiClientApiConfig> findByApiId(String apiId) {
            return Optional.ofNullable(existing);
        }

        @Override
        public List<AiClientApiConfig> findEnabled() {
            return List.of();
        }

        @Override
        public List<AiClientApiConfig> findAll() {
            return List.of();
        }
    }
}
