package org.wwz.ai.domain.agent.reactor.data.exception;

/**
 * @author: jinglingtuan
 * @date: 2023/4/26 9:26
 * @version: 1.0
 */
public class CatalogException  extends  RuntimeException{
    public CatalogException(String message) {
        super(message);
    }

    public CatalogException(String message, Throwable e) {
        super(message, e);
    }

    public CatalogException(Throwable e) {
        super(e);
    }

    public CatalogException() {
    }
}
