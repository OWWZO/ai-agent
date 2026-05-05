import { useCallback, useState } from "react";

import {
  ImageGenerationRequestError,
  requestDirectChat,
  requestImageGenerationTool,
} from "@/services/imageGeneration";
import {
  runImageBatchRequests,
  shouldUseImageBatchMode,
  type ImageBatchExecutionResult,
} from "./batch";
import type {
  AssistantMessage,
  EditorImageItem,
  GenerationConfig,
  GenerationMessage,
  ImageGenerationHistoryBatch,
  ResultImageItem,
  UserMessage,
} from "./types";
import {
  createLocalId,
  fileToDataUrl,
  resolveDownloadUrl,
  resolvePreviewUrl,
  trimTrailingSlash,
} from "./utils";

export type StatusTone = "default" | "success" | "error";

type UseImageGenerationSessionOptions = {
  config: GenerationConfig;
  collectEffectiveImages: () => EditorImageItem[];
  buildMaskCompositeDataUrls: (
    effectiveImages: EditorImageItem[],
    sourceImageDataUrls: string[]
  ) => Promise<string[]>;
  reloadHistory: () => Promise<void>;
};

function createAssistantMessage(assistantId: string): AssistantMessage {
  return {
    id: assistantId,
    role: "assistant",
    status: "loading",
    summary: "正在生成图像...",
    images: [],
    timestamp: Date.now(),
  };
}

