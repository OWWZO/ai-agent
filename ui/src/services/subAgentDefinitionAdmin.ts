import api from "./index";

export interface SubAgentDefinitionRecord {
  agentKey: string;
  displayName?: string;
  whenToUse: string;
  systemPrompt: string;
  allowedTools?: string[];
  disallowedTools?: string[];
  maxSteps?: number | null;
  status?: number;
}

export interface SubAgentDefinitionUpsertPayload {
  agentKey: string;
  displayName?: string;
  whenToUse: string;
  systemPrompt: string;
  allowedTools?: string[];
  disallowedTools?: string[];
  maxSteps?: number | null;
  status?: number;
}

export const subAgentDefinitionAdminApi = {
  queryList: () =>
    api.get<SubAgentDefinitionRecord[]>(
      "/api/v1/admin/sub-agent-definitions/query-list"
    ) as unknown as Promise<SubAgentDefinitionRecord[]>,

  get: (agentKey: string) =>
    api.get<SubAgentDefinitionRecord>(
      `/api/v1/admin/sub-agent-definitions/${encodeURIComponent(agentKey)}`
    ) as unknown as Promise<SubAgentDefinitionRecord>,

  toolCatalog: () =>
    api.get<string[]>(
      "/api/v1/admin/sub-agent-definitions/tool-catalog"
    ) as unknown as Promise<string[]>,

  create: (payload: SubAgentDefinitionUpsertPayload) =>
    api.post<boolean>(
      "/api/v1/admin/sub-agent-definitions/create",
      payload
    ) as unknown as Promise<boolean>,

  update: (payload: SubAgentDefinitionUpsertPayload) =>
    api.put<boolean>(
      "/api/v1/admin/sub-agent-definitions/update",
      payload
    ) as unknown as Promise<boolean>,

  remove: (agentKey: string) =>
    api.delete<boolean>(
      "/api/v1/admin/sub-agent-definitions/delete",
      { agentKey }
    ) as unknown as Promise<boolean>,

  reload: () =>
    api.post<number>(
      "/api/v1/admin/sub-agent-definitions/reload"
    ) as unknown as Promise<number>,
};
