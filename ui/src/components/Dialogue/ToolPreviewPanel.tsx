import { Fragment, memo, useMemo } from "react";
import { ArrowLeft, ChevronRightIcon, X } from "lucide-react";
import { useRequest } from "ahooks";
import { Alert } from "antd";
import { DiffCodeFence } from "./markdown/KimiCodeFence";
import { ToolOutputBlock } from "./tools/ToolOutputBlock";
import SourceCodeView from "../ActionPanel/SourceCodeView";
import Loading from "../ActionPanel/Loading";
import { cn } from "@/lib/utils";
import {
  FILE_KIND_TONE,
  fileKindBadge,
  resolveFileKind,
} from "@/utils/fileKind";
import {
  resolveFilePreviewKind,
  shouldShowPreviewHeaderDivider,
} from "@/lib/filePreview/kinds";
import { pathBreadcrumb, type CodeViewerSource } from "@/lib/codeViewerSource";

type ToolPreviewPanelProps = {
  source: CodeViewerSource;
  /**
   * `file` 类型的可读 URL。
   *
   * 决策点 B（见 TASKS.md §5）：前端没有「按任意绝对路径读文件」的接口，
   * 因此 `file` source 只有在能映射到会话产物 URL 时才有内容可读；
   * 映射不上时给出明确的降级提示，而不是空白面板。
   */
  fileUrl?: string;
  /** 降级原因（文件不在会话产物中 / 已失效 / 超过体积上限等） */
  unavailableReason?: string;
  /** 工具执行输出，作为 patch 之外的兜底内容 */
  output?: string[];
  status?: "running" | "ok" | "error";
  onBack?: () => void;
  onClose?: () => void;
};

/** 面板头部图标按钮：与 `ActionView` 的 `iconBtnClass` 同规格，保证两侧观感一致。 */
const headerIconBtnClass =
  "flex h-7 w-7 items-center justify-center rounded-lg text-foreground-subtle transition-colors hover:bg-surface-hover hover:text-foreground";

/**
 * 右侧工具预览面板（`ToolDiffPanel` 的泛化版）。
 *
 * 与旧面板的唯一区别是：不再假设「一定是 diff」，而是按 `CodeViewerSource.type` 分派：
 * - `patch` → `DiffCodeFence`（保持原渲染，验收要求与旧 diff 一模一样）
 * - `file`  → fetch 文本 → `SourceCodeView`（行号 / 行选择 / 行内评论）
 * - `text`  → 直接用 content 渲染 `SourceCodeView`
 * - `image` → 图片链路
 *
 * 面板头部（返回 / 关闭）与 `onBack` 语义完全保留，因此右侧的优先级链
 * （desktop → thinking → preview → agentDetail → ActionView）不受影响。
 */
