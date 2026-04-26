import { Modal } from "antd";
import type { ChangeEvent } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import type { KnowledgeBase, KnowledgeBaseFile } from "./types";
import WorkspaceMRagView from "./view";
import {
  MRAG_FILE_POLL_INTERVAL_MS,
  MRAG_FILE_REFRESH_DELAY_MS,
  loadMRagWorkspaceStoredState,
  persistMRagWorkspaceStoredState,
} from "./utils";
import {
  addWebUrlToKnowledgeBase,
  createKnowledgeBase,
  deleteKnowledgeBaseFiles,
  hasProcessingFiles,
  ingestLocalFilesToKnowledgeBase,
  listKnowledgeBaseFiles,
  listKnowledgeBases,
  mapMragError,
  streamMragQuery,
} from "@/services/mragWorkspace";
import { showMessage } from "@/utils";
import { trimTrailingSlash } from "@/pages/WorkspaceImageGeneration/utils";

type RefreshKnowledgeBaseOptions = {
  silent?: boolean;
  preferredKnowledgeBaseId?: string;
};

type RefreshFilesOptions = {
  silent?: boolean;
};

function resolveSelectedKnowledgeBaseId(
  knowledgeBases: KnowledgeBase[],
  currentKnowledgeBaseId: string,
  preferredKnowledgeBaseId?: string
): string {
  const preferred = preferredKnowledgeBaseId?.trim();
  if (preferred && knowledgeBases.some((item) => item.id === preferred)) {
    return preferred;
  }
  if (
    currentKnowledgeBaseId &&
    knowledgeBases.some((item) => item.id === currentKnowledgeBaseId)
  ) {
    return currentKnowledgeBaseId;
  }
  return knowledgeBases[0]?.id || "";
}

