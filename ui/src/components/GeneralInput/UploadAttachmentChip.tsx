import React from "react";
import {
  AlertCircleIcon,
  CheckIcon,
  FileIcon,
  LoaderCircleIcon,
  RefreshCwIcon,
  XIcon,
} from "lucide-react";

import { usePromptInputAttachments } from "@/components/ai-elements/prompt-input";
import { cn } from "@/lib/utils";
import type { PromptInputAttachmentItem } from "@/components/ai-elements/prompt-input";
import type { UploadAttachmentState } from "./uploadQueue";

function formatAttachmentSize(size?: number) {
  if (typeof size !== "number" || Number.isNaN(size) || size < 0) {
    return "未知大小";
  }

  const units = ["B", "KB", "MB", "GB"];
  let unitIndex = 0;
  let currentSize = size;
  while (currentSize >= 1024 && unitIndex < units.length - 1) {
    currentSize /= 1024;
    unitIndex += 1;
  }
  return `${currentSize.toFixed(2)} ${units[unitIndex]}`;
}

export function resolveUploadStatusLabel(uploadState?: UploadAttachmentState) {
  if (!uploadState) {
    return "";
  }
  switch (uploadState.status) {
    case "pending":
    case "uploading":
      return "上传中";
    case "success":
      return formatAttachmentSize(
        uploadState.uploadedFile?.size ?? uploadState.file.size
      );
    case "error":
      return uploadState.error || "上传失败";
    default:
      return "";
  }
}

export default function UploadAttachmentChip(props: {
  attachment: PromptInputAttachmentItem;
  uploadState?: UploadAttachmentState;
  onRemoveAttachment: (id: string) => void;
  onRetryAttachment: (id: string) => void;
}) {
  const attachments = usePromptInputAttachments();
  const isImage = props.attachment.mediaType?.startsWith("image/") && props.attachment.url;
  const isUploading =
    props.uploadState?.status === "pending" ||
    props.uploadState?.status === "uploading";
  const isSuccess = props.uploadState?.status === "success";
  const isError = props.uploadState?.status === "error";

  const removeAttachment = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    attachments.remove(props.attachment.id);
    props.onRemoveAttachment(props.attachment.id);
  };

  const retryAttachment = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    props.onRetryAttachment(props.attachment.id);
  };

  return (
    <div className="group flex min-w-0 max-w-full items-center gap-2 rounded-2xl bg-[var(--chat-surface-muted)]/78 px-2.5 py-2 text-[13px] shadow-[var(--shadow-xs)]">
      <div className="flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-white/90">
        {isImage ? (
          <img
            alt={props.attachment.filename || "attachment"}
            className="size-full object-cover"
            src={props.attachment.url}
          />
        ) : (
          <FileIcon className="size-4 text-[var(--chat-text-soft)]" />
        )}
      </div>
      <div className="min-w-0 flex-1">
        <div className="truncate text-[13px] font-medium text-[var(--chat-text)]">
          {props.attachment.filename || "未命名文件"}
        </div>
        <div
          className={cn(
            "flex items-center gap-1 text-[11px] leading-4",
            isError ? "text-[#d14343]" : "text-[var(--chat-text-soft)]"
          )}
        >
          {isUploading ? (
            <LoaderCircleIcon className="size-3 animate-spin" />
          ) : null}
          {isSuccess ? <CheckIcon className="size-3 text-[#0a74da]" /> : null}
          {isError ? <AlertCircleIcon className="size-3" /> : null}
          <span className="truncate">
            {resolveUploadStatusLabel(props.uploadState)}
          </span>
        </div>
      </div>
      {isError ? (
        <button
          type="button"
          className="flex size-7 shrink-0 items-center justify-center rounded-full text-[var(--chat-text-soft)] transition-colors hover:bg-white hover:text-[var(--chat-text)]"
          onClick={retryAttachment}
        >
          <RefreshCwIcon className="size-3.5" />
        </button>
      ) : null}
      <button
        type="button"
        className="flex size-7 shrink-0 items-center justify-center rounded-full text-[var(--chat-text-soft)] transition-colors hover:bg-white hover:text-[var(--chat-text)]"
        onClick={removeAttachment}
      >
        <XIcon className="size-3.5" />
      </button>
    </div>
  );
}
