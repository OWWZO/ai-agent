import { useEffect, useMemo, useRef, useState } from "react";
import { Modal } from "antd";

import WorkspaceMRagView from "./view";
import {
  loadMRagWorkspaceStoredState,
  persistMRagWorkspaceStoredState,
} from "./utils";
import { showMessage } from "@/utils";
import {
  resolveKnowledgeBaseAfterDeletion,
  resolveSelectedKnowledgeBaseId,
  shouldBootstrapKnowledgeBases,
} from "./knowledgeBaseState";
import { useKnowledgeBaseCatalog } from "./useKnowledgeBaseCatalog";
import { useKnowledgeBaseFiles } from "./useKnowledgeBaseFiles";
import { useMragHistory } from "./useMragHistory";
import { useMragQuery } from "./useMragQuery";

interface WorkspaceMRagProps {
  embedded?: boolean;
}

const WorkspaceMRag: ReactorType.FC<WorkspaceMRagProps> = ({ embedded }) => {
  // 启动时只读取一次本地工作区状态，后续状态变化通过 effect 持久化。
  const initialWorkspaceState = useMemo(() => loadMRagWorkspaceStoredState(), []);
  const toolBaseUrl = initialWorkspaceState.toolBaseUrl;
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState(
    initialWorkspaceState.selectedKnowledgeBaseId
  );
  const bootstrappedToolBaseUrlRef = useRef<string | null>(null);
  const catalog = useKnowledgeBaseCatalog(toolBaseUrl);
  const filesState = useKnowledgeBaseFiles(
    toolBaseUrl,
    selectedKnowledgeBaseId
  );
  const historyState = useMragHistory(toolBaseUrl, selectedKnowledgeBaseId);
  const queryState = useMragQuery(
    toolBaseUrl,
    selectedKnowledgeBaseId,
    historyState.activeSessionId,
    () => {
      // 查询完成后刷新当前详情和会话列表，确保流式结果与历史记录最终一致。
      if (historyState.activeSessionId) {
        void historyState.loadSessionDetail(historyState.activeSessionId);
      }
      void historyState.refreshSessions();
    }
  );

  const selectedKnowledgeBase = useMemo(
    () =>
      catalog.knowledgeBases.find((item) => item.id === selectedKnowledgeBaseId) ||
      null,
    [catalog.knowledgeBases, selectedKnowledgeBaseId]
  );

  useEffect(() => {
    // 当前知识库和工具地址是可恢复工作区状态的最小持久化集合。
    persistMRagWorkspaceStoredState({
      toolBaseUrl,
      selectedKnowledgeBaseId,
    });
  }, [selectedKnowledgeBaseId, toolBaseUrl]);

  useEffect(() => {
    // 每个工具地址只 bootstrap 一次，避免 hook 重新渲染重复请求知识库目录。
    if (
      !shouldBootstrapKnowledgeBases(
        bootstrappedToolBaseUrlRef.current,
        toolBaseUrl
      )
    ) {
      return;
    }

    bootstrappedToolBaseUrlRef.current = toolBaseUrl;
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
    toolBaseUrl,
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
          // 删除是跨资源操作：成功后同时刷新目录、重置文件全文和查询结果。
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
          // 创建成功后重新取目录，并优先选中新建知识库。
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
        sessions={historyState.sessions}
        sessionsLoading={historyState.sessionsLoading}
        sessionsError={historyState.sessionsError}
        activeSessionId={historyState.activeSessionId}
        sessionTurns={historyState.sessionDetail?.turns || []}
        onCreateSession={() => {
          queryState.handleClearQueryResult();
          void historyState.createSession();
        }}
        onSelectSession={(sessionId) => {
          // 切换历史会话先清理旧查询结果，再按会话记录的知识库恢复上下文。
          queryState.handleClearQueryResult();
          void historyState.loadSessionDetail(sessionId).then((detail) => {
            if (detail?.session.coverKbId) {
              setSelectedKnowledgeBaseId(detail.session.coverKbId);
            }
          });
        }}
        question={queryState.question}
        onQuestionChange={queryState.setQuestion}
        querying={queryState.querying}
        queryAnswer={queryState.queryAnswer}
        queryError={queryState.queryError}
        queryRawChunks={queryState.queryRawChunks}
        onSubmitQuery={() => {
          // 查询入口保证先有活动 session，再把明确的 sessionId 交给流式 hook。
          void historyState.ensureActiveSession().then((sessionId) => {
            void queryState.handleSubmitQuery(sessionId);
          });
        }}
        onStopQuery={queryState.handleStopQuery}
        onClearQueryResult={queryState.handleClearQueryResult}
      />
    </>
  );
};

export default WorkspaceMRag;
