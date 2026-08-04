import request from "@/utils/request";

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
};
