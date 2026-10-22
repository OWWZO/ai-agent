package org.wwz.ai.domain.agent.rag.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingRequest;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;

import java.util.List;

/**
 * Domain-facing embedding facade kept for the startup runner compatibility contract.
 */
@Service
public class TextEmbeddingService {

    private TextEmbeddingPort textEmbeddingPort;

    public TextEmbeddingService() {
    }

    @Autowired
    public TextEmbeddingService(TextEmbeddingPort textEmbeddingPort) {
        this.textEmbeddingPort = textEmbeddingPort;
    }

    public List<List<Float>> getVectorBatch(List<String> texts) {
        if (textEmbeddingPort == null) {
            return null;
        }
        try {
            TextEmbeddingResult result = textEmbeddingPort.embed(new TextEmbeddingRequest(texts, true));
            return result == null || !result.isAvailable() ? null : result.getVectors();
        } catch (Exception e) {
            return null;
        }
    }

    public List<Float> getVector(String text) {
        List<List<Float>> vectorBatch = getVectorBatch(List.of(text));
        if (vectorBatch != null && !vectorBatch.isEmpty()) {
            return vectorBatch.get(0);
        }
        return null;
    }

    public boolean healthCheck() {
        List<Float> vector = getVector("health_check");
        return vector != null && !vector.isEmpty();
    }

    public void setTextEmbeddingPort(TextEmbeddingPort textEmbeddingPort) {
        this.textEmbeddingPort = textEmbeddingPort;
    }
}
