import api from "./index";

/**
 * 模型接入管理 API（对齐后端 AiClientApi / AiClientModel Admin）。
 * 写库后后端 LlmModelCatalog 会 invalidate，下一请求即可热切换。
 */

export interface LlmApiRecord {
  id?: number;
  apiId: string;
  baseUrl: string;
  apiKey?: string;
  completionsPath?: string;
  embeddingsPath?: string;
  status?: number;
}

export interface LlmModelRecord {
  id: number;
  modelId: string;
  apiId: string;
  modelName: string;
  modelType?: string;
  modelUsage?: string;
  /** 0/1 是否支持深度思考 */
  supportsThinking?: number;
  /** 上下文窗口 token */
  contextWindow?: number | null;
  status?: number;
}

export interface LlmModelUpsertPayload {
  /** 配置行主键；更新时使用，新增时为空 */
  id?: number;
  /** 模型引用标识，可与上游模型名相同且允许重复 */
  modelId: string;
  /** 上游模型名（发给厂商的 model） */
  modelName: string;
  baseUrl: string;
  apiKey: string;
  completionsPath?: string;
  embeddingsPath?: string;
  modelType?: string;
  modelUsage?: string;
  supportsThinking?: number;
  contextWindow?: number | null;
  status?: number;
  /** 复用已有 apiId；空则用 api-{modelId}，冲突时自动生成新 ID */
  apiId?: string;
}

type LlmModelWritePayload = Omit<LlmModelUpsertPayload, "baseUrl" | "apiKey">;

export function isFallbackModelUsage(value?: string) {
  const normalized = value?.trim().toLowerCase();
  return (
    normalized === "fallback" ||
    normalized === "backup" ||
    normalized === "备用" ||
    normalized === "备用模型"
  );
}

const API_BASE = "/api/v1/admin/ai-client-api";
const MODEL_BASE = "/api/v1/admin/ai-client-model";

export const llmModelAdminApi = {
  listApis: () =>
    api.get<LlmApiRecord[]>(`${API_BASE}/query-all`) as unknown as Promise<
      LlmApiRecord[]
    >,

  listEnabledApis: () =>
    api.get<LlmApiRecord[]>(`${API_BASE}/query-enabled`) as unknown as Promise<
      LlmApiRecord[]
    >,

  listModels: () =>
    api.get<LlmModelRecord[]>(`${MODEL_BASE}/query-all`) as unknown as Promise<
      LlmModelRecord[]
    >,

  listEnabledModels: () =>
    api.get<LlmModelRecord[]>(
      `${MODEL_BASE}/query-enabled`
    ) as unknown as Promise<LlmModelRecord[]>,

  createApi: (payload: LlmApiRecord) =>
    api.post<boolean>(`${API_BASE}/create`, payload) as unknown as Promise<boolean>,

  updateApi: (payload: LlmApiRecord) =>
    api.put<boolean>(
      `${API_BASE}/update-by-api-id`,
      payload
    ) as unknown as Promise<boolean>,

  deleteApi: (apiId: string) =>
    api.delete<boolean>(
      `${API_BASE}/delete-by-api-id/${encodeURIComponent(apiId)}`
    ) as unknown as Promise<boolean>,

  createModel: (payload: LlmModelWritePayload) =>
    api.post<boolean>(
      `${MODEL_BASE}/create`,
      payload
    ) as unknown as Promise<boolean>,

  updateModel: (payload: LlmModelWritePayload) =>
    api.put<boolean>(
      `${MODEL_BASE}/update-by-id`,
      payload
    ) as unknown as Promise<boolean>,

  deleteModel: (id: number) =>
    api.delete<boolean>(
      `${MODEL_BASE}/delete-by-id/${id}`
    ) as unknown as Promise<boolean>,

  /** 真发一次极小请求测选中的具体模型配置 */
  testConnectionById: (id: number) =>
    api.post<{ ok: boolean; ms: number; message?: string }>(
      `${MODEL_BASE}/test-by-id/${id}`
    ) as unknown as Promise<{ ok: boolean; ms: number; message?: string }>,

  /** 兼容按模型引用测试的接口 */
  testConnection: (modelId: string) =>
    api.post<{ ok: boolean; ms: number; message?: string }>(
      `${MODEL_BASE}/test/${encodeURIComponent(modelId)}`
    ) as unknown as Promise<{ ok: boolean; ms: number; message?: string }>,

  /**
   * 一次提交：写 API 凭据 + 模型绑定。
   * 编辑时若 apiKey 含 • 或为空则先拉旧 API 保留原 key。
   */
  upsertBinding: async (
    payload: LlmModelUpsertPayload,
    options?: { isNew?: boolean; existingApiKey?: string }
  ) => {
    const requestedApiId = (payload.apiId || `api-${payload.modelId}`)
      .trim()
      .slice(0, 64);
    let apiId = requestedApiId;
    if (options?.isNew && !payload.apiId) {
      const existingApis = await llmModelAdminApi.listApis();
      if (existingApis.some((api) => api.apiId === apiId)) {
        const suffix = `-${Date.now().toString(36)}`;
        apiId = `${apiId.slice(0, Math.max(1, 64 - suffix.length))}${suffix}`;
      }
    }
    const keepKey =
      !payload.apiKey ||
      payload.apiKey.includes("•") ||
      payload.apiKey.includes("*");
    const apiKey = keepKey
      ? options?.existingApiKey || payload.apiKey
      : payload.apiKey;

    if (!apiKey?.trim()) {
      throw new Error("API Key 不能为空");
    }

    const apiBody: LlmApiRecord = {
      apiId,
      baseUrl: payload.baseUrl.trim(),
      apiKey: apiKey.trim(),
      completionsPath: payload.completionsPath?.trim() || "/chat/completions",
      embeddingsPath: payload.embeddingsPath?.trim() || "/embeddings",
      status: 1,
    };

    const modelBody: LlmModelWritePayload = {
      id: payload.id,
      modelId: payload.modelId.trim(),
      apiId,
      modelName: payload.modelName.trim(),
      modelType: payload.modelType?.trim() || "openai",
      modelUsage: payload.modelUsage?.trim() || "default",
      supportsThinking: payload.supportsThinking ?? 0,
      contextWindow: payload.contextWindow ?? null,
      status: payload.status ?? 1,
    };

    if (options?.isNew) {
      await llmModelAdminApi.createApi(apiBody);
      await llmModelAdminApi.createModel(modelBody);
    } else {
      if (payload.id == null) {
        throw new Error("模型配置缺少记录 ID");
      }
      try {
        await llmModelAdminApi.updateApi(apiBody);
      } catch {
        await llmModelAdminApi.createApi(apiBody);
      }
      try {
        await llmModelAdminApi.updateModel(modelBody);
      } catch {
        await llmModelAdminApi.createModel(modelBody);
      }
    }
    return {
      apiId,
      modelId: modelBody.modelId,
    };
  },
};
