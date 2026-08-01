package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.api.dto.AiClientQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientRequestDTO;
import org.wwz.ai.api.dto.AiClientResponseDTO;
import org.wwz.ai.application.agent.admin.AiClientAdminApplicationService;
import org.wwz.ai.domain.agent.adapter.repository.IAiClientConfigRepository;
import org.wwz.ai.domain.agent.model.valobj.AiClientAdminRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * AiClient Admin 应用服务仓储编排回归。
 */
public class AiClientAdminApplicationServiceTest {

    @Test
    public void shouldCreateAndQueryThroughRepositoryPort() {
        InMemoryAiClientConfigRepository repository = new InMemoryAiClientConfigRepository();
        AiClientAdminApplicationService service = new AiClientAdminApplicationService(repository);

        boolean created = service.create(AiClientRequestDTO.builder()
                .clientId("client-1")
                .clientName("demo")
                .description("test")
                .status(1)
                .build());
        Assert.assertTrue(created);

        AiClientResponseDTO found = service.queryByClientId("client-1");
        Assert.assertNotNull(found);
        Assert.assertEquals("demo", found.getClientName());
        Assert.assertEquals(Integer.valueOf(1), found.getStatus());

        List<AiClientResponseDTO> page = service.queryList(AiClientQueryRequestDTO.builder()
                .status(1)
                .pageNum(1)
                .pageSize(10)
                .build());
        Assert.assertEquals(1, page.size());
    }

    private static final class InMemoryAiClientConfigRepository implements IAiClientConfigRepository {
        private final AtomicLong idSeq = new AtomicLong(1);
        private final List<AiClientAdminRecord> store = new ArrayList<>();

        @Override
        public boolean insert(AiClientAdminRecord config) {
            config.setId(idSeq.getAndIncrement());
            store.add(copy(config));
            return true;
        }

        @Override
        public boolean updateById(AiClientAdminRecord config) {
            return false;
        }

        @Override
        public boolean updateByClientId(AiClientAdminRecord config) {
            return false;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public boolean deleteByClientId(String clientId) {
            return false;
        }

        @Override
        public Optional<AiClientAdminRecord> findById(Long id) {
            return store.stream().filter(item -> id.equals(item.getId())).map(this::copy).findFirst();
        }

        @Override
        public Optional<AiClientAdminRecord> findByClientId(String clientId) {
            return store.stream().filter(item -> clientId.equals(item.getClientId())).map(this::copy).findFirst();
        }

        @Override
        public List<AiClientAdminRecord> findEnabled() {
            return store.stream()
                    .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                    .map(this::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public List<AiClientAdminRecord> findByClientName(String clientName) {
            return store.stream()
                    .filter(item -> clientName.equals(item.getClientName()))
                    .map(this::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public List<AiClientAdminRecord> findAll() {
            return store.stream().map(this::copy).collect(Collectors.toList());
        }

        private AiClientAdminRecord copy(AiClientAdminRecord source) {
            return AiClientAdminRecord.builder()
                    .id(source.getId())
                    .clientId(source.getClientId())
                    .clientName(source.getClientName())
                    .description(source.getDescription())
                    .status(source.getStatus())
                    .createTime(source.getCreateTime())
                    .updateTime(source.getUpdateTime())
                    .build();
        }
    }
}
