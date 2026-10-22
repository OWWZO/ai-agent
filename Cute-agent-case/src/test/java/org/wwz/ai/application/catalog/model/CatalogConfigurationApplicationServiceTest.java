package org.wwz.ai.application.catalog.model;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.domain.agent.catalog.model.AiClientApiConfig;
import org.wwz.ai.domain.agent.catalog.model.AiClientModelConfig;
import org.wwz.ai.domain.agent.catalog.model.CatalogModel;
import org.wwz.ai.domain.agent.catalog.port.IAiClientApiConfigRepository;
import org.wwz.ai.domain.agent.catalog.port.IAiClientModelConfigRepository;
import org.wwz.ai.domain.agent.catalog.port.ICatalogModelRuntimePort;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class CatalogConfigurationApplicationServiceTest {

    @Test
    public void shouldRejectDuplicateModelIdAndApiIdWithoutWriting() {
        InMemoryApiRepository apiRepository = new InMemoryApiRepository();
        InMemoryModelRepository modelRepository = new InMemoryModelRepository();
        modelRepository.existing = AiClientModelConfig.builder().modelId("gpt").build();
        CatalogConfigurationApplicationService service = new CatalogConfigurationApplicationService(
                apiRepository, modelRepository, new RecordingRuntime());

        try {
            service.createModel(command("gpt", "new-api"));
            Assert.fail("duplicate model id should fail");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("模型 ID 已存在: gpt", e.getMessage());
        }
        Assert.assertNull(apiRepository.inserted);

        modelRepository.existing = null;
        apiRepository.existing = AiClientApiConfig.builder().apiId("new-api").build();
        try {
            service.createModel(command("gpt", "new-api"));
            Assert.fail("duplicate api id should fail");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("API ID 已存在: new-api", e.getMessage());
        }
        Assert.assertNull(apiRepository.inserted);
    }

    @Test
    public void shouldLeaveRuntimeUntouchedWhenSecondWriteFails() {
        InMemoryApiRepository apiRepository = new InMemoryApiRepository();
        InMemoryModelRepository modelRepository = new InMemoryModelRepository();
        modelRepository.insertResult = false;
        RecordingRuntime runtime = new RecordingRuntime();
        CatalogConfigurationApplicationService service = new CatalogConfigurationApplicationService(
                apiRepository, modelRepository, runtime);

        try {
            service.createModel(command("gpt", "new-api"));
            Assert.fail("second write failure should propagate through transaction boundary");
        } catch (IllegalStateException e) {
            Assert.assertEquals("模型配置写入失败", e.getMessage());
        }
        Assert.assertNotNull(apiRepository.inserted);
        Assert.assertEquals(0, runtime.invalidations);
    }

    @Test
    public void shouldDeclareRollbackForCheckedAndRuntimeWriteFailures() throws Exception {
        Method method = CatalogConfigurationApplicationService.class.getMethod(
                "createModel", CatalogModelCreateCommand.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        Assert.assertNotNull(transactional);
        Assert.assertArrayEquals(new Class<?>[]{Exception.class}, transactional.rollbackFor());
    }

    private static CatalogModelCreateCommand command(String modelId, String apiId) {
        return new CatalogModelCreateCommand(
                apiId, "https://example.test", "secret", null, null,
                modelId, "Model", "openai", "default", 0, 4096, 1);
    }

    private static final class RecordingRuntime implements ICatalogModelRuntimePort {
        private int invalidations;

        @Override
        public List<CatalogModel> listUserSelectableModels() {
            return List.of();
        }

        @Override
        public void invalidateAll() {
            invalidations++;
        }
    }

    private static final class InMemoryApiRepository implements IAiClientApiConfigRepository {
        private AiClientApiConfig existing;
        private AiClientApiConfig inserted;

        @Override
        public boolean insert(AiClientApiConfig config) {
            inserted = config;
            return true;
        }

        @Override public boolean updateById(AiClientApiConfig config) { return true; }
        @Override public boolean updateByApiId(AiClientApiConfig config) { return true; }
        @Override public boolean deleteById(Long id) { return true; }
        @Override public boolean deleteByApiId(String apiId) { return true; }
        @Override public Optional<AiClientApiConfig> findById(Long id) { return Optional.empty(); }
        @Override public Optional<AiClientApiConfig> findByApiId(String apiId) { return Optional.ofNullable(existing); }
        @Override public List<AiClientApiConfig> findEnabled() { return List.of(); }
        @Override public List<AiClientApiConfig> findAll() { return List.of(); }
    }

    private static final class InMemoryModelRepository implements IAiClientModelConfigRepository {
        private AiClientModelConfig existing;
        private boolean insertResult = true;

        @Override public boolean insert(AiClientModelConfig config) { return insertResult; }
        @Override public boolean updateById(AiClientModelConfig config) { return true; }
        @Override public boolean updateByModelId(AiClientModelConfig config) { return true; }
        @Override public boolean deleteById(Long id) { return true; }
        @Override public boolean deleteByModelId(String modelId) { return true; }
        @Override public Optional<AiClientModelConfig> findById(Long id) { return Optional.empty(); }
        @Override public Optional<AiClientModelConfig> findByModelId(String modelId) { return Optional.ofNullable(existing); }
        @Override public List<AiClientModelConfig> findAllByModelId(String modelId) {
            return existing == null ? List.of() : List.of(existing);
        }
        @Override public List<AiClientModelConfig> findByApiId(String apiId) { return List.of(); }
        @Override public List<AiClientModelConfig> findByModelType(String modelType) { return List.of(); }
        @Override public List<AiClientModelConfig> findEnabled() { return List.of(); }
        @Override public List<AiClientModelConfig> findAll() { return List.of(); }
    }
}