export const ToolPreviewPanel = memo(function ToolPreviewPanel({
  source,
  fileUrl,
  unavailableReason,
  output = [],
  status = "ok",
  onBack,
  onClose,
}: ToolPreviewPanelProps) {
  const language = source.type === "text" ? source.language : undefined;

  /**
   * 单行面包屑：`parents › file`。
   *
   * 对齐 ZCode `PreviewPane.tsx:1546-1568`。旧版这里是三行堆叠
   * （`预览` / `title` / `path`），而 source tab 的 `title` 与 `path` 常常是同一个
   * 裸文件名（如 `generate_fixtures.py`）—— 于是同一个名字被渲染了两遍。
   */
  const crumb = useMemo(() => {
    const raw = source.path || source.title;
    const parsed = pathBreadcrumb(raw);
    return {
      parents: parsed.parents,
      file: source.title || parsed.file,
    };
  }, [source]);

  // 头部底部分隔线按 ZCode 的规则条件渲染：只有 pdf / Office / 音视频
  // 这类「下方换成独立 viewer」的预览才画线，代码 / 文本 / 图片不画。
  // 判定必须走 resolveFilePreviewKind（预览类型唯一真相），
  // 不能复用 utils/fileKind 的 FileKind —— 那是给徽章用的另一套枚举。
  const showHeaderDivider = useMemo(
    () =>
      shouldShowPreviewHeaderDivider(
        resolveFilePreviewKind({ name: crumb.file })
      ),
    [crumb.file]
  );

  const kind = useMemo(
    () => resolveFileKind(undefined, crumb.file),
    [crumb.file]
  );

  // file 类型：把 URL 上的文本取回来交给 SourceCodeView。
  // text / patch / image 不需要请求，因此用 ready 关掉。
  // 已知不可用（不在产物清单里）时直接跳过请求，同步给出降级说明 —— 不要先闪一个 spinner。
  const needsFetch = source.type === "file" && !unavailableReason;
  const { data, loading, error } = useRequest(
    async () => {
      if (!fileUrl) {
        throw new Error("该文件不在会话产物中，无法预览");
      }
      const response = await fetch(fileUrl);
      if (!response.ok) {
        throw new Error("引用资源不存在或已失效");
      }
      return await response.text();
    },
    {
      refreshDeps: [fileUrl, source.type, source.path],
      ready: needsFetch,
    }
  );

  const body = useMemo(() => {
    if (source.type === "patch") {
      const showDiff = Boolean(source.patch) && status !== "error";
      if (showDiff) {
        return <DiffCodeFence code={source.patch} />;
      }
      if (output.length > 0) {
        return (
          <div className="px-3 py-2">
            <ToolOutputBlock
              lines={output}
              emptyText={status === "error" ? "工具执行失败" : "No output"}
            />
          </div>
        );
      }
      return (
        <div className="kimi-detail-panel-empty">
          {status === "error" ? "工具执行失败，无可用 diff" : "暂无 diff"}
        </div>
      );
    }

    if (source.type === "text") {
      return (
        <SourceCodeView
          code={source.content}
          language={source.language || "text"}
          className="h-full"
          sourcePath={source.path || source.title}
        />
      );
    }

    if (source.type === "image") {
      // 图片链路复用会话产物 URL：能映射到就渲染，映射不到给降级说明。
      if (!fileUrl) {
        return (
          <div className="p-4">
            <Alert
              type="info"
              showIcon
              message="该文件不在会话产物中，无法预览"
              description={source.path}
            />
          </div>
        );
      }
      return (
        <div className="flex h-full min-h-0 items-center justify-center p-4">
          <img
            src={fileUrl}
            alt={source.title}
            className="max-h-full max-w-full object-contain"
          />
        </div>
      );
    }

    // source.type === "file"
    if (unavailableReason) {
      return (
        <div className="p-4">
          <Alert
            type="info"
            showIcon
            message="该文件不在会话产物中，无法预览"
            description={unavailableReason}
          />
        </div>
      );
    }
    if (loading) {
      return (
        <div className="flex h-full min-h-[200px] items-center justify-center">
          <Loading className="mr-32" />
        </div>
      );
    }
    if (error) {
      return (
        <div className="p-4">
          <Alert
            type="info"
            showIcon
            message="该文件不在会话产物中，无法预览"
            description={source.path}
          />
        </div>
      );
    }
    return (
      <SourceCodeView
        code={data || ""}
        language={language || "text"}
        className="h-full"
        sourcePath={source.path}
      />
    );
  }, [
    source,
    status,
    output,
    fileUrl,
    loading,
    error,
    data,
    unavailableReason,
    language,
  ]);

  return (
    <div className="kimi-detail-panel" data-testid="tool-preview-panel">
      {/* 头部：单行 h-10 面包屑（对齐 ZCode PreviewPane.tsx:1531-1570）。
          底部线条件渲染 —— 见 shouldShowPreviewHeaderDivider()。 */}
      <div
        className={cn(
          "flex h-10 shrink-0 items-center gap-8 px-3",
          showHeaderDivider && "border-b border-border/50"
        )}
      >
        <div className="flex min-w-0 flex-1 items-center gap-0.5 overflow-x-auto text-ui-base text-foreground-subtle [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {crumb.parents.map((segment, index) => (
            <Fragment key={`${segment}-${index}`}>
              <span className="shrink-0">{segment}</span>
              <ChevronRightIcon className="size-3.5 shrink-0 text-foreground-subtlest" />
            </Fragment>
          ))}
          <span className="inline-flex min-w-0 items-center gap-1 text-foreground">
            <span
              className={cn(
                "flex size-4 shrink-0 items-center justify-center rounded-sm text-ui-xs font-bold leading-none",
                FILE_KIND_TONE
              )}
            >
              {fileKindBadge(kind)}
            </span>
            <span className="truncate">{crumb.file}</span>
          </span>
        </div>
        <div className="flex shrink-0 items-center gap-0.5">
          {onBack ? (
            <button
              type="button"
              className={headerIconBtnClass}
              title="返回子 Agent"
              aria-label="返回子 Agent"
              onClick={onBack}
            >
              <ArrowLeft className="h-3.5 w-3.5" />
            </button>
          ) : null}
          <button
            type="button"
            className={headerIconBtnClass}
            title="关闭"
            aria-label="关闭"
            onClick={onClose}
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
      <div className="kimi-detail-panel-body">{body}</div>
    </div>
  );
});
