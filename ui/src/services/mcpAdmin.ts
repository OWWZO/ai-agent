import api from "./index";

export type McpRecord = {
  id?: number;
  mcpId: string;
  mcpName: string;
  transportType: string;
  transportConfig?: string;
  requestTimeout?: number;
  status?: number;
};

const BASE = "/api/v1/admin/ai-client-tool-mcp";

export const mcpAdminApi = {
  list: () =>
    api.get<McpRecord[]>(`${BASE}/query-all`) as unknown as Promise<McpRecord[]>,

  create: (payload: McpRecord) =>
    api.post<boolean>(`${BASE}/create`, payload) as unknown as Promise<boolean>,

  update: (payload: McpRecord) =>
    api.put<boolean>(
      `${BASE}/update-by-mcp-id`,
      payload
    ) as unknown as Promise<boolean>,

  remove: (mcpId: string) =>
    api.delete<boolean>(
      `${BASE}/delete-by-mcp-id/${encodeURIComponent(mcpId)}`
    ) as unknown as Promise<boolean>,
};
