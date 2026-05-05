export type UploadStatus = "pending" | "uploading" | "success" | "error";

export type UploadAttachmentState = {
  id: string;
  file: File;
  status: UploadStatus;
  error?: string;
  uploadedFile?: CHAT.TFile;
};

export function markUploadSuccess(
  queue: Record<string, UploadAttachmentState>,
  id: string,
  uploadedFile: CHAT.TFile
) {
  const current = queue[id];
  if (!current) {
    return queue;
  }

  return {
    ...queue,
    [id]: {
      ...current,
      status: "success" as UploadStatus,
      error: undefined,
      uploadedFile,
    },
  };
}

export function markUploadUploading(
  queue: Record<string, UploadAttachmentState>,
  id: string
) {
  const current = queue[id];
  if (!current) {
    return queue;
  }

  return {
    ...queue,
    [id]: {
      ...current,
      status: "uploading" as UploadStatus,
      error: undefined,
    },
  };
}

export function markUploadError(
  queue: Record<string, UploadAttachmentState>,
  id: string,
  error: string
) {
  const current = queue[id];
  if (!current) {
    return queue;
  }

  return {
    ...queue,
    [id]: {
      ...current,
      status: "error" as UploadStatus,
      error,
    },
  };
}
