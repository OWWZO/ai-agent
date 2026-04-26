import { fetchEventSource } from "@microsoft/fetch-event-source";

import type {
  KnowledgeBase,
  KnowledgeBaseFile,
  MRagChunkEnvelope,
  MRagFileStatus,
  MRagSourceType,
  UploadDocumentResult,
} from "@/pages/WorkspaceMRag/types";
import { trimTrailingSlash } from "@/pages/WorkspaceImageGeneration/utils";

type WrappedResponse<T> = {
  code?: number | string;
  msg?: string;
  message?: string;
  detail?: string;
  data?: T;
};

type UploadResponse = {
  success?: boolean;
  message?: string;
  detail?: string;
  data?: RawUploadDocument;
};

type RawKnowledgeBase = {
  kb_id?: string;
  kb_name?: string;
  kb_desc?: string;
  chunk_type?: string;
  chunk_size?: number | null;
  chunk_overlap_size?: number | null;
  create_time?: string | null;
  modify_time?: string | null;
  [key: string]: unknown;
};

type RawKnowledgeBaseFile = {
  kb_id?: string;
  file_id?: string;
  file_url?: string;
  title?: string;
  file_ext?: string;
  source_type?: string;
  task_status?: Record<string, unknown> | null;
  file_status?: string;
  doc_count?: number | null;
  create_time?: string | null;
  modify_time?: string | null;
  [key: string]: unknown;
};

type RawUploadDocument = {
  document_id?: string;
  filename?: string;
  original_filename?: string;
  preview_url?: string;
  permanent_url?: string;
  presigned_url?: string;
  storage_type?: string;
  content_type?: string;
  file_size?: number;
  upload_time?: string;
  [key: string]: unknown;
};

type ListKnowledgeBasePayload = {
  list?: RawKnowledgeBase[];
};

type ListKnowledgeBaseFilesPayload = {
  records?: RawKnowledgeBaseFile[];
};

type CreateKnowledgeBasePayload = {
  kbName: string;
  kbDesc?: string;
};

type DeleteKnowledgeBaseFilesPayload = {
  kbId: string;
  fileIds: string[];
};

type AddWebUrlPayload = {
  kbId: string;
  url: string;
};

type AddUploadedFilesPayload = {
  kbId: string;
  uploads: UploadDocumentResult[];
};

type StreamMragQueryPayload = {
  toolBaseUrl: string;
  kbId?: string;
  question: string;
  imageUrls?: string[];
  signal?: AbortSignal;
  onChunk: (chunk: MRagChunkEnvelope) => void;
};

type JsonValue = Record<string, unknown> | string;

export class MRagWorkspaceRequestError extends Error {
  status?: number;
  rawResponse?: unknown;

  constructor(message: string, options?: { status?: number; rawResponse?: unknown }) {
    super(message);
    this.name = "MRagWorkspaceRequestError";
    this.status = options?.status;
    this.rawResponse = options?.rawResponse;
  }
}

function normalizeToolBaseUrl(toolBaseUrl: string): string {
  return trimTrailingSlash(toolBaseUrl || "");
}

function toRecord(value: unknown): Record<string, unknown> {
  if (value && typeof value === "object") {
    return value as Record<string, unknown>;
  }
  return {};
}

