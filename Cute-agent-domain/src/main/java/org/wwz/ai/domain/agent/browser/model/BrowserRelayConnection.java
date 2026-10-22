package org.wwz.ai.domain.agent.browser.model;

/**
 * Transport-neutral channel owned by the inbound adapter.
 *
 * <p>Only typed commands cross this boundary. The implementation may wrap a
 * WebSocket, but that transport type never appears in this contract.</p>
 */
public interface BrowserRelayConnection {

    String userId();

    String id();

    boolean isOpen();

    void send(BrowserCommand command) throws Exception;

    void terminate() throws Exception;
}
