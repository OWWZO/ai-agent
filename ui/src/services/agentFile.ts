import request from "@/utils/request";
import { getDeviceId } from "@/services/agentConversation";
import { resolveServiceBaseUrl } from "@/utils/origin";

// 对话附件上传契约；返回的稳定资源引用会进入聊天附件和后续工具调用。
export type UploadedConversationFile = {
  name: string;
  url: string;
  type: string;
  size: number;
  previewUrl?: string;
  downloadUrl?: string;
  resourceKey?: string;
  mimeType?: string | null;
  originFileName?: string;
};

export const agentFileApi = {
  uploadConversationFile: async (
    sessionId: string,
    file: File
  ): Promise<UploadedConversationFile> => {
    const formData = new FormData();
    formData.append("sessionId", sessionId);
    formData.append("file", file);

    return request.post("/api/agent/file/upload", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  },

  downloadWorkspaceArchive: async (sessionId: string): Promise<void> => {
    const base = resolveServiceBaseUrl(SERVICE_BASE_URL);
    const response = await fetch(
      `${base}/api/agent/workspace/${encodeURIComponent(sessionId)}/archive`,
      {
        credentials: "include",
        headers: { "X-Device-Id": getDeviceId() },
      }
    );
    if (!response.ok) {
      if (response.status === 403) {
        throw new Error("无权下载该工作区");
      }
      throw new Error("工作区打包失败");
    }
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = `workspace-${sessionId}.zip`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(objectUrl);
  },
};