function resolveResponseMessage(
  rawResponse: unknown,
  fallbackMessage: string
): string {
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

async function parseResponseBody(response: Response): Promise<JsonValue> {
  const rawText = await response.text();
  if (!rawText) {
    return "";
  }

  try {
    return JSON.parse(rawText) as Record<string, unknown>;
  } catch {
    return rawText;
  }
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

  const rawResponse = await parseResponseBody(response);
  if (!response.ok) {
    throw new MRagWorkspaceRequestError(
      resolveResponseMessage(rawResponse, `${response.status} ${response.statusText}`),
      {
        status: response.status,
        rawResponse
      }
    );
  }

  const wrapped = toRecord(rawResponse) as WrappedResponse<T>;
  if (Number(wrapped.code) !== 200) {
    throw new MRagWorkspaceRequestError(
      resolveResponseMessage(rawResponse, "MRAG 请求失败"),
      {
        status: response.status,
        rawResponse
      }
    );
  }

  return (wrapped.data as T) ?? ({} as T);
}

function inferSourceType(rawFile: RawKnowledgeBaseFile): MRagSourceType {
  const sourceType = String(rawFile.source_type || "").toLowerCase();
  if (sourceType === "url") {
    return "url";
  }
  if (sourceType === "file") {
    const title = String(rawFile.title || "").trim();
    if (!title) {
      return "url";
    }
    return "file";
  }

  return String(rawFile.title || "").trim() ? "file" : "url";
}

function normalizeStatus(status?: string | null): MRagFileStatus {
  const normalized = String(status || "").toUpperCase();
  if (
    normalized === "PENDING" ||
    normalized === "RUNNING" ||
    normalized === "SUCCESS" ||
    normalized === "FAILED"
  ) {
    return normalized;
  }
  return "UNKNOWN";
}

function swapUrlSegment(url: string, fromSegment: string, toSegment: string): string {
  if (!url || !url.includes(`/${fromSegment}/`)) {
    return url;
  }

  try {
    const parsed = new URL(url, "https://workspace.local");
    parsed.pathname = parsed.pathname.replace(`/${fromSegment}/`, `/${toSegment}/`);
    if (parsed.origin === "https://workspace.local") {
      return `${parsed.pathname}${parsed.search}${parsed.hash}`;
    }
    return parsed.toString();
  } catch {
    return url.replace(`/${fromSegment}/`, `/${toSegment}/`);
  }
}

function resolveDisplayTitle(rawFile: RawKnowledgeBaseFile): string {
  const title = String(rawFile.title || "").trim();
  if (title) {
    return title;
  }

  const url = String(rawFile.file_url || "").trim();
  if (!url) {
    return "未命名文件";
  }

  try {
    const parsed = new URL(url);
    return decodeURIComponent(parsed.pathname.split("/").filter(Boolean).pop() || parsed.hostname);
  } catch {
    return url;
  }
}

export function normalizeKnowledgeBase(rawKnowledgeBase: RawKnowledgeBase): KnowledgeBase {
  return {
    id: String(rawKnowledgeBase.kb_id || ""),
    name: String(rawKnowledgeBase.kb_name || rawKnowledgeBase.kb_id || "未命名知识库"),
    description: String(rawKnowledgeBase.kb_desc || ""),
    chunkType: String(rawKnowledgeBase.chunk_type || "fixed_size"),
    chunkSize: rawKnowledgeBase.chunk_size ?? null,
    chunkOverlapSize: rawKnowledgeBase.chunk_overlap_size ?? null,
    createdAt: rawKnowledgeBase.create_time ?? null,
    updatedAt: rawKnowledgeBase.modify_time ?? null,
    raw: toRecord(rawKnowledgeBase),
  };
}

export function resolveUploadedFileUrl(upload: UploadDocumentResult): string {
  if (upload.storageType === "local" && upload.previewUrl) {
    return upload.previewUrl;
  }
  return upload.previewUrl || upload.permanentUrl || upload.presignedUrl || "";
}

export function resolveWorkspacePreviewUrl(fileUrl: string): string {
  if (!fileUrl) {
    return "";
  }
  return swapUrlSegment(fileUrl, "download", "preview");
}

export function resolveWorkspaceDownloadUrl(fileUrl: string): string {
  if (!fileUrl) {
    return "";
  }
  return swapUrlSegment(fileUrl, "preview", "download");
}

export function normalizeKnowledgeBaseFile(rawFile: RawKnowledgeBaseFile): KnowledgeBaseFile {
  const sourceType = inferSourceType(rawFile);
  const sourceUrl = String(rawFile.file_url || "");
  const taskStatus = toRecord(rawFile.task_status);
  const fileStatus = normalizeStatus(
    String(taskStatus.global_status || rawFile.file_status || "")
  );
  const previewUrl = sourceType === "url" ? sourceUrl : resolveWorkspacePreviewUrl(sourceUrl);
  const downloadUrl =
    sourceType === "url" ? sourceUrl : resolveWorkspaceDownloadUrl(sourceUrl);

  return {
    id: String(rawFile.file_id || ""),
    knowledgeBaseId: String(rawFile.kb_id || ""),
    title: resolveDisplayTitle(rawFile),
    sourceType,
    sourceUrl,
    fileUrl: sourceUrl,
    previewUrl,
    downloadUrl,
    fileExt: String(rawFile.file_ext || "").toLowerCase(),
    fileStatus,
    taskStatus,
    docCount: Number(rawFile.doc_count || 0),
    createdAt: rawFile.create_time ?? null,
    updatedAt: rawFile.modify_time ?? null,
    errorMessage: String(taskStatus.error_message || ""),
    raw: toRecord(rawFile),
  };
}

export function hasProcessingFiles(files: KnowledgeBaseFile[]): boolean {
  return files.some(
    (file) => file.fileStatus === "PENDING" || file.fileStatus === "RUNNING"
  );
}

export function extractMragChunkContent(payload: unknown): string {
  const record = toRecord(payload);
  const choices = Array.isArray(record.choices) ? record.choices : [];
  const firstChoice = toRecord(choices[0]);
  const delta = toRecord(firstChoice.delta);
  const message = toRecord(firstChoice.message);

  if (typeof delta.content === "string") {
    return delta.content;
  }
  if (typeof message.content === "string") {
    return message.content;
  }
  if (typeof record.data === "string") {
    return record.data;
  }
  return "";
}

function extractMragFinishReason(payload: unknown): string | null {
  const record = toRecord(payload);
  const choices = Array.isArray(record.choices) ? record.choices : [];
  const firstChoice = toRecord(choices[0]);
  const finishReason = firstChoice.finishReason || firstChoice.finish_reason;
  return typeof finishReason === "string" ? finishReason : null;
}

export function mapMragError(error: unknown): string {
  if (error instanceof MRagWorkspaceRequestError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return resolveResponseMessage(error, "MRAG 请求失败，请稍后重试。");
}

export async function listKnowledgeBases(toolBaseUrl: string): Promise<KnowledgeBase[]> {
  const payload = await requestWrappedData<ListKnowledgeBasePayload>(
    toolBaseUrl,
    "/v1/documents/list_knowledge_base",
    {
      page_no: 1,
      page_size: 100,
    }
  );

  return (payload.list || []).map(normalizeKnowledgeBase);
}

export async function createKnowledgeBase(
  toolBaseUrl: string,
  payload: CreateKnowledgeBasePayload
): Promise<KnowledgeBase> {
  const created = await requestWrappedData<RawKnowledgeBase>(
    toolBaseUrl,
    "/v1/documents/create_knowledge_base",
    {
      kb_name: payload.kbName,
      kb_desc: payload.kbDesc || "",
    }
  );

  return normalizeKnowledgeBase(created);
}

export async function listKnowledgeBaseFiles(
  toolBaseUrl: string,
  kbId: string
): Promise<KnowledgeBaseFile[]> {
  const payload = await requestWrappedData<ListKnowledgeBaseFilesPayload>(
    toolBaseUrl,
    "/v1/documents/list_kb_files",
    {
      kb_id: kbId,
      page_no: 1,
      page_size: 100,
    }
  );

  return (payload.records || []).map(normalizeKnowledgeBaseFile);
}

export async function deleteKnowledgeBaseFiles(
  toolBaseUrl: string,
  payload: DeleteKnowledgeBaseFilesPayload
): Promise<void> {
  await requestWrappedData<Record<string, never>>(
    toolBaseUrl,
    "/v1/documents/delete_files",
    {
      kb_id: payload.kbId,
      file_ids: payload.fileIds,
    }
  );
}

export async function addWebUrlToKnowledgeBase(
  toolBaseUrl: string,
  payload: AddWebUrlPayload
): Promise<void> {
  await requestWrappedData<Record<string, never>>(
    toolBaseUrl,
    "/v1/documents/add_web_url",
    {
      kb_id: payload.kbId,
      url: payload.url,
    }
  );
}

export async function uploadDocument(
  toolBaseUrl: string,
  file: File
): Promise<UploadDocumentResult> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${normalizeToolBaseUrl(toolBaseUrl)}/v1/documents/upload`, {
    method: "POST",
    body: formData,
  });

  const rawResponse = await parseResponseBody(response);
  if (!response.ok) {
    throw new MRagWorkspaceRequestError(
      resolveResponseMessage(rawResponse, `${response.status} ${response.statusText}`),
      {
        status: response.status,
        rawResponse
      }
    );
  }

  const wrapped = toRecord(rawResponse) as UploadResponse;
  if (!wrapped.success || !wrapped.data) {
    throw new MRagWorkspaceRequestError(
      resolveResponseMessage(rawResponse, "文件上传失败"),
      {
        status: response.status,
        rawResponse
      }
    );
  }

  const rawUpload = wrapped.data;
  return {
    documentId: String(rawUpload.document_id || ""),
    filename: String(rawUpload.filename || ""),
    originalFilename: String(rawUpload.original_filename || rawUpload.filename || ""),
    previewUrl: String(rawUpload.preview_url || ""),
    permanentUrl: String(rawUpload.permanent_url || ""),
    presignedUrl: String(rawUpload.presigned_url || ""),
    storageType: String(rawUpload.storage_type || ""),
    contentType: String(rawUpload.content_type || ""),
    fileSize: Number(rawUpload.file_size || 0),
    uploadTime: String(rawUpload.upload_time || ""),
    raw: toRecord(rawUpload),
  };
}

export async function addUploadedFilesToKnowledgeBase(
  toolBaseUrl: string,
  payload: AddUploadedFilesPayload
): Promise<void> {
  await requestWrappedData<Record<string, never>>(
    toolBaseUrl,
    "/v1/documents/add_files",
    {
      kb_id: payload.kbId,
      files: payload.uploads.map((upload) => ({
        filename: upload.filename,
        file_url: resolveUploadedFileUrl(upload),
        file_type: "file",
      })),
    }
  );
}

export async function ingestLocalFilesToKnowledgeBase(
  toolBaseUrl: string,
  kbId: string,
  files: File[]
): Promise<UploadDocumentResult[]> {
  const uploads = await Promise.all(files.map((file) => uploadDocument(toolBaseUrl, file)));
  await addUploadedFilesToKnowledgeBase(toolBaseUrl, {
    kbId,
    uploads
  });
  return uploads;
}

export async function streamMragQuery(
  payload: StreamMragQueryPayload
): Promise<void> {
  const url = `${normalizeToolBaseUrl(payload.toolBaseUrl)}/v1/tool/mragQuery`;
  let receivedDone = false;
  let streamClosed = false;

  await fetchEventSource(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      "Cache-Control": "no-cache",
    },
    body: JSON.stringify({
      question: payload.question,
      image_urls: payload.imageUrls || [],
      kb_id: payload.kbId || undefined,
    }),
    signal: payload.signal,
    openWhenHidden: true,
    onmessage(event) {
      if (!event.data || event.data === "heartbeat") {
        return;
      }

      if (event.data === "[DONE]") {
        receivedDone = true;
        return;
      }

      let parsedPayload: unknown = event.data;
      try {
        parsedPayload = JSON.parse(event.data);
      } catch {
        throw new MRagWorkspaceRequestError("MRAG SSE 消息解析失败", {rawResponse: event.data,});
      }

      payload.onChunk({
        raw: parsedPayload,
        content: extractMragChunkContent(parsedPayload),
        finishReason: extractMragFinishReason(parsedPayload),
      });
    },
    onclose() {
      streamClosed = true;
    },
    onerror(error) {
      if (payload.signal?.aborted) {
        return;
      }
      throw error instanceof Error
        ? error
        : new MRagWorkspaceRequestError("MRAG 流式请求失败", { rawResponse: error });
    },
  });

  if (!receivedDone && streamClosed && !payload.signal?.aborted) {
    throw new MRagWorkspaceRequestError("MRAG 连接在完成前意外关闭");
  }
}