function buildOutputName(text: string) {
  const normalized = text
    .trim()
    .slice(0, 16)
    .split("")
    .map((char) => {
      const code = char.charCodeAt(0);
      if (code < 32 || /[<>:"/\\|?*]/.test(char)) {
        return "_";
      }
      return char;
    })
    .join("");
  return normalized || "图片生成结果";
}

function toRequestError(error: unknown) {
  return error instanceof ImageGenerationRequestError
    ? error
    : new ImageGenerationRequestError(
      error instanceof Error ? error.message : "请求失败"
    );
}

function mapToolFilesToResultImages(
  fileInfo: ImageGenerationHistoryBatch["images"],
  prefix?: string
): ResultImageItem[] {
  const outputImages: ResultImageItem[] = [];
  (fileInfo || []).forEach((item, index) => {
    const previewUrl = resolvePreviewUrl(item);
    if (!previewUrl) {
      return;
    }
    outputImages.push({
      url: previewUrl,
      label: prefix
        ? `${prefix} · ${item.fileName || `结果图 ${index + 1}`}`
        : item.fileName || `结果图 ${index + 1}`,
      downloadUrl: resolveDownloadUrl(item),
    });
  });
  return outputImages;
}

function buildBatchAssistantPayload(results: ImageBatchExecutionResult[]) {
  const outputImages: ResultImageItem[] = [];
  const failureLines: string[] = [];
  let successCount = 0;
  let failureCount = 0;
  let usedFallbackAny = false;

  results.forEach((result) => {
    const batchLabel = `#${result.index + 1}`;
    if (result.success) {
      successCount += 1;
      usedFallbackAny = usedFallbackAny || Boolean(result.response.usedFallback);
      const currentImages = mapToolFilesToResultImages(
        result.response.fileInfo || [],
        batchLabel
      );
      outputImages.push(...currentImages);
      if (!currentImages.length) {
        failureLines.push(`${batchLabel} 未返回可预览图片`);
      }
      return;
    }

    failureCount += 1;
    const requestError = toRequestError(result.error);
    failureLines.push(`${batchLabel} ${requestError.message}`);
  });

  const summary =
    failureCount > 0
      ? `批处理完成，成功 ${successCount}/${results.length} 个请求，共生成 ${outputImages.length} 张图`
      : `批处理完成，共处理 ${results.length} 个请求，生成 ${outputImages.length} 张图`;

  return {
    outputImages,
    failureLines,
    failureCount,
    summary: usedFallbackAny ? `${summary}（部分请求已自动切换兼容接口）` : summary,
    debugPayload: {
      mode: "batch",
      total: results.length,
      successCount,
      failureCount,
      results: results.map((result) =>
        result.success
          ? {
            index: result.index + 1,
            success: true,
            usedFallback: Boolean(result.response.usedFallback),
            data: result.response.data,
            rawResponse: result.response.rawResponse ?? result.response,
          }
          : {
            index: result.index + 1,
            success: false,
            error: toRequestError(result.error).message,
            rawResponse:
              toRequestError(result.error).rawResponse ??
              toRequestError(result.error).message,
          }
      ),
    },
  };
}

/**
 * 会话消息、发送链路和调试响应统一收口，页面只保留工作台编排和展示。
 */
export function useImageGenerationSession(
  options: UseImageGenerationSessionOptions
) {
  const {
    config,
    collectEffectiveImages,
    buildMaskCompositeDataUrls,
    reloadHistory,
  } = options;

  const [statusText, setStatusText] = useState("");
  const [statusTone, setStatusTone] = useState<StatusTone>("default");
  const [prompt, setPrompt] = useState("");
  const [messages, setMessages] = useState<GenerationMessage[]>([]);
  const [debugPayload, setDebugPayload] = useState<unknown>("（尚未请求）");

  const setStatus = useCallback(
    (text: string, tone: StatusTone = "default") => {
      setStatusText(text);
      setStatusTone(tone);
    },
    []
  );

  const updateAssistantMessage = useCallback(
    (
      assistantId: string,
      updater: (previous: AssistantMessage) => AssistantMessage
    ) => {
      setMessages((previous) =>
        previous.map((item) => {
          if (item.role !== "assistant" || item.id !== assistantId) {
            return item;
          }
          return updater(item);
        })
      );
    },
    []
  );

  const clearMessages = useCallback(() => {
    setMessages([]);
    setStatus("", "default");
    setDebugPayload("（尚未请求）");
  }, [setStatus]);

  const handleSend = useCallback(async () => {
    const currentPrompt = prompt.trim();
    if (!currentPrompt) {
      setStatus("请先输入 Prompt", "error");
      return;
    }

    const effectiveImages = collectEffectiveImages();
    if (config.mode === "edits" && !effectiveImages.length) {
      setStatus("请先上传至少一张参考图片", "error");
      return;
    }

    const userMessage: UserMessage = {
      id: createLocalId("user"),
      role: "user",
      prompt: currentPrompt,
      mode: config.mode,
      images:
        config.mode === "edits"
          ? effectiveImages.map((item) => item.objectUrl)
          : [],
      timestamp: Date.now(),
    };
    const assistantId = createLocalId("assistant");

    setMessages((previous) => [
      ...previous,
      userMessage,
      createAssistantMessage(assistantId),
    ]);
    setPrompt("");
    setStatus("请求发送中...", "default");
    setDebugPayload("请求发送中...");

    try {
      if (config.mode === "chat") {
        const currentBaseUrl = trimTrailingSlash(config.baseUrl);
        if (
          !currentBaseUrl ||
          !config.apiKey.trim() ||
          !config.model.trim()
        ) {
          setStatus("请填写完整的对话调试配置与 Prompt", "error");
          return;
        }

        const chatResult = await requestDirectChat({
          baseUrl: currentBaseUrl,
          apiKey: config.apiKey.trim(),
          model: config.model.trim(),
          prompt: currentPrompt,
        });

        setDebugPayload(chatResult.rawResponse);
        const outputImages: ResultImageItem[] = [];
        if (chatResult.image?.dataUrl) {
          outputImages.push({
            url: chatResult.image.dataUrl,
            label: "对话返回图片",
          });
        } else if (chatResult.image?.url) {
          outputImages.push({
            url: chatResult.image.url,
            label: "对话返回图片",
            downloadUrl: chatResult.image.url,
          });
        }

        updateAssistantMessage(assistantId, () => ({
          id: assistantId,
          role: "assistant",
          status: outputImages.length || chatResult.text ? "done" : "error",
          summary: outputImages.length
            ? "对话接口返回了图片结果"
            : chatResult.text || "响应中未识别到图片内容",
          text: chatResult.text || undefined,
          images: outputImages,
          rawResponse: chatResult.rawResponse,
          timestamp: Date.now(),
        }));
        setStatus(
          outputImages.length ? "生成完成" : "未识别到图片内容",
          outputImages.length ? "success" : "error"
        );
        return;
      }

      const sourceImageDataUrls = await Promise.all(
        effectiveImages.map((item) => fileToDataUrl(item.file))
      );
      const maskFileNames = await buildMaskCompositeDataUrls(
        effectiveImages,
        sourceImageDataUrls
      );

      if (
        shouldUseImageBatchMode({
          mode: config.mode,
          imageCount: effectiveImages.length,
          batchMode: config.batchMode,
        })
      ) {
        const batchResults = await runImageBatchRequests({
          prompt: currentPrompt,
          size: config.size.trim(),
          n: config.n,
          plans: effectiveImages.map((item, index) => ({
            key: String(index + 1),
            fileNames: [sourceImageDataUrls[index]],
            maskFileNames: [maskFileNames[index] || ""],
            fileName: buildOutputName(
              item.file.name || `图片生成结果_${index + 1}`
            ),
          })),
          createRequestId: (index) => createLocalId(`image-batch-${index + 1}`),
          request: requestImageGenerationTool,
        });

        const batchPayload = buildBatchAssistantPayload(batchResults);
        setDebugPayload(batchPayload.debugPayload);
        updateAssistantMessage(assistantId, () => ({
          id: assistantId,
          role: "assistant",
          status: batchPayload.outputImages.length ? "done" : "error",
          summary: batchPayload.summary,
          text: batchPayload.failureLines.length
            ? batchPayload.failureLines.join("\n")
            : undefined,
          images: batchPayload.outputImages,
          rawResponse: batchPayload.debugPayload,
          timestamp: Date.now(),
        }));
        setStatus(
          batchPayload.summary,
          batchPayload.failureCount === 0
            ? "success"
            : batchPayload.outputImages.length
              ? "default"
              : "error"
        );
        await reloadHistory();
        return;
      }

      const toolResponse = await requestImageGenerationTool({
        requestId: createLocalId("image"),
        prompt: currentPrompt,
        mode: config.mode,
        size: config.size.trim(),
        n: config.n,
        fileNames: config.mode === "edits" ? sourceImageDataUrls : [],
        maskFileNames: config.mode === "edits" ? maskFileNames : [],
        fileName: buildOutputName(currentPrompt),
        fileDescription: currentPrompt.slice(0, 80),
      });

      setDebugPayload(toolResponse.rawResponse ?? toolResponse);
      const outputImages = mapToolFilesToResultImages(toolResponse.fileInfo || []);

      updateAssistantMessage(assistantId, () => ({
        id: assistantId,
        role: "assistant",
        status: outputImages.length ? "done" : "error",
        summary: toolResponse.data,
        images: outputImages,
        rawResponse: toolResponse.rawResponse ?? toolResponse,
        timestamp: Date.now(),
      }));
      setStatus(
        toolResponse.usedFallback
          ? "生成完成（已自动切换兼容接口）"
          : "生成完成",
        "success"
      );
      await reloadHistory();
    } catch (error) {
      const requestError = toRequestError(error);
      setDebugPayload(requestError.rawResponse ?? requestError.message);
      updateAssistantMessage(assistantId, () => ({
        id: assistantId,
        role: "assistant",
        status: "error",
        summary: "请求失败",
        images: [],
        error: requestError.message,
        rawResponse: requestError.rawResponse,
        timestamp: Date.now(),
      }));
      setStatus("请求失败", "error");
    }
  }, [
    buildMaskCompositeDataUrls,
    collectEffectiveImages,
    config,
    prompt,
    reloadHistory,
    setStatus,
    updateAssistantMessage,
  ]);

  return {
    prompt,
    setPrompt,
    messages,
    clearMessages,
    handleSend,
    statusText,
    statusTone,
    debugPayload,
  };
}
