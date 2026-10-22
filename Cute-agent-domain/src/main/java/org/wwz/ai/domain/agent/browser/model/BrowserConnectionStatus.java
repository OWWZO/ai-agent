package org.wwz.ai.domain.agent.browser.model;

/**
 * Browser relay connectivity and current tab metadata for one user.
 */
public final class BrowserConnectionStatus {

    private final boolean connected;
    private final String tabUrl;
    private final String tabTitle;

    public BrowserConnectionStatus(boolean connected, String tabUrl, String tabTitle) {
        this.connected = connected;
        this.tabUrl = tabUrl;
        this.tabTitle = tabTitle;
    }

    public static BrowserConnectionStatus offline() {
        return new BrowserConnectionStatus(false, null, null);
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean getConnected() {
        return connected;
    }

    public String getTabUrl() {
        return tabUrl;
    }

    public String getTabTitle() {
        return tabTitle;
    }
}
