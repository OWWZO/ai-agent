import { memo, useState } from "react";
import classNames from "classnames";
import {
  ChevronDown,
  ChevronRight,
  FileCode,
  FileIcon,
  FileSpreadsheet,
  FileText,
  Folder,
  ImageIcon,
} from "lucide-react";
import {
  formatWorkspaceBytes,
  workspaceFileKey,
  type WorkspaceFileItem,
  type WorkspaceTreeNode,
} from "./workspaceFiles";

type WorkspaceFileTreeProps = {
  nodes: WorkspaceTreeNode[];
  selectedFileKey?: string;
  onSelectFile?: (file: WorkspaceFileItem) => void;
  depth?: number;
};

const getFileIcon = (type: string) => {
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
    case "css":
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

const TreeNode = memo(function TreeNode({
  node,
  selectedFileKey,
  onSelectFile,
  depth,
}: {
  node: WorkspaceTreeNode;
  selectedFileKey?: string;
  onSelectFile?: (file: WorkspaceFileItem) => void;
  depth: number;
}) {
  const [expanded, setExpanded] = useState(depth === 0);

  if (node.kind === "dir") {
    return (
      <div>
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="flex w-full items-center gap-2.5 rounded-[14px] bg-black/[0.035] px-3 py-2.5 text-left transition-colors hover:bg-black/[0.055]"
          style={{ marginLeft: depth * 12 }}
        >
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/80">
            <Folder className="h-4 w-4 text-[#8b8b90]" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-[13px] font-medium text-[var(--chat-text)]">
              {node.name}
            </span>
            <span className="block text-[11px] text-[var(--chat-text-muted)]">
              {formatWorkspaceBytes(node.size)}
            </span>
          </span>
          {expanded ? (
            <ChevronDown className="h-4 w-4 shrink-0 text-[var(--chat-text-muted)]" />
          ) : (
            <ChevronRight className="h-4 w-4 shrink-0 text-[var(--chat-text-muted)]" />
          )}
        </button>
        {expanded ? (
          <div className="mt-0.5 flex flex-col gap-0.5">
            {node.children.map((child) => (
              <TreeNode
                key={child.path}
                node={child}
                selectedFileKey={selectedFileKey}
                onSelectFile={onSelectFile}
                depth={depth + 1}
              />
            ))}
          </div>
        ) : null}
      </div>
    );
  }

  const key = workspaceFileKey(node.file);
  const active = key === selectedFileKey;
  return (
    <button
      type="button"
      onClick={() => onSelectFile?.(node.file)}
      className={classNames(
        "flex w-full items-center gap-2.5 rounded-[14px] px-3 py-2.5 text-left transition-colors",
        active
          ? "bg-black/[0.08] text-[var(--chat-text)]"
          : "text-[var(--chat-text)] hover:bg-black/[0.04]"
      )}
      style={{ marginLeft: depth * 12 }}
    >
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/70">
        {getFileIcon(node.file.type)}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] font-medium">{node.name}</span>
        <span className="block text-[11px] text-[var(--chat-text-muted)]">
          {node.file.missing
            ? node.file.missingReason || "内容不可读取"
            : formatWorkspaceBytes(node.size)}
        </span>
      </span>
    </button>
  );
});

const WorkspaceFileTree = memo(function WorkspaceFileTree(
  props: WorkspaceFileTreeProps
) {
  const { nodes, selectedFileKey, onSelectFile, depth = 0 } = props;
  if (!nodes.length) {
    return (
      <div className="px-2.5 py-8 text-center text-[12px] text-[var(--chat-text-muted)]">
        当前会话暂无文件
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-1">
      {nodes.map((node) => (
        <TreeNode
          key={node.path}
          node={node}
          selectedFileKey={selectedFileKey}
          onSelectFile={onSelectFile}
          depth={depth}
        />
      ))}
    </div>
  );
});

WorkspaceFileTree.displayName = "WorkspaceFileTree";

export default WorkspaceFileTree;
