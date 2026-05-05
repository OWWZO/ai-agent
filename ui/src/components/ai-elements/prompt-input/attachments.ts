import { nanoid } from "nanoid";

import type {
  PromptInputAttachmentError,
  PromptInputAttachmentItem,
} from "./types";

export type PromptInputAttachmentValidationOptions = {
  accept?: string;
  maxFiles?: number;
  maxFileSize?: number;
  currentCount?: number;
};

export function matchesPromptInputAccept(file: File, accept?: string) {
  if (!accept || accept.trim() === "") {
    return true;
  }

  const patterns = accept
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  const fileType = file.type.toLowerCase();
  const fileName = file.name.toLowerCase();

  return patterns.some((pattern) => {
    const normalizedPattern = pattern.toLowerCase();
    if (normalizedPattern.endsWith("/*")) {
      const prefix = normalizedPattern.slice(0, -1);
      return fileType.startsWith(prefix);
    }
    // 兼容 `.pdf`、`.md` 这类扩展名声明，避免前端校验与原生 accept 行为不一致。
    if (normalizedPattern.startsWith(".")) {
      return fileName.endsWith(normalizedPattern);
    }
    return fileType === normalizedPattern;
  });
}

export function validatePromptInputFiles(
  fileList: File[] | FileList,
  options: PromptInputAttachmentValidationOptions
): {
  accepted: File[];
  error?: PromptInputAttachmentError;
} {
  const {
    accept,
    maxFiles,
    maxFileSize,
    currentCount = 0,
  } = options;
  const incoming = Array.from(fileList);
  const acceptedByType = incoming.filter((file) =>
    matchesPromptInputAccept(file, accept)
  );

  if (incoming.length && acceptedByType.length === 0) {
    return {
      accepted: [],
      error: {
        code: "accept",
        message: "No files match the accepted types.",
      },
    };
  }

  const acceptedBySize = acceptedByType.filter((file) =>
    maxFileSize ? file.size <= maxFileSize : true
  );

  if (acceptedByType.length > 0 && acceptedBySize.length === 0) {
    return {
      accepted: [],
      error: {
        code: "max_file_size",
        message: "All files exceed the maximum size.",
      },
    };
  }

  const capacity =
    typeof maxFiles === "number"
      ? Math.max(0, maxFiles - currentCount)
      : undefined;
  const accepted =
    typeof capacity === "number"
      ? acceptedBySize.slice(0, capacity)
      : acceptedBySize;

  if (
    typeof capacity === "number" &&
    acceptedBySize.length > capacity
  ) {
    return {
      accepted,
      error: {
        code: "max_files",
        message: "Too many files. Some were not added.",
      },
    };
  }

  return { accepted };
}

export function createPromptInputAttachmentItems(
  files: File[]
): PromptInputAttachmentItem[] {
  return files.map((file) => ({
    id: nanoid(),
    type: "file",
    url: URL.createObjectURL(file),
    mediaType: file.type,
    filename: file.name,
    file,
  }));
}

export function revokePromptInputAttachmentUrls(
  items: Array<Pick<PromptInputAttachmentItem, "url">>
) {
  items.forEach((item) => {
    if (item.url) {
      URL.revokeObjectURL(item.url);
    }
  });
}

export async function convertBlobUrlToDataUrl(
  url: string
): Promise<string | null> {
  try {
    const response = await fetch(url);
    const blob = await response.blob();
    return await new Promise((resolve) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(reader.result as string);
      reader.onerror = () => resolve(null);
      reader.readAsDataURL(blob);
    });
  } catch {
    return null;
  }
}
