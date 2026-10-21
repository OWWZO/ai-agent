import { memo, useState } from "react";
import { cn } from "@/lib/utils";
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

// ZCode WorkspaceFileTreeRowView.tsx:270 —— 行内图标槽统一 size-4、走三级前景。
// 类型色改走语义 token（DESIGN.md 禁 Tailwind 调色板色），保留"颜色即类型"的识别性。
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
      return <ImageIcon className="size-4 text-foreground-subtle" />;
    case "csv":
    case "xlsx":
    case "xls":
      return <FileSpreadsheet className="size-4 text-success" />;
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
      return <FileCode className="size-4 text-brand" />;
    case "pdf":
      return <FileText className="size-4 text-destructive" />;
    case "doc":
    case "docx":
    case "md":
    case "markdown":
    case "txt":
      return <FileText className="size-4 text-foreground-subtle" />;
    default:
      return <FileIcon className="size-4 text-foreground-subtlest" />;
  }
};

/**
 * ZCode WorkspaceFileTreeRowView.tsx:218-224 —— 文件树行的唯一真源。
 * h-7 / rounded-lg / border / hover:bg-surface-hover；选中态只换描边色，
 * 不铺实心底色（`border-input-border-focused`）。
 */
const treeRowClass = (active: boolean) =>
  cn(
    "group/file-tree-row relative flex h-7 w-full min-w-0 items-center gap-1.5 rounded-lg border pr-2 py-1 text-left text-ui-base text-foreground outline-none transition-colors",
    active
      ? "border-input-border-focused hover:bg-surface-hover"
      : "border-transparent hover:bg-surface-hover"
  );

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
          className={treeRowClass(false)}
          style={{ marginLeft: depth * 12 }}
          title={`${node.name} · ${formatWorkspaceBytes(node.size)}`}
        >
          <span className="flex size-4 shrink-0 items-center justify-center text-foreground-subtle">
            <Folder className="size-4" />
          </span>
          <span className="min-w-0 flex-1 truncate">{node.name}</span>
          <span className="shrink-0 text-ui-xs tabular-nums text-foreground-subtlest">
            {formatWorkspaceBytes(node.size)}
          </span>
          {expanded ? (
            <ChevronDown className="size-3 shrink-0 text-foreground-subtlest" />
          ) : (
            <ChevronRight className="size-3 shrink-0 text-foreground-subtlest" />
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
      className={treeRowClass(active)}
      style={{ marginLeft: depth * 12 }}
      title={
        node.file.missing
          ? node.file.missingReason || "内容不可读取"
          : `${node.name} · ${formatWorkspaceBytes(node.size)}`
      }
    >
      <span className="flex size-4 shrink-0 items-center justify-center">
        {getFileIcon(node.file.type)}
      </span>
      <span className="min-w-0 flex-1 truncate">{node.name}</span>
      {node.file.missing ? (
        <span className="max-w-24 shrink-0 truncate text-ui-xs text-destructive">
          {node.file.missingReason || "不可读取"}
        </span>
      ) : (
        <span className="shrink-0 text-ui-xs tabular-nums text-foreground-subtlest">
          {formatWorkspaceBytes(node.size)}
        </span>
      )}
    </button>
  );
});

const WorkspaceFileTree = memo(function WorkspaceFileTree(
  props: WorkspaceFileTreeProps
) {
  const { nodes, selectedFileKey, onSelectFile, depth = 0 } = props;
  if (!nodes.length) {
    return (
      <div className="px-2 py-8 text-center text-ui-sm text-foreground-subtle">
        当前会话暂无文件
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-0.5">
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
