package org.wwz.ai.domain.agent.browser.model;

/**
 * Typed tab metadata reported by a browser relay.
 */
public record BrowserTabMetadata(String url, String title) {

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }
}
