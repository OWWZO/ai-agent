import { memo, useMemo, useState } from "react";
import {
  ArrowLeft,
  Copy,
  Download,
  Link2,
  RefreshCw,
} from "lucide-react";
import type { PanelItemType } from "../ActionPanel";
import {
  buildWorkspaceTree,
  collectWorkspaceFiles,
  workspaceFileKey,
  workspaceRelativePath,
  type WorkspaceFileItem,
} from "./workspaceFiles";
import WorkspaceFileTree from "./WorkspaceFileTree";
import { agentFileApi } from "@/services/agentFile";
import { copyText, showMessage } from "@/utils";

type TaskFileSidebarProps = {
  taskList?: PanelItemType[];
  selectedFileKey?: string;
  sessionId?: string;
  onSelectFile?: (file: WorkspaceFileItem) => void;
  onBack: () => void;
  onRefresh?: () => void;
};

const TaskFileSidebar = memo(function TaskFileSidebar(props: TaskFileSidebarProps) {
  const { taskList, selectedFileKey, sessionId, onSelectFile, onBack, onRefresh } = props;
  const files = useMemo(() => collectWorkspaceFiles(taskList), [taskList]);
  const tree = useMemo(() => buildWorkspaceTree(files), [files]);
  const [archiving, setArchiving] = useState(false);

  const resolveSelected = () =>
    files.find((item) => workspaceFileKey(item) === selectedFileKey) || files[0];

  const handleCopySelectedLink = () => {
    const selected = resolveSelected();
    const url = selected?.downloadUrl || selected?.url;
    if (!url) {
      showMessage()?.warning("暂无可复制链接");
      return;
    }
    copyText(url);
    showMessage()?.success("链接已复制");
  };

  const handleDownloadAll = async () => {
    if (!sessionId) {
      showMessage()?.warning("当前没有会话");
      return;
    }
    if (!files.length) {
      showMessage()?.warning("当前会话暂无文件");
      return;
    }
    setArchiving(true);
    try {
      await agentFileApi.downloadWorkspaceArchive(sessionId);
      showMessage()?.success("已开始下载工作区");
    } catch (error) {
      showMessage()?.error(error instanceof Error ? error.message : "工作区打包失败");
    } finally {
      setArchiving(false);
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex shrink-0 items-center justify-between gap-2 px-3 pb-2 pt-1">
        <div className="text-[13px] font-medium text-[var(--chat-text)]">全部文件</div>
        <div className="flex items-center gap-0.5">
          <button
            type="button"
            className="inline-flex h-7 w-7 items-center justify-center rounded-md text-[var(--chat-text-muted)] transition-colors hover:bg-black/[0.05] hover:text-[var(--chat-text)] disabled:opacity-40"
            title="下载全部"
            disabled={archiving || !files.length}
            onClick={() => void handleDownloadAll()}
          >
            <Download className="h-3.5 w-3.5" />
          </button>
          <button
            type="button"
            className="inline-flex h-7 w-7 items-center justify-center rounded-md text-[var(--chat-text-muted)] transition-colors hover:bg-black/[0.05] hover:text-[var(--chat-text)]"
            title="复制链接"
            onClick={handleCopySelectedLink}
          >
            <Link2 className="h-3.5 w-3.5" />
          </button>
          <button
            type="button"
            className="inline-flex h-7 w-7 items-center justify-center rounded-md text-[var(--chat-text-muted)] transition-colors hover:bg-black/[0.05] hover:text-[var(--chat-text)]"
            title="复制路径"
            onClick={() => {
              const selected = resolveSelected();
              const path = selected ? workspaceRelativePath(selected) || selected.name : "";
              if (!path) {
                showMessage()?.warning("暂无文件");
                return;
              }
              copyText(path);
              showMessage()?.success("路径已复制");
            }}
          >
            <Copy className="h-3.5 w-3.5" />
          </button>
          <button
            type="button"
            className="inline-flex h-7 w-7 items-center justify-center rounded-md text-[var(--chat-text-muted)] transition-colors hover:bg-black/[0.05] hover:text-[var(--chat-text)]"
            title="刷新"
            onClick={() => onRefresh?.()}
          >
            <RefreshCw className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2 scrollbar-hover">
        <WorkspaceFileTree
          nodes={tree}
          selectedFileKey={selectedFileKey}
          onSelectFile={onSelectFile}
        />
      </div>

      <div className="shrink-0 border-t border-[var(--chat-border)]/50 px-2 py-2">
        <button
          type="button"
          onClick={onBack}
          className="flex h-9 w-full items-center gap-2 rounded-[10px] px-2.5 text-[14px] font-medium text-[var(--chat-text)] transition-colors hover:bg-black/[0.04]"
        >
          <ArrowLeft className="h-4 w-4 text-[var(--chat-text-muted)]" />
          返回
        </button>
      </div>
    </div>
  );
});

TaskFileSidebar.displayName = "TaskFileSidebar";

export default TaskFileSidebar;
export type { WorkspaceFileItem };
