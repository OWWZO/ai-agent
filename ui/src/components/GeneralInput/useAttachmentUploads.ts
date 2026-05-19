import { useCallback, useEffect, useRef, useState } from "react";

import { agentFileApi, type UploadedConversationFile } from "@/services/agentFile";
import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";
import {
  markUploadError,
  markUploadSuccess,
  markUploadUploading,
  type UploadAttachmentState,
} from "./uploadQueue";

function resolveFileExtension(fileName?: string, mimeType?: string | null) {
  const ext = fileName?.split(".").pop()?.trim().toLowerCase();
  if (ext) {
    return ext;
  }
  if (mimeType?.includes("/")) {
    return mimeType.split("/").pop() || "";
  }
  return "";
}

export function normalizeUploadedFile(file: UploadedConversationFile): CHAT.TFile {
  const previewUrl = normalizeFileUrlForBrowser(
    file.previewUrl || file.url || file.downloadUrl || ""
  );
  const downloadUrl = normalizeFileUrlForBrowser(file.downloadUrl || file.url || "");
  return {
    name: file.name,
    url: previewUrl,
    type: file.type || resolveFileExtension(file.name, file.mimeType),
    size: Number(file.size) || 0,
    previewUrl,
    downloadUrl,
    resourceKey: file.resourceKey,
    mimeType: file.mimeType ?? null,
    originFileName: file.originFileName,
  };
}

export function useAttachmentUploads(sessionId: string) {
  const [attachmentUploads, setAttachmentUploads] = useState<
    Record<string, UploadAttachmentState>
  >({});
  const [attachmentOrder, setAttachmentOrder] = useState<string[]>([]);
  const attachmentUploadsRef = useRef<Record<string, UploadAttachmentState>>({});

  useEffect(() => {
    attachmentUploadsRef.current = attachmentUploads;
  }, [attachmentUploads]);

  const clearAttachmentUploads = useCallback(() => {
    setAttachmentUploads({});
    setAttachmentOrder([]);
  }, []);

  useEffect(() => {
    clearAttachmentUploads();
  }, [clearAttachmentUploads, sessionId]);

  const removeAttachmentUpload = useCallback((id: string) => {
    setAttachmentUploads((prev) => {
      if (!prev[id]) {
        return prev;
      }
      const next = { ...prev };
      delete next[id];
      return next;
    });
    setAttachmentOrder((prev) => prev.filter((itemId) => itemId !== id));
  }, []);

  const uploadAttachment = useCallback(
    async (attachmentId: string, file: File) => {
      setAttachmentUploads((prev) => markUploadUploading(prev, attachmentId));

      try {
        const uploadedFile = normalizeUploadedFile(
          await agentFileApi.uploadConversationFile(sessionId, file)
        );
        setAttachmentUploads((prev) =>
          markUploadSuccess(prev, attachmentId, uploadedFile)
        );
      } catch (error) {
        const errorMessage =
          error instanceof Error && error.message
            ? error.message
            : "上传失败，请稍后重试";
        setAttachmentUploads((prev) =>
          markUploadError(prev, attachmentId, errorMessage)
        );
      }
    },
    [sessionId]
  );

  const addAttachmentUploads = useCallback(
    (attachments: Array<{ id: string; file: File }>) => {
      if (!attachments.length) {
        return;
      }

      setAttachmentUploads((prev) => {
        const next = { ...prev };
        attachments.forEach((attachment) => {
          if (next[attachment.id]) {
            return;
          }
          next[attachment.id] = {
            id: attachment.id,
            file: attachment.file,
            status: "pending",
          };
        });
        return next;
      });

      setAttachmentOrder((prev) => {
        const next = [...prev];
        attachments.forEach((attachment) => {
          if (!next.includes(attachment.id)) {
            next.push(attachment.id);
          }
        });
        return next;
      });

      attachments.forEach((attachment) => {
        void uploadAttachment(attachment.id, attachment.file);
      });
    },
    [uploadAttachment]
  );

  const retryAttachmentUpload = useCallback(
    (id: string) => {
      const target = attachmentUploadsRef.current[id];
      if (!target) {
        return;
      }
      void uploadAttachment(id, target.file);
    },
    [uploadAttachment]
  );

  return {
    attachmentUploads,
    attachmentOrder,
    clearAttachmentUploads,
    removeAttachmentUpload,
    retryAttachmentUpload,
    addAttachmentUploads,
  };
}
