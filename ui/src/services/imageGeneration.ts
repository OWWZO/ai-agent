import type { ImageGenerationToolResponse } from "@/pages/WorkspaceImageGeneration/types";
import { extractImageFromUnknown, extractTextFromUnknown, trimTrailingSlash } from "@/pages/WorkspaceImageGeneration/utils";

type DirectChatRequest = {
  baseUrl: string;
  apiKey: string;
  model: string;
  prompt: string;
};

type ToolRequest = {
  toolBaseUrl: string;
  requestId: string;
  prompt: string;
  mode: "images" | "edits";
  baseUrl: string;
  apiKey: string;
  model: string;
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
  const response = await fetch(`${trimTrailingSlash(payload.toolBaseUrl)}/v1/tool/image_generation`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({
      requestId: payload.requestId,
      prompt: payload.prompt,
      mode: payload.mode,
      fileNames: payload.fileNames,
      maskFileNames: payload.maskFileNames,
      fileName: payload.fileName,
      fileDescription: payload.fileDescription,
      baseUrl: payload.baseUrl,
      apiKey: payload.apiKey,
      model: payload.model,
      size: payload.size,
      n: payload.n,
      timeoutSeconds: 300,
      stream: false,
    }),
  });

  const rawText = await response.text();
  let rawResponse: unknown = rawText;
  try {
    rawResponse = JSON.parse(rawText);
  } catch {
    rawResponse = rawText;
  }

  if (!response.ok) {
    const message =
      (rawResponse as Record<string, unknown>)?.message ||
      (rawResponse as Record<string, unknown>)?.detail ||
      `${response.status} ${response.statusText}`;
    throw new ImageGenerationRequestError(String(message), rawResponse);
  }

  return rawResponse as ImageGenerationToolResponse;
}

export async function requestDirectChat(payload: DirectChatRequest) {
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