const WorkspaceMRag: ReactorType.FC = () => {
  const initialWorkspaceState = useMemo(() => loadMRagWorkspaceStoredState(), []);
  const [workspaceState, setWorkspaceState] = useState(initialWorkspaceState);
  const [toolBaseUrlDraft, setToolBaseUrlDraft] = useState(
    initialWorkspaceState.toolBaseUrl
  );
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [knowledgeBasesLoading, setKnowledgeBasesLoading] = useState(false);
  const [knowledgeBasesError, setKnowledgeBasesError] = useState("");
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState(
    initialWorkspaceState.selectedKnowledgeBaseId
  );
  const [createKnowledgeBaseName, setCreateKnowledgeBaseName] = useState("");
  const [createKnowledgeBaseDesc, setCreateKnowledgeBaseDesc] = useState("");
  const [creatingKnowledgeBase, setCreatingKnowledgeBase] = useState(false);
  const [files, setFiles] = useState<KnowledgeBaseFile[]>([]);
  const [filesLoading, setFilesLoading] = useState(false);
  const [filesError, setFilesError] = useState("");
  const [uploadingFiles, setUploadingFiles] = useState(false);
  const [webUrl, setWebUrl] = useState("");
  const [addingWebUrl, setAddingWebUrl] = useState(false);
  const [question, setQuestion] = useState("");
  const [querying, setQuerying] = useState(false);
  const [queryAnswer, setQueryAnswer] = useState("");
  const [queryError, setQueryError] = useState("");
  const [queryRawChunks, setQueryRawChunks] = useState<unknown[]>([]);

  const knowledgeBaseRequestIdRef = useRef(0);
  const filesRequestIdRef = useRef(0);
  const delayedRefreshTimerRef = useRef<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const queryAbortRef = useRef<AbortController | null>(null);

  const selectedKnowledgeBase = useMemo(
    () => knowledgeBases.find((item) => item.id === selectedKnowledgeBaseId) || null,
    [knowledgeBases, selectedKnowledgeBaseId]
  );

  useEffect(() => {
    persistMRagWorkspaceStoredState({
      toolBaseUrl: workspaceState.toolBaseUrl,
      selectedKnowledgeBaseId,
    });
  }, [selectedKnowledgeBaseId, workspaceState.toolBaseUrl]);

  useEffect(() => {
    return () => {
      if (delayedRefreshTimerRef.current) {
        window.clearTimeout(delayedRefreshTimerRef.current);
      }
      queryAbortRef.current?.abort();
    };
  }, []);

  const refreshKnowledgeBases = useCallback(
    async (options?: RefreshKnowledgeBaseOptions) => {
      const requestId = knowledgeBaseRequestIdRef.current + 1;
      knowledgeBaseRequestIdRef.current = requestId;

      if (!options?.silent) {
        setKnowledgeBasesLoading(true);
      }

      try {
        const nextKnowledgeBases = await listKnowledgeBases(workspaceState.toolBaseUrl);
        if (requestId !== knowledgeBaseRequestIdRef.current) {
          return;
        }

        setKnowledgeBases(nextKnowledgeBases);
        setKnowledgeBasesError("");

        const nextSelectedKnowledgeBaseId = resolveSelectedKnowledgeBaseId(
          nextKnowledgeBases,
          selectedKnowledgeBaseId,
          options?.preferredKnowledgeBaseId
        );
        setSelectedKnowledgeBaseId(nextSelectedKnowledgeBaseId);
      } catch (error) {
        if (requestId !== knowledgeBaseRequestIdRef.current) {
          return;
        }
        setKnowledgeBasesError(mapMragError(error));
        setKnowledgeBases([]);
        setSelectedKnowledgeBaseId("");
      } finally {
        if (requestId === knowledgeBaseRequestIdRef.current) {
          setKnowledgeBasesLoading(false);
        }
      }
    },
    [selectedKnowledgeBaseId, workspaceState.toolBaseUrl]
  );

  const refreshFiles = useCallback(
    async (knowledgeBaseId: string, options?: RefreshFilesOptions) => {
      const requestId = filesRequestIdRef.current + 1;
      filesRequestIdRef.current = requestId;

      if (!options?.silent) {
        setFilesLoading(true);
      }

      try {
        const nextFiles = await listKnowledgeBaseFiles(
          workspaceState.toolBaseUrl,
          knowledgeBaseId
        );
        if (requestId !== filesRequestIdRef.current) {
          return;
        }
        setFiles(nextFiles);
        setFilesError("");
      } catch (error) {
        if (requestId !== filesRequestIdRef.current) {
          return;
        }
        setFilesError(mapMragError(error));
        setFiles([]);
      } finally {
        if (requestId === filesRequestIdRef.current) {
          setFilesLoading(false);
        }
      }
    },
    [workspaceState.toolBaseUrl]
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

  const applyToolBaseUrl = useCallback(() => {
    const normalized = trimTrailingSlash(toolBaseUrlDraft);
    if (!normalized) {
      showMessage()?.error("请先填写可访问的 Tool Base URL");
      return;
    }

    setToolBaseUrlDraft(normalized);
    setWorkspaceState((previous) => ({
      ...previous,
      toolBaseUrl: normalized,
    }));

    if (normalized === workspaceState.toolBaseUrl) {
      void refreshKnowledgeBases();
      return;
    }

    setFiles([]);
    setFilesError("");
  }, [refreshKnowledgeBases, toolBaseUrlDraft, workspaceState.toolBaseUrl]);

  const handleCreateKnowledgeBase = useCallback(async () => {
    const kbName = createKnowledgeBaseName.trim();
    if (!kbName) {
      showMessage()?.error("请输入知识库名称");
      return;
    }

    setCreatingKnowledgeBase(true);
    try {
      const createdKnowledgeBase = await createKnowledgeBase(workspaceState.toolBaseUrl, {
        kbName,
        kbDesc: createKnowledgeBaseDesc.trim(),
      });
      setCreateKnowledgeBaseName("");
      setCreateKnowledgeBaseDesc("");
      showMessage()?.success("知识库已创建");
      await refreshKnowledgeBases({preferredKnowledgeBaseId: createdKnowledgeBase.id,});
    } catch (error) {
      showMessage()?.error(mapMragError(error));
    } finally {
      setCreatingKnowledgeBase(false);
    }
  }, [
    createKnowledgeBaseDesc,
    createKnowledgeBaseName,
    refreshKnowledgeBases,
    workspaceState.toolBaseUrl,
  ]);

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
          workspaceState.toolBaseUrl,
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
    [
      refreshFiles,
      scheduleDelayedFileRefresh,
      selectedKnowledgeBaseId,
      workspaceState.toolBaseUrl,
    ]
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
      await addWebUrlToKnowledgeBase(workspaceState.toolBaseUrl, {
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
    webUrl,
    workspaceState.toolBaseUrl,
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
        okButtonProps: {danger: true,},
        async onOk() {
          await deleteKnowledgeBaseFiles(workspaceState.toolBaseUrl, {
            kbId: selectedKnowledgeBaseId,
            fileIds: [fileId],
          });
          showMessage()?.success("资料已删除");
          await refreshFiles(selectedKnowledgeBaseId, { silent: true });
        },
      });
    },
    [refreshFiles, selectedKnowledgeBaseId, workspaceState.toolBaseUrl]
  );

  const handleSubmitQuery = useCallback(async () => {
    const currentQuestion = question.trim();
    if (!selectedKnowledgeBaseId) {
      showMessage()?.error("请先选择知识库");
      return;
    }
    if (!currentQuestion) {
      showMessage()?.error("请输入问题");
      return;
    }

    queryAbortRef.current?.abort();
    const abortController = new AbortController();
    queryAbortRef.current = abortController;

    setQuerying(true);
    setQueryError("");
    setQueryAnswer("");
    setQueryRawChunks([]);

    try {
      await streamMragQuery({
        toolBaseUrl: workspaceState.toolBaseUrl,
        kbId: selectedKnowledgeBaseId,
        question: currentQuestion,
        signal: abortController.signal,
        onChunk(chunk) {
          if (chunk.content) {
            setQueryAnswer((previous) => previous + chunk.content);
          }
          setQueryRawChunks((previous) => [...previous, chunk.raw].slice(-50));
        },
      });
    } catch (error) {
      if (!abortController.signal.aborted) {
        setQueryError(mapMragError(error));
      }
    } finally {
      if (queryAbortRef.current === abortController) {
        queryAbortRef.current = null;
      }
      setQuerying(false);
    }
  }, [question, selectedKnowledgeBaseId, workspaceState.toolBaseUrl]);

  const handleStopQuery = useCallback(() => {
    if (!queryAbortRef.current) {
      return;
    }

    queryAbortRef.current.abort();
    queryAbortRef.current = null;
    setQuerying(false);
    showMessage()?.info("已停止当前检索");
  }, []);

  const handleClearQueryResult = useCallback(() => {
    setQueryAnswer("");
    setQueryError("");
    setQueryRawChunks([]);
  }, []);

  useEffect(() => {
    void refreshKnowledgeBases();
  }, [refreshKnowledgeBases]);

  useEffect(() => {
    if (!selectedKnowledgeBaseId) {
      setFiles([]);
      setFilesError("");
      return;
    }

    void refreshFiles(selectedKnowledgeBaseId);
  }, [refreshFiles, selectedKnowledgeBaseId]);

  useEffect(() => {
    if (!selectedKnowledgeBaseId || !hasProcessingFiles(files)) {
      return;
    }

    const timer = window.setTimeout(() => {
      void refreshFiles(selectedKnowledgeBaseId, { silent: true });
    }, MRAG_FILE_POLL_INTERVAL_MS);

    return () => window.clearTimeout(timer);
  }, [files, refreshFiles, selectedKnowledgeBaseId]);

  return (
    <>
      <input
        ref={fileInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={(event) => {
          void handleFileInputChange(event);
        }}
      />
      <WorkspaceMRagView
        toolBaseUrlDraft={toolBaseUrlDraft}
        activeToolBaseUrl={workspaceState.toolBaseUrl}
        onToolBaseUrlChange={setToolBaseUrlDraft}
        onApplyToolBaseUrl={applyToolBaseUrl}
        knowledgeBases={knowledgeBases}
        knowledgeBasesLoading={knowledgeBasesLoading}
        knowledgeBasesError={knowledgeBasesError}
        selectedKnowledgeBaseId={selectedKnowledgeBaseId}
        onSelectKnowledgeBase={setSelectedKnowledgeBaseId}
        onRefreshKnowledgeBases={() => {
          void refreshKnowledgeBases();
        }}
        createKnowledgeBaseName={createKnowledgeBaseName}
        createKnowledgeBaseDesc={createKnowledgeBaseDesc}
        onCreateKnowledgeBaseNameChange={setCreateKnowledgeBaseName}
        onCreateKnowledgeBaseDescChange={setCreateKnowledgeBaseDesc}
        creatingKnowledgeBase={creatingKnowledgeBase}
        onCreateKnowledgeBase={() => {
          void handleCreateKnowledgeBase();
        }}
        selectedKnowledgeBase={selectedKnowledgeBase}
        files={files}
        filesLoading={filesLoading}
        filesError={filesError}
        uploadingFiles={uploadingFiles}
        addingWebUrl={addingWebUrl}
        webUrl={webUrl}
        onWebUrlChange={setWebUrl}
        onUploadFiles={handleUploadFiles}
        onAddWebUrl={() => {
          void handleAddWebUrl();
        }}
        onRefreshFiles={() => {
          if (selectedKnowledgeBaseId) {
            void refreshFiles(selectedKnowledgeBaseId);
          }
        }}
        onDeleteFile={handleDeleteFile}
        question={question}
        onQuestionChange={setQuestion}
        querying={querying}
        queryAnswer={queryAnswer}
        queryError={queryError}
        queryRawChunks={queryRawChunks}
        onSubmitQuery={() => {
          void handleSubmitQuery();
        }}
        onStopQuery={handleStopQuery}
        onClearQueryResult={handleClearQueryResult}
      />
    </>
  );
};

export default WorkspaceMRag;
