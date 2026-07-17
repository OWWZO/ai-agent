import { trimTrailingSlash } from "@/pages/WorkspaceImageGeneration/utils";
import type {
  SopItem,
  SopRecallTestResult,
  SopStatus,
  SopStep,
} from "@/pages/WorkspaceSop/types";
import { normalizeStatus } from "@/pages/WorkspaceSop/utils";
import { normalizeToolBaseUrlForBrowser } from "@/utils/fileUrl";

type WrappedResponse<T> = {
  code?: number | string;
  msg?: string;
  message?: string;
  detail?: string;
  data?: T;
};

type RawSopStep = {
  title?: string;
  steps?: string[];
};

type RawSopItem = {
  sop_id?: string;
  sopId?: string;
  sop_name?: string;
  sopName?: string;
  sop_desc?: string;
  sopDesc?: string;
  sop_type?: string;
  sopType?: string;
  sop_steps?: RawSopStep[];
  sopSteps?: RawSopStep[];
  status?: string;
  sop_string?: string;
  created_at?: string | null;
  updated_at?: string | null;
};

type RawRecallHit = {
  sop_id?: string;
  sop_name?: string;
  score?: number | null;
  status?: string | null;
};

type RawRecallTest = {
  sop_mode?: string;
  choosed_sop_string?: string;
  hits?: RawRecallHit[];
};

export class SopWorkspaceRequestError extends Error {
  status?: number;
  rawResponse?: unknown;

  constructor(message: string, options?: { status?: number; rawResponse?: unknown }) {
    super(message);
    this.name = "SopWorkspaceRequestError";
    this.status = options?.status;
    this.rawResponse = options?.rawResponse;
  }
}

function normalizeToolBaseUrl(toolBaseUrl: string): string {
  return normalizeToolBaseUrlForBrowser(trimTrailingSlash(toolBaseUrl || ""));
}

function toRecord(value: unknown): Record<string, unknown> {
  if (value && typeof value === "object") {
    return value as Record<string, unknown>;
  }
  return {};
}

function resolveResponseMessage(rawResponse: unknown, fallbackMessage: string): string {
  if (typeof rawResponse === "string" && rawResponse.trim()) {
    return rawResponse;
  }
  const record = toRecord(rawResponse);
  const message = record.message || record.detail || record.msg || record.info;
  if (typeof message === "string" && message.trim()) {
    return message;
  }
  return fallbackMessage;
}

async function parseResponseBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function mapStep(raw: RawSopStep | undefined): SopStep {
  return {
    title: String(raw?.title || ""),
    steps: Array.isArray(raw?.steps)
      ? raw!.steps!.map((item) => String(item || ""))
      : [],
  };
}

function mapSopItem(raw: RawSopItem | null | undefined): SopItem {
  const steps = raw?.sop_steps || raw?.sopSteps || [];
  return {
    sopId: String(raw?.sop_id || raw?.sopId || ""),
    sopName: String(raw?.sop_name || raw?.sopName || ""),
    sopDesc: String(raw?.sop_desc || raw?.sopDesc || ""),
    sopType: String(raw?.sop_type || raw?.sopType || "list"),
    sopSteps: Array.isArray(steps) ? steps.map(mapStep) : [],
    status: normalizeStatus(raw?.status),
    sopString: raw?.sop_string ? String(raw.sop_string) : undefined,
    createdAt: raw?.created_at ?? null,
    updatedAt: raw?.updated_at ?? null,
  };
}

async function requestWrappedData<T>(
  toolBaseUrl: string,
  path: string,
  body: Record<string, unknown>
): Promise<T> {
  const response = await fetch(`${normalizeToolBaseUrl(toolBaseUrl)}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(body),
  });
  const raw = await parseResponseBody(response);
  if (!response.ok) {
    throw new SopWorkspaceRequestError(
      resolveResponseMessage(raw, `请求失败 (${response.status})`),
      { status: response.status, rawResponse: raw }
    );
  }
  const wrapped = raw as WrappedResponse<T>;
  const code = wrapped?.code;
  if (code !== undefined && String(code) !== "200") {
    throw new SopWorkspaceRequestError(
      resolveResponseMessage(raw, `业务失败 code=${code}`),
      { rawResponse: raw }
    );
  }
  return (wrapped?.data ?? (raw as T)) as T;
}

export async function listSops(
  toolBaseUrl: string,
  payload?: { keyword?: string; status?: string; limit?: number }
): Promise<SopItem[]> {
  const data = await requestWrappedData<{ list?: RawSopItem[] }>(
    toolBaseUrl,
    "/v1/sop/list",
    {
      requestId: `sop-list-${Date.now()}`,
      keyword: payload?.keyword || "",
      status: payload?.status || null,
      limit: payload?.limit || 200,
    }
  );
  return (data?.list || []).map(mapSopItem).filter((item) => item.sopId);
}

export async function getSop(toolBaseUrl: string, sopId: string): Promise<SopItem> {
  const data = await requestWrappedData<RawSopItem>(toolBaseUrl, "/v1/sop/get", {
    requestId: `sop-get-${Date.now()}`,
    sopId,
  });
  return mapSopItem(data);
}

export async function upsertSop(
  toolBaseUrl: string,
  payload: {
    sopId?: string | null;
    sopName: string;
    sopDesc: string;
    sopType?: string;
    sopSteps: SopStep[];
    status: SopStatus;
  }
): Promise<SopItem> {
  const data = await requestWrappedData<RawSopItem>(toolBaseUrl, "/v1/sop/upsert", {
    requestId: `sop-upsert-${Date.now()}`,
    sopId: payload.sopId || null,
    sopName: payload.sopName,
    sopDesc: payload.sopDesc,
    sopType: payload.sopType || "list",
    sopSteps: payload.sopSteps,
    status: payload.status,
  });
  return mapSopItem(data);
}

export async function deleteSop(toolBaseUrl: string, sopId: string): Promise<void> {
  await requestWrappedData(toolBaseUrl, "/v1/sop/delete", {
    requestId: `sop-delete-${Date.now()}`,
    sopId,
  });
}

export async function setSopStatus(
  toolBaseUrl: string,
  sopId: string,
  status: SopStatus
): Promise<SopItem> {
  const data = await requestWrappedData<RawSopItem>(toolBaseUrl, "/v1/sop/status", {
    requestId: `sop-status-${Date.now()}`,
    sopId,
    status,
  });
  return mapSopItem(data);
}

export async function recallTestSop(
  toolBaseUrl: string,
  query: string
): Promise<SopRecallTestResult> {
  const data = await requestWrappedData<RawRecallTest>(
    toolBaseUrl,
    "/v1/sop/recall_test",
    {
      requestId: `sop-recall-${Date.now()}`,
      query,
    }
  );
  return {
    sopMode: String(data?.sop_mode || ""),
    choosedSopString: String(data?.choosed_sop_string || ""),
    hits: (data?.hits || []).map((hit) => ({
      sopId: String(hit.sop_id || ""),
      sopName: String(hit.sop_name || ""),
      score: hit.score ?? null,
      status: hit.status ?? null,
    })),
  };
}
