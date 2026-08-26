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
  // 采用 1024 进位并固定两位小数，让上传中/成功状态的文本长度稳定。
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
  // pending/uploading 共用进行中文案，success 展示最终文件大小，error 优先展示服务端原因。
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
    // 同步移除 prompt input 和上传队列，避免 UI 消失但异步上传结果随后重新写回。
    event.stopPropagation();
    attachments.remove(props.attachment.id);
    props.onRemoveAttachment(props.attachment.id);
  };

  const retryAttachment = (event: React.MouseEvent<HTMLButtonElement>) => {
    // 重试只触发上传 hook，附件本身仍保留在输入框中。
    event.stopPropagation();
    props.onRetryAttachment(props.attachment.id);
  };

  return (
    <div className="reactor-composer-attachment-chip group flex min-w-0 max-w-full items-center gap-1.5 rounded-full border border-[var(--color-line)] bg-[var(--color-surface-raised)] px-1.5 py-1 text-[12px] shadow-none transition-colors hover:border-[var(--color-line-strong)]">
      <div className="flex size-5 shrink-0 items-center justify-center overflow-hidden rounded-full bg-[var(--color-surface-sunken)]">
        {isImage ? (
          <img
            alt={props.attachment.filename || "attachment"}
            className="size-full object-cover"
            src={props.attachment.url}
          />
        ) : (
          <FileIcon className="size-3.5 text-[var(--color-text-muted)]" />
        )}
      </div>
      <div className="min-w-0 flex-1">
        <div className="truncate text-[12px] font-medium text-[var(--color-text)]">
          {props.attachment.filename || "未命名文件"}
        </div>
        <div
          className={cn(
            "flex items-center gap-1 text-[10px] leading-3.5",
            isError ? "text-[var(--color-danger)]" : "text-[var(--color-text-muted)]"
          )}
        >
          {isUploading ? (
            <LoaderCircleIcon className="size-2.5 animate-spin" />
          ) : null}
          {isSuccess ? <CheckIcon className="size-3 text-[var(--color-accent)]" /> : null}
          {isError ? <AlertCircleIcon className="size-3" /> : null}
          <span className="truncate">
            {resolveUploadStatusLabel(props.uploadState)}
          </span>
        </div>
      </div>
      {isError ? (
        <button
          type="button"
          className="flex size-5 shrink-0 items-center justify-center rounded-full text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-hover)] hover:text-[var(--color-text)]"
          onClick={retryAttachment}
        >
          <RefreshCwIcon className="size-3" />
        </button>
      ) : null}
      <button
        type="button"
        className="flex size-5 shrink-0 items-center justify-center rounded-full text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-hover)] hover:text-[var(--color-text)]"
        onClick={removeAttachment}
      >
        <XIcon className="size-3" />
      </button>
    </div>
  );
}
