import type { ImageGenerationToolResponse, RequestMode } from "./types";

export type ImageBatchPlanInput = {
  fileName: string;
  source: string;
  mask: string;
};

export type ImageBatchPlan = {
  key: string;
  fileNames: string[];
  maskFileNames: string[];
  fileName: string;
};

export type ImageBatchRequestPayload = {
  requestId: string;
  prompt: string;
  mode: "edits";
  model?: string;
  size: string;
  n: number;
  fileNames: string[];
  maskFileNames: string[];
  fileName: string;
  fileDescription?: string;
};

export type ImageBatchExecutionResult =
  | {
      success: true;
      index: number;
      plan: ImageBatchPlan;
      response: ImageGenerationToolResponse;
    }
  | {
      success: false;
      index: number;
      plan: ImageBatchPlan;
      error: unknown;
    };

export function shouldUseImageBatchMode(input: {
  mode: RequestMode;
  imageCount: number;
  batchMode: boolean;
}) {
  // 只有编辑模式且存在多张源图时才拆批，单图请求保持原有单次协议。
  return input.mode === "edits" && input.batchMode && input.imageCount > 1;
}

export function buildImageBatchPlans(input: {
  prompt: string;
  size: string;
  images: ImageBatchPlanInput[];
}): ImageBatchPlan[] {
  // 每张源图生成独立 plan，保留 mask 对齐关系并去掉输出扩展名。
  return input.images.map((image, index) => ({
    key: String(index + 1),
    fileNames: [image.source],
    maskFileNames: [image.mask || ""],
    fileName: normalizeBatchOutputName(image.fileName, index),
  }));
}

export async function runImageBatchRequests(input: {
  prompt: string;
  model?: string;
  size: string;
  n: number;
  plans: ImageBatchPlan[];
  createRequestId: (index: number) => string;
  request: (payload: ImageBatchRequestPayload) => Promise<ImageGenerationToolResponse>;
}): Promise<ImageBatchExecutionResult[]> {
  // Promise.all 并行执行，但每个 plan 单独捕获错误，使部分成功结果仍可回显。
  return await Promise.all(
    input.plans.map(async (plan, index) => {
      try {
        const response = await input.request({
          requestId: input.createRequestId(index),
          prompt: input.prompt,
          mode: "edits",
          model: input.model,
          size: input.size,
          n: input.n,
          fileNames: plan.fileNames,
          maskFileNames: plan.maskFileNames,
          fileName: plan.fileName,
          fileDescription: `${input.prompt.slice(0, 80)}（批次 ${index + 1}）`,
        });
        return {
          success: true,
          index,
          plan,
          response,
        };
      } catch (error) {
        return {
          success: false,
          index,
          plan,
          error,
        };
      }
    })
  );
}

function normalizeBatchOutputName(fileName: string, index: number) {
  // 后端接收不带扩展名的逻辑文件名；空名使用稳定的批次序号兜底。
  const trimmed = (fileName || "").trim();
  if (!trimmed) {
    return `图片生成结果_${index + 1}`;
  }
  const dotIndex = trimmed.lastIndexOf(".");
  return dotIndex > 0 ? trimmed.slice(0, dotIndex) : trimmed;
}
