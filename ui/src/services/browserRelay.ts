import api from "./index";

export type BrowserRelayStatus = {
  connected: boolean;
  tabUrl?: string;
  tabTitle?: string;
};

export type BrowserPairing = {
  token: string;
  code: string;
  relayUrl: string;
  expiresAt: number;
  /** bsk 扩展的配对链接（url#token）；网关未启用时为空。 */
  bskPairingUrl?: string | null;
};

export const browserRelayApi = {
  status: () => api.get<BrowserRelayStatus>("/api/agent/browser/status") as unknown as Promise<BrowserRelayStatus>,
  pairing: () => api.post<BrowserPairing>("/api/agent/browser/pairing") as unknown as Promise<BrowserPairing>,
  disconnect: () => api.post<void>("/api/agent/browser/disconnect") as unknown as Promise<void>,
};
