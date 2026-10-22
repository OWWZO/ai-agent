import { memo, useCallback, useMemo, useState } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Info } from "lucide-react";
import type { BundledLanguage } from "shiki";
import { useCodePreviewWrap } from "@/hooks/useCodePreviewWrap";
import { dispatchCodeCommentAddToChat, type CodeCommentRange } from "@/lib/promptContexts";
import { cn } from "@/lib/utils";
import { CodeViewer, CodeWrapToggle } from "./codeViewer";
import MermaidBlock from "./MermaidBlock";
import PreviewModeToggle, { type PreviewViewMode } from "./PreviewModeToggle";

/** 超过该字节数不做语法高亮：高亮是 CPU 密集操作，大文件会把主线程卡死。 */
export const FILE_VIEWER_MAX_TEXT_BYTES = 256 * 1024;

interface SourceCodeViewProps {
  code: string;
  language: BundledLanguage | string;
  className?: string;
  /**
   * 源文件路径（或文件名）。
   *
   * 提供后才开启「选中行 → 写评论 → 加入聊天」：评论会以 `File: <path>` 的形式
   * 拼进 prompt 尾块，模型据此定位；拿不到路径时该能力整体关闭（评论无法定位就没有意义）。
   */
  sourcePath?: string;
  /**
   * 显式指定自动换行。不传则跟随全局偏好（`useCodePreviewWrap`）并显示右上角开关；
   * 传了即为受控，不再显示开关。
   */
  wrapLongLines?: boolean;
  /** 不显示自动换行开关（外层已有同义控件时）。 */
  hideWrapToggle?: boolean;
}

const estimateByteLength = (code: string): number => {
  if (code.length <= FILE_VIEWER_MAX_TEXT_BYTES / 4) {
    // 字符数都远小于阈值时不必精确计算
    return code.length;
  }
  try {
    return new TextEncoder().encode(code).length;
  } catch {
    return code.length;
  }
};

function getSourceTitle(path: string): string {
  return path.split(/[\\/]/).filter(Boolean).pop() ?? path;
}

/**
 * 源码视图。
 *
 * 渲染交给 `CodeViewer`（`@pierre/diffs`，与 ZCode `CodeViewer` 同引擎）：
 * 行号 gutter、行 hover、行选择、gutter `+`、行内评论全部由库在 Shadow DOM 内绘制。
 * 本组件只保留四件前置处理：
 * 1. `mermaid` 语言特判 —— 默认渲染成图，并提供「图形 / 源码」切换（源码模式落回代码高亮）；
 * 2. 256KB 阈值 —— 超过则停用高亮，避免主线程被拖死；
 * 3. 把「提交代码评论」接到 prompt 上下文通道（`dispatchCodeCommentAddToChat`）；
 * 4. 自动换行开关 —— 主路径由 `CodeViewer` 自带；降级分支（过大文件）由本组件补一个，
 *    保证两种分支下用户都能切、且共享同一份全局偏好。
 */
const SourceCodeView: CuteType.FC<SourceCodeViewProps> = (props) => {
  const { code, language, className, sourcePath, wrapLongLines, hideWrapToggle = false } = props;
  const tooLarge = useMemo(() => estimateByteLength(code) > FILE_VIEWER_MAX_TEXT_BYTES, [code]);

  const [globalWrap, , toggleGlobalWrap] = useCodePreviewWrap();
  const isWrapControlled = wrapLongLines !== undefined;
  const resolvedWrap = wrapLongLines ?? globalWrap;
  const showWrapToggle = !hideWrapToggle && !isWrapControlled;

  const isMermaid = language === "mermaid";
  const [mermaidMode, setMermaidMode] = useState<PreviewViewMode>("preview");

  const normalizedSourcePath = (sourcePath || "").trim();
  const canComment = normalizedSourcePath.length > 0;

  const handleSubmitCodeComment = useCallback(
    ({
      range,
      selectedText,
      comment,
    }: {
      range: CodeCommentRange;
      selectedText: string;
      comment: string;
    }) => {
      if (!canComment) return;
      dispatchCodeCommentAddToChat({
        sourcePath: normalizedSourcePath,
        sourceTitle: getSourceTitle(normalizedSourcePath),
        startLine: range.startLine,
        endLine: range.endLine,
        selectedText,
        comment,
      });
    },
    [canComment, normalizedSourcePath]
  );

  if (tooLarge) {
    return (
      <div
        className={cn(
          "relative h-full min-h-0 w-full overflow-hidden bg-[var(--chat-surface)]",
          className
        )}
      >
        <div
          className={cn(
            "h-full w-full",
            // 换行模式没有横向溢出；非换行模式要能横向滚动（`w-max` 撑开内容宽度）
            resolvedWrap ? "overflow-y-auto overflow-x-hidden" : "overflow-auto"
          )}
        >
          <Alert
            variant="info"
            className="m-4"
          >
            <Info />
            <AlertTitle>文件过大，已停用语法高亮</AlertTitle>
            <AlertDescription>{`内容超过 ${Math.round(FILE_VIEWER_MAX_TEXT_BYTES / 1024)}KB，为避免页面卡顿不再高亮。请下载后查看完整内容。`}</AlertDescription>
          </Alert>
          <pre
            className={cn(
              "px-2 py-1 font-mono text-[13px] leading-[1.7]",
              resolvedWrap ? "whitespace-pre-wrap break-words" : "w-max min-w-full whitespace-pre"
            )}
          >
            {code.slice(0, FILE_VIEWER_MAX_TEXT_BYTES)}
          </pre>
        </div>
        {showWrapToggle ? (
          <CodeWrapToggle
            wrap={resolvedWrap}
            onToggle={toggleGlobalWrap}
            className="absolute right-3 top-3 z-10"
          />
        ) : null}
      </div>
    );
  }

  if (isMermaid) {
    const modeToggle = (
      <PreviewModeToggle
        mode={mermaidMode}
        onChange={setMermaidMode}
        previewLabel="图形"
        sourceLabel="源码"
      />
    );

    // 源码模式：交给 CodeViewer（shiki 不支持 mermaid 语法，按纯文本高亮）。
    // 「图形/源码」切换经 headerActions 与自动换行开关同排渲染，避免两个浮动控件重叠。
    if (mermaidMode === "source") {
      return (
        <CodeViewer
          code={code}
          language="text"
          className={className}
          enableLineSelection
          enableGutterUtility
          onSubmitCodeComment={canComment ? handleSubmitCodeComment : undefined}
          wrapLongLines={wrapLongLines}
          hideWrapToggle={hideWrapToggle}
          headerActions={modeToggle}
        />
      );
    }

    return (
      <div
        className={cn(
          "relative h-full w-full overflow-hidden bg-[var(--chat-surface)]",
          className
        )}
      >
        <div className="h-full w-full overflow-auto p-4">
          <MermaidBlock className="min-h-full rounded-xl border border-[var(--chat-border)]">
            {code}
          </MermaidBlock>
        </div>
        <div className="absolute right-3 top-3 z-10">{modeToggle}</div>
      </div>
    );
  }

  return (
    <CodeViewer
      code={code}
      language={String(language)}
      className={className}
      enableLineSelection
      enableGutterUtility
      onSubmitCodeComment={canComment ? handleSubmitCodeComment : undefined}
      wrapLongLines={wrapLongLines}
      hideWrapToggle={hideWrapToggle}
    />
  );
};

SourceCodeView.displayName = "SourceCodeView";

export default memo(SourceCodeView);
