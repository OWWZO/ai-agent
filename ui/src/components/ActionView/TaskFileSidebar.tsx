import { memo, useMemo } from "react";
import classNames from "classnames";
import {
  ArrowLeft,
  Copy,
  FileCode,
  FileIcon,
  FileSpreadsheet,
  FileText,
  ImageIcon,
  Link2,
  RefreshCw,
  FolderOpen,
} from "lucide-react";
import type { PanelItemType } from "../ActionPanel";
import {
  collectWorkspaceFiles,
  workspaceFileKey,
  type WorkspaceFileItem,
} from "./workspaceFiles";
import { copyText, showMessage } from "@/utils";

type TaskFileSidebarProps = {
  taskList?: PanelItemType[];
  selectedFileKey?: string;
  onSelectFile?: (file: WorkspaceFileItem) => void;
  onBack: () => void;
  onRefresh?: () => void;
};

const getFileIcon = (type: string) => {
  // 文件图标只依据扩展名分组，具体 URL/文件类型判断由 workspaceFiles 和 fileKind 负责。
  const ext = (type || "").toLowerCase();
  switch (ext) {
    case "png":
    case "jpg":
    case "jpeg":
    case "gif":
    case "webp":
    case "svg":
    case "bmp":
      return <ImageIcon className="h-4 w-4 text-[var(--chat-text-muted)]" />;
    case "csv":
    case "xlsx":
    case "xls":
      return <FileSpreadsheet className="h-4 w-4 text-emerald-500" />;
    case "html":
    case "htm":
    case "code":
    case "js":
    case "ts":
    case "tsx":
    case "jsx":
    case "py":
    case "java":
      return <FileCode className="h-4 w-4 text-blue-500" />;
    case "pdf":
      return <FileText className="h-4 w-4 text-red-500" />;
    case "doc":
    case "docx":
    case "md":
    case "markdown":
    case "txt":
      return <FileText className="h-4 w-4 text-gray-500" />;
    default:
      return <FileIcon className="h-4 w-4 text-gray-400" />;
  }
};

const TaskFileSidebar = memo(function TaskFileSidebar(props: TaskFileSidebarProps) {
  const { taskList, selectedFileKey, onSelectFile, onBack, onRefresh } = props;
  // 任务列表可能由多个工具事件拼接而来，先统一去重/规范化为可展示的工作区文件。
  const files = useMemo(() => collectWorkspaceFiles(taskList), [taskList]);

  const handleCopySelectedLink = () => {
    // 优先复制当前选中文件，否则回退到首个文件；没有可下载引用时不伪造成功提示。
    const selected =
      files.find((item) => workspaceFileKey(item) === selectedFileKey) || files[0];
    const url = selected?.downloadUrl || selected?.url;
    if (!url) {
      showMessage()?.warning("暂无可复制链接");
      return;
    }
    copyText(url);
    showMessage()?.success("链接已复制");
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex shrink-0 items-center justify-between gap-2 px-3 pb-2 pt-1">
        <div className="text-[13px] font-medium text-[var(--chat-text)]">文件</div>
        <div className="flex items-center gap-0.5">
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
            title="打开目录"
            onClick={() => showMessage()?.info("当前会话产物列表")}
          >
            <FolderOpen className="h-3.5 w-3.5" />
          </button>
          <button
            type="button"
            className="inline-flex h-7 w-7 items-center justify-center rounded-md text-[var(--chat-text-muted)] transition-colors hover:bg-black/[0.05] hover:text-[var(--chat-text)]"
            title="复制名称"
            onClick={() => {
              const selected =
                files.find((item) => workspaceFileKey(item) === selectedFileKey) ||
                files[0];
              if (!selected?.name) {
                showMessage()?.warning("暂无文件");
                return;
              }
              copyText(selected.name);
              showMessage()?.success("名称已复制");
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
        {files.length === 0 ? (
          <div className="px-2.5 py-8 text-center text-[12px] text-[var(--chat-text-muted)]">
            当前会话暂无文件
          </div>
        ) : (
          <div className="flex flex-col gap-0.5">
            {files.map((file) => {
              const key = workspaceFileKey(file);
              const active = key === selectedFileKey;
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => onSelectFile?.(file)}
                  className={classNames(
                    "flex w-full items-center gap-2.5 rounded-[10px] px-2.5 py-2 text-left transition-colors",
                    active
                      ? "bg-black/[0.08] text-[var(--chat-text)]"
                      : "text-[var(--chat-text)] hover:bg-black/[0.04]"
                  )}
                >
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white/70">
                    {getFileIcon(file.type)}
                  </span>
                  <span className="min-w-0 flex-1 truncate text-[13px] font-medium">
                    {file.name}
                  </span>
                </button>
              );
            })}
          </div>
        )}
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
