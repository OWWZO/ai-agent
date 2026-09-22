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
};

export const browserRelayApi = {
  status: () => api.get<BrowserRelayStatus>("/api/agent/browser/status") as unknown as Promise<BrowserRelayStatus>,
  pairing: () => api.post<BrowserPairing>("/api/agent/browser/pairing") as unknown as Promise<BrowserPairing>,
  disconnect: () => api.post<void>("/api/agent/browser/disconnect") as unknown as Promise<void>,
};
