import type { ChangeEvent } from "react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Modal } from "antd";

import {
  addWebUrlToKnowledgeBase,
  deleteKnowledgeBaseFiles,
  getKnowledgeBaseFileFullContent,
  ingestLocalFilesToKnowledgeBase,
  listKnowledgeBaseFiles,
  mapMragError,
} from "@/services/mragWorkspace";
import { showMessage } from "@/utils";
import {
  MRAG_FILE_POLL_INTERVAL_MS,
  MRAG_FILE_REFRESH_DELAY_MS,
} from "./utils";
import { shouldPollKnowledgeBaseFiles } from "./knowledgeBaseState";
import type {
  KnowledgeBaseFile,
  MRagFullContentStatus,
} from "./types";

type RefreshFilesOptions = {
  silent?: boolean;
};

export function useKnowledgeBaseFiles(
  toolBaseUrl: string,
  selectedKnowledgeBaseId: string
) {
  const [files, setFiles] = useState<KnowledgeBaseFile[]>([]);
  const [filesLoading, setFilesLoading] = useState(false);
  const [filesError, setFilesError] = useState("");
  const [uploadingFiles, setUploadingFiles] = useState(false);
  const [webUrl, setWebUrl] = useState("");
  const [addingWebUrl, setAddingWebUrl] = useState(false);
  const [activeFullContentFileId, setActiveFullContentFileId] = useState("");
  const [fullContentDrawerOpen, setFullContentDrawerOpen] = useState(false);
  const [fullContentLoading, setFullContentLoading] = useState(false);
  const [fullContentTitle, setFullContentTitle] = useState("");
  const [fullContentStatus, setFullContentStatus] =
    useState<MRagFullContentStatus>("IDLE");
  const [fullContentError, setFullContentError] = useState("");
  const [fullContentMarkdown, setFullContentMarkdown] = useState("");

  const delayedRefreshTimerRef = useRef<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const resetFullContentState = useCallback(() => {
    setActiveFullContentFileId("");
    setFullContentDrawerOpen(false);
    setFullContentLoading(false);
    setFullContentTitle("");
    setFullContentStatus("IDLE");
    setFullContentError("");
    setFullContentMarkdown("");
  }, []);

  useEffect(() => {
    return () => {
      if (delayedRefreshTimerRef.current) {
        window.clearTimeout(delayedRefreshTimerRef.current);
      }
    };
  }, []);

  const refreshFiles = useCallback(
    async (knowledgeBaseId: string, options?: RefreshFilesOptions) => {
      if (!options?.silent) {
        setFilesLoading(true);
      }

      try {
        const nextFiles = await listKnowledgeBaseFiles(toolBaseUrl, knowledgeBaseId);
        setFiles(nextFiles);
        setFilesError("");

        if (
          activeFullContentFileId &&
          !nextFiles.some((file) => file.id === activeFullContentFileId)
        ) {
          resetFullContentState();
        }
      } catch (error) {
        setFilesError(mapMragError(error));
        setFiles([]);
        resetFullContentState();
      } finally {
        setFilesLoading(false);
      }
    },
    [activeFullContentFileId, resetFullContentState, toolBaseUrl]
  );

  const scheduleDelayedFileRefresh = useCallback(
    (knowledgeBaseId: string) => {
      if (delayedRefreshTimerRef.current) {
        window.clearTimeout(delayedRefreshTimerRef.current);
      }

      // add_files / add_web_url 通过后台任务异步插入记录，这里补一次延迟刷新避免首刷空窗。
      delayedRefreshTimerRef.current = window.setTimeout(() => {
        void refreshFiles(knowledgeBaseId, { silent: true });
      }, MRAG_FILE_REFRESH_DELAY_MS);
    },
    [refreshFiles]
  );

  const handleFileInputChange = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      const inputFiles = Array.from(event.target.files || []);
      event.target.value = "";

      if (!selectedKnowledgeBaseId) {
        showMessage()?.error("请先选择知识库");
        return;
      }
      if (!inputFiles.length) {
        return;
      }

      setUploadingFiles(true);
      try {
        await ingestLocalFilesToKnowledgeBase(
          toolBaseUrl,
          selectedKnowledgeBaseId,
          inputFiles
        );
        showMessage()?.success(`已提交 ${inputFiles.length} 个文件的入库任务`);
        await refreshFiles(selectedKnowledgeBaseId, { silent: true });
        scheduleDelayedFileRefresh(selectedKnowledgeBaseId);
      } catch (error) {
        showMessage()?.error(mapMragError(error));
      } finally {
        setUploadingFiles(false);
      }
    },
    [refreshFiles, scheduleDelayedFileRefresh, selectedKnowledgeBaseId, toolBaseUrl]
  );

  const handleUploadFiles = useCallback(() => {
    if (!selectedKnowledgeBaseId) {
      showMessage()?.error("请先选择知识库");
      return;
    }
    fileInputRef.current?.click();
  }, [selectedKnowledgeBaseId]);

  const handleAddWebUrl = useCallback(async () => {
    const normalizedUrl = webUrl.trim();
    if (!selectedKnowledgeBaseId) {
      showMessage()?.error("请先选择知识库");
      return;
    }
    if (!normalizedUrl) {
      showMessage()?.error("请输入网页链接");
      return;
    }

    setAddingWebUrl(true);
    try {
      await addWebUrlToKnowledgeBase(toolBaseUrl, {
        kbId: selectedKnowledgeBaseId,
        url: normalizedUrl,
      });
      setWebUrl("");
      showMessage()?.success("网页链接已提交入库");
      await refreshFiles(selectedKnowledgeBaseId, { silent: true });
      scheduleDelayedFileRefresh(selectedKnowledgeBaseId);
    } catch (error) {
      showMessage()?.error(mapMragError(error));
    } finally {
      setAddingWebUrl(false);
    }
  }, [
    refreshFiles,
    scheduleDelayedFileRefresh,
    selectedKnowledgeBaseId,
    toolBaseUrl,
    webUrl,
  ]);

  const handleDeleteFile = useCallback(
    (fileId: string) => {
      if (!selectedKnowledgeBaseId) {
        return;
      }

      Modal.confirm({
        title: "确认删除这条资料吗？",
        content: "删除后会移除对应的文件记录和已写入的向量数据。",
        okText: "确认删除",
        cancelText: "取消",
        okButtonProps: { danger: true },
        async onOk() {
          await deleteKnowledgeBaseFiles(toolBaseUrl, {
            kbId: selectedKnowledgeBaseId,
            fileIds: [fileId],
          });
          showMessage()?.success("资料已删除");
          if (activeFullContentFileId === fileId) {
            resetFullContentState();
          }
          await refreshFiles(selectedKnowledgeBaseId, { silent: true });
        },
      });
    },
    [
      activeFullContentFileId,
      refreshFiles,
      resetFullContentState,
      selectedKnowledgeBaseId,
      toolBaseUrl,
    ]
  );

  const handleOpenFullContent = useCallback(
    async (fileId: string) => {
      if (!selectedKnowledgeBaseId) {
        showMessage()?.error("请先选择知识库");
        return;
      }

      const targetFile = files.find((file) => file.id === fileId);
      setActiveFullContentFileId(fileId);
      setFullContentDrawerOpen(true);
      setFullContentLoading(true);
      setFullContentTitle(targetFile?.title || "");
      setFullContentStatus("IDLE");
      setFullContentError("");
      setFullContentMarkdown("");

      try {
        const fullContent = await getKnowledgeBaseFileFullContent(toolBaseUrl, {
          kbId: selectedKnowledgeBaseId,
          fileId,
        });
        setFullContentTitle(fullContent.title || targetFile?.title || "");
        setFullContentStatus(fullContent.contentStatus);
        setFullContentError(fullContent.errorMessage);
        setFullContentMarkdown(fullContent.content);
      } catch (error) {
        setFullContentStatus("FAILED");
        setFullContentError(mapMragError(error));
        setFullContentMarkdown("");
      } finally {
        setFullContentLoading(false);
      }
    },
    [files, selectedKnowledgeBaseId, toolBaseUrl]
  );

  const handleCloseFullContent = useCallback(() => {
    setFullContentDrawerOpen(false);
  }, []);

  useEffect(() => {
    if (!selectedKnowledgeBaseId) {
      setFiles([]);
      setFilesError("");
      resetFullContentState();
      return;
    }

    void refreshFiles(selectedKnowledgeBaseId);
  }, [refreshFiles, resetFullContentState, selectedKnowledgeBaseId]);

  useEffect(() => {
    if (!selectedKnowledgeBaseId || !shouldPollKnowledgeBaseFiles(files)) {
      return;
    }

    const timer = window.setTimeout(() => {
      void refreshFiles(selectedKnowledgeBaseId, { silent: true });
    }, MRAG_FILE_POLL_INTERVAL_MS);

    return () => window.clearTimeout(timer);
  }, [files, refreshFiles, selectedKnowledgeBaseId]);

  return {
    fileInputRef,
    files,
    filesLoading,
    filesError,
    uploadingFiles,
    webUrl,
    addingWebUrl,
    activeFullContentFileId,
    fullContentDrawerOpen,
    fullContentLoading,
    fullContentTitle,
    fullContentStatus,
    fullContentError,
    fullContentMarkdown,
    setWebUrl,
    refreshFiles,
    handleFileInputChange,
    handleUploadFiles,
    handleAddWebUrl,
    handleDeleteFile,
    handleOpenFullContent,
    handleCloseFullContent,
    resetFullContentState,
  };
}
