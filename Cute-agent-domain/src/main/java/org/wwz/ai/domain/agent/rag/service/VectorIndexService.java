package org.wwz.ai.domain.agent.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.rag.model.vector.VectorFilter;
import org.wwz.ai.domain.agent.rag.port.VectorIndexAdminPort;

import java.util.concurrent.ExecutionException;

/**
 * Domain-facing vector index facade kept for startup and metadata compatibility callers.
 */
@Service
@RequiredArgsConstructor
public class VectorIndexService {

    private final VectorIndexAdminPort vectorIndexAdminPort;

    public boolean isCollectionExist(String collectionName) throws ExecutionException, InterruptedException {
        try {
            return requireIndexPort().collectionExists(collectionName);
        } catch (Exception e) {
            throw asExecutionException(e);
        }
    }

    public void createCosineCollection(String collectionName, int dimension)
            throws ExecutionException, InterruptedException {
        try {
            requireIndexPort().createCollection(collectionName, dimension);
        } catch (Exception e) {
            throw asExecutionException(e);
        }
    }

    public void recreateCosineCollection(String collectionName, int dimension)
            throws ExecutionException, InterruptedException {
        try {
            requireIndexPort().recreateCollection(collectionName, dimension);
        } catch (Exception e) {
            throw asExecutionException(e);
        }
    }

    public void deleteByFilterSync(String collectionName, VectorFilter filter)
            throws ExecutionException, InterruptedException {
        try {
            requireIndexPort().deleteByFilter(collectionName, filter);
        } catch (Exception e) {
            throw asExecutionException(e);
        }
    }

    private VectorIndexAdminPort requireIndexPort() {
        if (vectorIndexAdminPort == null) {
            throw new IllegalStateException("Vector index port is unavailable");
        }
        return vectorIndexAdminPort;
    }

    private ExecutionException asExecutionException(Exception exception) throws InterruptedException {
        if (exception instanceof InterruptedException interruptedException) {
            throw interruptedException;
        }
        if (exception instanceof ExecutionException executionException) {
            return executionException;
        }
        if (exception instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        return new ExecutionException(exception);
    }

}
