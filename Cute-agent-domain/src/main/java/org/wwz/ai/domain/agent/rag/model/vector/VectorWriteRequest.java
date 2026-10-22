package org.wwz.ai.domain.agent.rag.model.vector;

import java.util.List;
import java.util.Map;

/**
 * Domain-level vector write input. Legacy request mapping stays outside Domain.
 */
public final class VectorWriteRequest {

    private final String collectionName;
    private final List<Item> items;

    public VectorWriteRequest(String collectionName, List<Item> items) {
        this.collectionName = collectionName;
        this.items = List.copyOf(items == null ? List.of() : items);
    }

    public String getCollectionName() {
        return collectionName;
    }

    public List<Item> getItems() {
        return items;
    }

    public static final class Item {

        private final String embeddingText;
        private final String id;
        private final Map<String, Object> payload;

        public Item(String embeddingText, String id, Map<String, Object> payload) {
            this.embeddingText = embeddingText;
            this.id = id;
            this.payload = Map.copyOf(payload == null ? Map.of() : payload);
        }

        public String getEmbeddingText() {
            return embeddingText;
        }

        public String getId() {
            return id;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }
    }
}
