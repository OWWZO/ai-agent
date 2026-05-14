import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Modal } from "antd";

import WorkspaceMRagView from "./view";
import {
  loadMRagWorkspaceStoredState,
  persistMRagWorkspaceStoredState,
} from "./utils";
import { showMessage } from "@/utils";
import { trimTrailingSlash } from "@/pages/WorkspaceImageGeneration/utils";
import {
  resolveKnowledgeBaseAfterDeletion,
  resolveSelectedKnowledgeBaseId,
  shouldBootstrapKnowledgeBases,
} from "./knowledgeBaseState";
import { useKnowledgeBaseCatalog } from "./useKnowledgeBaseCatalog";
import { useKnowledgeBaseFiles } from "./useKnowledgeBaseFiles";
import { useMragQuery } from "./useMragQuery";

interface WorkspaceMRagProps {
  embedded?: boolean;
}

const WorkspaceMRag: ReactorType.FC<WorkspaceMRagProps> = ({ embedded }) => {
  const initialWorkspaceState = useMemo(() => loadMRagWorkspaceStoredState(), []);
  const [workspaceState, setWorkspaceState] = useState(initialWorkspaceState);
  const [toolBaseUrlDraft, setToolBaseUrlDraft] = useState(
    initialWorkspaceState.toolBaseUrl
  );
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState(
    initialWorkspaceState.selectedKnowledgeBaseId
  );
  const bootstrappedToolBaseUrlRef = useRef<string | null>(null);
  const catalog = useKnowledgeBaseCatalog(workspaceState.toolBaseUrl);
  const filesState = useKnowledgeBaseFiles(
    workspaceState.toolBaseUrl,
    selectedKnowledgeBaseId
  );
  const queryState = useMragQuery(
    workspaceState.toolBaseUrl,
    selectedKnowledgeBaseId
  );

  const selectedKnowledgeBase = useMemo(
    () =>
      catalog.knowledgeBases.find((item) => item.id === selectedKnowledgeBaseId) ||
      null,
    [catalog.knowledgeBases, selectedKnowledgeBaseId]
  );

  useEffect(() => {
    persistMRagWorkspaceStoredState({
      toolBaseUrl: workspaceState.toolBaseUrl,
      selectedKnowledgeBaseId,
    });
  }, [selectedKnowledgeBaseId, workspaceState.toolBaseUrl]);

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
      void catalog.refreshKnowledgeBases();
      return;
    }
  }, [catalog, toolBaseUrlDraft, workspaceState.toolBaseUrl]);

  useEffect(() => {
    if (
      !shouldBootstrapKnowledgeBases(
        bootstrappedToolBaseUrlRef.current,
        workspaceState.toolBaseUrl
      )
    ) {
      return;
    }

    bootstrappedToolBaseUrlRef.current = workspaceState.toolBaseUrl;
    void catalog.refreshKnowledgeBases().then((nextKnowledgeBases) => {
      setSelectedKnowledgeBaseId((previous) =>
        resolveSelectedKnowledgeBaseId(
          nextKnowledgeBases,
          previous,
          initialWorkspaceState.selectedKnowledgeBaseId
        )
      );
    });
  }, [
    catalog,
    catalog.refreshKnowledgeBases,
    initialWorkspaceState.selectedKnowledgeBaseId,
    workspaceState.toolBaseUrl,
  ]);

  return (
    <>
      <input
        ref={filesState.fileInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={(event) => {
          void filesState.handleFileInputChange(event);
        }}
      />
      <WorkspaceMRagView
        embedded={embedded}
        toolBaseUrlDraft={toolBaseUrlDraft}
        activeToolBaseUrl={workspaceState.toolBaseUrl}
        onToolBaseUrlChange={setToolBaseUrlDraft}
        onApplyToolBaseUrl={applyToolBaseUrl}
        knowledgeBases={catalog.knowledgeBases}
        knowledgeBasesLoading={catalog.knowledgeBasesLoading}
        knowledgeBasesError={catalog.knowledgeBasesError}
        selectedKnowledgeBaseId={selectedKnowledgeBaseId}
        onSelectKnowledgeBase={setSelectedKnowledgeBaseId}
        onRefreshKnowledgeBases={() => {
          void catalog.refreshKnowledgeBases().then((nextKnowledgeBases) => {
            setSelectedKnowledgeBaseId((previous) =>
              resolveSelectedKnowledgeBaseId(nextKnowledgeBases, previous)
            );
          });
        }}
        deletingKnowledgeBaseId={catalog.deletingKnowledgeBaseId}
        onDeleteKnowledgeBase={(kbId) => {
          Modal.confirm({
            title: "确认删除这个知识库吗？",
            content: "删除后会同时清理向量数据、文件记录和正文回显记录。",
            okText: "确认删除",
            cancelText: "取消",
            okButtonProps: { danger: true },
            async onOk() {
              const deletedResult = await catalog.deleteKnowledgeBaseById(kbId);
              if (!deletedResult) {
                return;
              }

              const nextKnowledgeBases = await catalog.refreshKnowledgeBases({ silent: true });
              setSelectedKnowledgeBaseId((previous) =>
                resolveKnowledgeBaseAfterDeletion(nextKnowledgeBases, previous, kbId)
              );
              filesState.resetFullContentState();
              queryState.handleClearQueryResult();
              showMessage()?.success(
                deletedResult.deletedFileCount > 0
                  ? `知识库已删除，并清理 ${deletedResult.deletedFileCount} 条资料记录`
                  : "知识库已删除"
              );
            },
          });
        }}
        createKnowledgeBaseName={catalog.createKnowledgeBaseName}
        createKnowledgeBaseDesc={catalog.createKnowledgeBaseDesc}
        onCreateKnowledgeBaseNameChange={catalog.setCreateKnowledgeBaseName}
        onCreateKnowledgeBaseDescChange={catalog.setCreateKnowledgeBaseDesc}
        creatingKnowledgeBase={catalog.creatingKnowledgeBase}
        onCreateKnowledgeBase={() => {
          void catalog.handleCreateKnowledgeBase().then((createdKnowledgeBase) => {
            if (!createdKnowledgeBase) {
              return;
            }
            void catalog
              .refreshKnowledgeBases()
              .then((nextKnowledgeBases) => {
                setSelectedKnowledgeBaseId(
                  resolveSelectedKnowledgeBaseId(
                    nextKnowledgeBases,
                    selectedKnowledgeBaseId,
                    createdKnowledgeBase.id
                  )
                );
              });
          });
        }}
        selectedKnowledgeBase={selectedKnowledgeBase}
        files={filesState.files}
        filesLoading={filesState.filesLoading}
        filesError={filesState.filesError}
        uploadingFiles={filesState.uploadingFiles}
        addingWebUrl={filesState.addingWebUrl}
        webUrl={filesState.webUrl}
        onWebUrlChange={filesState.setWebUrl}
        onUploadFiles={filesState.handleUploadFiles}
        onAddWebUrl={() => {
          void filesState.handleAddWebUrl();
        }}
        onRefreshFiles={() => {
          if (selectedKnowledgeBaseId) {
            void filesState.refreshFiles(selectedKnowledgeBaseId);
          }
        }}
        activeFullContentFileId={filesState.activeFullContentFileId}
        fullContentLoading={filesState.fullContentLoading}
        fullContentDrawerOpen={filesState.fullContentDrawerOpen}
        fullContentTitle={filesState.fullContentTitle}
        fullContentStatus={filesState.fullContentStatus}
        fullContentError={filesState.fullContentError}
        fullContentMarkdown={filesState.fullContentMarkdown}
        onOpenFullContent={(fileId) => {
          void filesState.handleOpenFullContent(fileId);
        }}
        onCloseFullContent={filesState.handleCloseFullContent}
        onDeleteFile={filesState.handleDeleteFile}
        question={queryState.question}
        onQuestionChange={queryState.setQuestion}
        querying={queryState.querying}
        queryAnswer={queryState.queryAnswer}
        queryError={queryState.queryError}
        queryRawChunks={queryState.queryRawChunks}
        onSubmitQuery={() => {
          void queryState.handleSubmitQuery();
        }}
        onStopQuery={queryState.handleStopQuery}
        onClearQueryResult={queryState.handleClearQueryResult}
      />
    </>
  );
};

export default WorkspaceMRag;
