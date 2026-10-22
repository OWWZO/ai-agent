package org.wwz.ai.infrastructure.dataquery.jdbc.catalog;

public class CatalogException extends RuntimeException {
    public CatalogException(String message) {
        super(message);
    }

    public CatalogException(String message, Throwable cause) {
        super(message, cause);
    }

    public CatalogException(Throwable cause) {
        super(cause);
    }

    public CatalogException() {
    }
}
