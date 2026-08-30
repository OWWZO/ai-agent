import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";

/**
 * Agent SSE 请求体适配器。
 *
 * <p>该模块把页面输入和附件引用转换为 Reactor 后端契约，集中处理 deepThink 数值
 * 标识和浏览器可访问 URL，避免组件层重复维护协议字段。</p>
 */
export type AgentSessionFile = {
  fileName: string
  ossUrl?: string
  domainUrl?: string
  fileSize?: number
  fileType?: string
  resourceKey?: string
  mimeType?: string | null
  originFileName?: string
}

type BuildAgentStreamRequestInput = {
  sessionId: string
  requestId: string
  message: string
  deepThink: boolean
  outputStyle?: "dataAgent"
  files?: CHAT.TFile[]
  /** modelId 或上游 modelName */
  model?: string
  thinking?: boolean
  thinkingEffort?: string
}

const resolvePreviewUrl = (file: CHAT.TFile) =>
  normalizeFileUrlForBrowser(file.previewUrl || file.url || file.downloadUrl || "")

const resolveDownloadUrl = (file: CHAT.TFile) =>
  normalizeFileUrlForBrowser(file.downloadUrl || file.previewUrl || file.url || "")

/**
 * 把当前轮上传附件转换为后端可直接消费的 sessionFiles 结构。
 */
export const mapSessionFiles = (files?: CHAT.TFile[]): AgentSessionFile[] => {
  if (!files?.length) {
    return []
  }

  return files
    .filter((file): file is CHAT.TFile => Boolean(file?.name))
    .map((file) => ({
      fileName: file.name,
      ossUrl: resolveDownloadUrl(file),
      domainUrl: resolvePreviewUrl(file),
      fileSize: file.size,
      fileType: file.type,
      resourceKey: file.resourceKey,
      mimeType: file.mimeType,
      originFileName: file.originFileName,
    }))
}

/**
 * 统一组装 Reactor SSE 请求，避免协议细节散落在组件里。
 */
export const buildAgentStreamRequest = ({
  sessionId,
  requestId,
  message,
  deepThink,
  outputStyle,
  files,
  model,
  thinking,
  thinkingEffort,
}: BuildAgentStreamRequestInput) => {
  const sessionFiles = mapSessionFiles(files)
  const modelRef = model?.trim()
  const effort = thinkingEffort?.trim()

  return {
    sessionId,
    requestId,
    query: message,
    deepThink: deepThink ? 1 : 0,
    ...(outputStyle === "dataAgent" ? { outputStyle } : {}),
    ...(sessionFiles.length ? { sessionFiles } : {}),
    ...(modelRef ? { model: modelRef } : {}),
    ...(thinking !== undefined ? { thinking } : {}),
    ...(thinking && effort ? { thinkingEffort: effort } : {}),
  }
}
