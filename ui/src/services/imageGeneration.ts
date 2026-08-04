import api from "./index";
import type {
  ImageGenerationHistoryPage,
  ImageGenerationToolResponse,
} from "@/pages/WorkspaceImageGeneration/types";
import { extractImageFromUnknown, extractTextFromUnknown, trimTrailingSlash } from "@/pages/WorkspaceImageGeneration/utils";

const IMAGE_GENERATION_REQUEST_TIMEOUT_MS = 330_000;

type DirectChatRequest = {
  baseUrl: string;
  apiKey: string;
  model: string;
  prompt: string;
};

type ToolRequest = {
  requestId: string;
  prompt: string;
  mode: "images" | "edits";
  model?: string;
  size: string;
  n: number;
  fileNames: string[];
  maskFileNames: string[];
  fileName?: string;
  fileDescription?: string;
};

export class ImageGenerationRequestError extends Error {
  rawResponse?: unknown;

  constructor(message: string, rawResponse?: unknown) {
    super(message);
    this.name = "ImageGenerationRequestError";
    this.rawResponse = rawResponse;
  }
}

export async function requestImageGenerationTool(
  payload: ToolRequest
): Promise<ImageGenerationToolResponse> {
  // 代理请求沿用统一 api 客户端和长超时，错误统一包装为可识别的领域异常。
  try {
    return (await api.post<ImageGenerationToolResponse>("/api/agent/image-generation/generate", {
      requestId: payload.requestId,
      prompt: payload.prompt,
      mode: payload.mode,
      model: payload.model,
      fileNames: payload.fileNames,
      maskFileNames: payload.maskFileNames,
      fileName: payload.fileName,
      fileDescription: payload.fileDescription,
      size: payload.size,
      n: payload.n,
    }, {
      timeout: IMAGE_GENERATION_REQUEST_TIMEOUT_MS,
    })) as unknown as ImageGenerationToolResponse;
  } catch (error) {
    throw new ImageGenerationRequestError(
      error instanceof Error ? error.message : "请求失败"
    );
  }
}

export async function requestDirectChat(payload: DirectChatRequest) {
  // 直连模式不经过后端 api 封装，因此在这里显式处理鉴权、JSON 解析和 HTTP 状态。
  const response = await fetch(`${trimTrailingSlash(payload.baseUrl)}/v1/chat/completions`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${payload.apiKey}`,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({
      model: payload.model,
      messages: [
        {
          role: "user",
          content: payload.prompt,
        },
      ],
    }),
  });

  const rawText = await response.text();
  // 同时保留原始文本和可解析对象，便于兼容不同 OpenAI-compatible 服务的错误格式。
  let rawResponse: unknown = rawText;
  try {
    rawResponse = JSON.parse(rawText);
  } catch {
    rawResponse = rawText;
  }

  if (!response.ok) {
    throw new ImageGenerationRequestError(
      `${response.status} ${response.statusText}`,
      rawResponse
    );
  }

  return {
    rawResponse,
    image: extractImageFromUnknown(rawResponse),
    text: extractTextFromUnknown(rawResponse),
  };
}

export async function requestImageGenerationHistory(params: {
  pageNo: number;
  pageSize: number;
}): Promise<ImageGenerationHistoryPage> {
  // 历史查询与生成请求使用同一异常类型，页面只需统一处理 message 展示。
  try {
    return (await api.get<ImageGenerationHistoryPage>("/api/agent/image-generation/history", params)) as unknown as ImageGenerationHistoryPage;
  } catch (error) {
    throw new ImageGenerationRequestError(
      error instanceof Error ? error.message : "历史查询失败"
    );
  }
}
