package org.wwz.ai.domain.agent.rag;

/** Business failure while building or executing an analytical query. */
public class DataQueryException extends RuntimeException {
    public DataQueryException(String message) {
        super(message);
    }

    public DataQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
