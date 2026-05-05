import { useCallback, useEffect, useRef, useState } from "react";

import {
  createPromptInputAttachmentItems,
  revokePromptInputAttachmentUrls,
  validatePromptInputFiles,
} from "./attachments";
import type {
  PromptInputAttachmentError,
  PromptInputAttachmentItem,
} from "./types";

type UsePromptInputAttachmentStateOptions = {
  accept?: string;
  maxFiles?: number;
  maxFileSize?: number;
  onError?: (error: PromptInputAttachmentError) => void;
  onAttachmentsAdded?: (attachments: PromptInputAttachmentItem[]) => void;
};

/**
 * 局部附件状态统一收口，避免 provider / local 两套逻辑在主文件继续重复实现。
 */
export function usePromptInputAttachmentState(
  options: UsePromptInputAttachmentStateOptions
) {
  const {
    accept,
    maxFiles,
    maxFileSize,
    onError,
    onAttachmentsAdded,
  } = options;

  const [items, setItems] = useState<PromptInputAttachmentItem[]>([]);
  const itemsRef = useRef(items);
  itemsRef.current = items;

  useEffect(
    () => () => {
      revokePromptInputAttachmentUrls(itemsRef.current);
    },
    []
  );

  const add = useCallback((fileList: File[] | FileList) => {
    const result = validatePromptInputFiles(fileList, {
      accept,
      maxFiles,
      maxFileSize,
      currentCount: itemsRef.current.length,
    });

    if (result.error) {
      onError?.(result.error);
    }

    if (!result.accepted.length) {
      return;
    }

    const nextItems = createPromptInputAttachmentItems(result.accepted);
    onAttachmentsAdded?.(nextItems);
    setItems((previous) => previous.concat(nextItems));
  }, [accept, maxFileSize, maxFiles, onAttachmentsAdded, onError]);

  const remove = useCallback((id: string) => {
    setItems((previous) => {
      const found = previous.find((item) => item.id === id);
      if (found?.url) {
        URL.revokeObjectURL(found.url);
      }
      return previous.filter((item) => item.id !== id);
    });
  }, []);

  const clear = useCallback(() => {
    setItems((previous) => {
      revokePromptInputAttachmentUrls(previous);
      return [];
    });
  }, []);

  return {
    items,
    setItems,
    add,
    remove,
    clear,
  };
}
