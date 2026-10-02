import api from "./index";

export type KernelBrowserStatus = {
  exists: boolean;
  kernelSessionId?: string;
  lastUsedAt?: string;
  reconstructed: boolean;
};

export type KernelBrowserLiveView = {
  browserLiveViewUrl: string;
  kernelSessionId: string;
  reconstructed: boolean;
};

export const kernelBrowserApi = {
  status: () => api.get<KernelBrowserStatus>("/api/agent/kernel-browser/status") as unknown as Promise<KernelBrowserStatus>,
  ensure: () => api.post<KernelBrowserLiveView>("/api/agent/kernel-browser/ensure") as unknown as Promise<KernelBrowserLiveView>,
  reset: () => api.delete<void>("/api/agent/kernel-browser/session") as unknown as Promise<void>,
};
