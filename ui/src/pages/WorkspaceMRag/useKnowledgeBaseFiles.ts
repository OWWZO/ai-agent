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

  // 入库接口是异步任务：立即刷新用于尽快显示已创建记录，延迟刷新和状态
  // 轮询则负责追上后台解析/向量化后的最终状态。两个 ref 只保存可取消的
  // 浏览器资源，不参与业务数据本身的渲染。
  const delayedRefreshTimerRef = useRef<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const resetFullContentState = useCallback(() => {
    // 切换知识库、删除当前文件或列表请求失败时，全文抽屉必须和文件列表
    // 一起失效，避免继续展示已经不属于当前知识库的旧内容。
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
      // 轮询使用 silent 模式，避免后台状态刷新让整个工作台反复显示 loading；
      // 只有用户主动进入知识库时才显示列表级加载状态。
      if (!options?.silent) {
        setFilesLoading(true);
      }

      try {
        const nextFiles = await listKnowledgeBaseFiles(toolBaseUrl, knowledgeBaseId);
        setFiles(nextFiles);
        setFilesError("");

        // 当前全文抽屉依赖列表中的文件 ID。删除或切换知识库后，先清理抽屉，
        // 再保留新列表，避免异步请求返回顺序造成悬空详情。
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
      // 先读取并立即清空 input value，允许用户再次选择同名文件；真正的
      // 上传状态由 finally 收口，保证异常时按钮不会永久处于忙碌状态。
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
        // 后端只在后台任务启动后才写入完整状态，因此首刷后再补一次延迟刷新。
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
    // URL 入库与本地文件入库共享同一条异步生命周期：校验 -> 提交 -> 首刷 ->
    // 延迟追踪，避免两个入口产生不同的列表一致性行为。
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
          // 删除成功后先关闭当前详情，再刷新列表；这样详情不会短暂显示已删除
          // 文件，刷新失败时也不会把旧抽屉误当作仍然有效。
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

      // 先打开抽屉并显示可识别的标题，再异步请求正文；请求失败仍保留抽屉，
      // 由 FAILED 状态承载错误，避免把网络异常误处理成“没有正文”。
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

    // 知识库切换是新的数据边界：先清理空选择，再加载当前 ID 的列表。
    void refreshFiles(selectedKnowledgeBaseId);
  }, [refreshFiles, resetFullContentState, selectedKnowledgeBaseId]);

  useEffect(() => {
    if (!selectedKnowledgeBaseId || !shouldPollKnowledgeBaseFiles(files)) {
      return;
    }

    // 只对仍处于处理中/待处理状态的列表递归安排下一次刷新；cleanup 会在
    // 列表或知识库切换时取消旧 timer，避免旧请求继续污染新页面。
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
