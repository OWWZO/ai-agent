package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.model.vector.VectorFilter;
import org.wwz.ai.domain.agent.rag.port.VectorIndexAdminPort;
import org.wwz.ai.domain.agent.rag.service.VectorIndexService;

import java.util.Map;

/**
 * Qdrant facade port delegation tests.
 */
public class QdrantServiceCloudClientTest {

    @Test
    public void shouldDelegateCollectionCreationToPort() throws Exception {
        RecordingIndexPort indexPort = new RecordingIndexPort();
        VectorIndexService service = new VectorIndexService(indexPort);

        service.createCosineCollection("reactor_model_schema", 1024);

        Assert.assertEquals("reactor_model_schema", indexPort.collectionName);
        Assert.assertEquals(1024, indexPort.dimension);
    }

    @Test
    public void shouldDelegateTypedFilterWithoutQdrantClientType() throws Exception {
        RecordingIndexPort indexPort = new RecordingIndexPort();
        VectorIndexService service = new VectorIndexService(indexPort);

        service.deleteByFilterSync("reactor_model_schema", new VectorFilter(Map.of("modelCode", "sales")));

        Assert.assertEquals("sales", indexPort.filter.getMust().get("modelCode"));
    }

    private static final class RecordingIndexPort implements VectorIndexAdminPort {
        private String collectionName;
        private int dimension;
        private VectorFilter filter;

        @Override
        public boolean collectionExists(String collectionName) {
            return false;
        }

        @Override
        public void createCollection(String collectionName, int dimension) {
            this.collectionName = collectionName;
            this.dimension = dimension;
        }

        @Override
        public void recreateCollection(String collectionName, int dimension) {
        }

        @Override
        public void upsert(String collectionName,
                           java.util.List<org.wwz.ai.domain.agent.rag.model.vector.VectorPoint> points) {
        }

        @Override
        public void deleteByIds(String collectionName, java.util.List<String> ids) {
        }

        @Override
        public void deleteByFilter(String collectionName, VectorFilter filter) {
            this.filter = filter;
        }
    }
}
