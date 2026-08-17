import api from "./index";

export type CapabilityItem = {
  refId: string;
  name: string;
  enabled: boolean;
  source?: string;
};

export type SessionCapabilities = {
  locked: boolean;
  skills: CapabilityItem[];
  mcpServers: CapabilityItem[];
};

export const sessionCapabilityApi = {
  get: (sessionId: string) =>
    api.get<SessionCapabilities>(
      `/api/agent/session/${encodeURIComponent(sessionId)}/capabilities`
    ) as unknown as Promise<SessionCapabilities>,

  setEnabled: (
    sessionId: string,
    kind: "skill" | "mcp",
    refId: string,
    enabled: boolean
  ) =>
    api.put<boolean>(
      `/api/agent/session/${encodeURIComponent(sessionId)}/capabilities`,
      { kind, refId, enabled }
    ) as unknown as Promise<boolean>,
};
