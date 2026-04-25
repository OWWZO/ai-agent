export type WorkspaceTab = "decode" | "generate";

export type RequestMode = "images" | "edits" | "chat";

export type ToolFileInfo = {
  fileName?: string;
  ossUrl?: string | null;
  domainUrl?: string | null;
  downloadUrl?: string | null;
  previewUrl?: string | null;
  fileSize?: number | null;
  mimeType?: string | null;
};

export type ImageGenerationToolResponse = {
  data: string;
  fileInfo: ToolFileInfo[];
  requestId: string;
  mode?: RequestMode | "images" | "edits";
  usedFallback?: boolean;
  rawResponse?: unknown;
};

export type GenerationConfig = {
  toolBaseUrl: string;
  baseUrl: string;
  apiKey: string;
  model: string;
  mode: RequestMode;
  size: string;
  n: number;
};

export type EditorImageItem = {
  id: string;
  file: File;
  objectUrl: string;
  naturalWidth: number;
  naturalHeight: number;
  maskDataUrl: string | null;
};

export type DecodeResult = {
  dataUrl: string;
  mimeType: string;
  fileExtension: string;
  byteLength: number;
  base64Length: number;
};

export type ExtractedImageHit = {
  dataUrl?: string;
  url?: string;
};

export type ResultImageItem = {
  url: string;
  label: string;
  downloadUrl?: string;
};

export type UserMessage = {
  id: string;
  role: "user";
  prompt: string;
  mode: RequestMode;
  images: string[];
  timestamp: number;
};

export type AssistantMessage = {
  id: string;
  role: "assistant";
  status: "loading" | "done" | "error";
  summary: string;
  text?: string;
  images: ResultImageItem[];
  error?: string;
  rawResponse?: unknown;
  timestamp: number;
};

export type GenerationMessage = UserMessage | AssistantMessage;
