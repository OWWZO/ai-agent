import React, {
  Suspense,
  lazy,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ComponentType,
  type Ref,
} from "react";
import type { LineAnnotation } from "@pierre/diffs";
import type { FileOptions, FileProps } from "@pierre/diffs/react";
import { useIsDarkTheme } from "@/hooks/useIsDarkTheme";
import { useCodePreviewWrap } from "@/hooks/useCodePreviewWrap";
import { DEFAULT_CODE_PREVIEW_SETTINGS, resolveCodePreviewTheme } from "@/lib/codePreviewSettings";
import { cn } from "@/lib/utils";
import Loading from "../Loading";
import { CodeCommentAnnotation } from "./CodeCommentAnnotation";
import { CodeWrapToggle } from "./CodeWrapToggle";
import { CommentDraft } from "./CommentDraft";
import type {
  CodeCommentRange,
  CodeViewerAnnotationMetadata,
  CodeViewerProps,
  CodeViewerStyle,
  SelectedLineRange,
} from "./types";
import {
  CODE_VIEWER_UNSAFE_CSS,
  DEFAULT_CODE_COMMENT_LABELS,
  codeCommentRangeToSelectedRange,
  codeViewerMarkedLinesCss,
  createCodeViewerFile,
  findCodeViewerCommentElement,
  findCodeViewerLineElement,
  getTextForLineRange,
  normalizeRange,
  patchCodeCommentUtilityButtons,
  selectedRangeToCodeCommentRange,
  toCssString,
  walkShadowRoots,
} from "./utils";

const LOADING_CLASS = "mr-32";

/**
 * `@pierre/diffs` 的 `File` 懒加载：自带 shiki + oniguruma WASM，必须独立分包，
 * 只在真的打开代码预览时才拉取。
 */
const LazyDiffsFile = lazy(async () => {
  const mod = await import("@pierre/diffs/react");
  return {
    default: mod.File as unknown as ComponentType<
      FileProps<CodeViewerAnnotationMetadata, undefined>
    >,
  };
});

function setOptionalRefValue<T>(ref: Ref<T> | undefined, value: T | null): void {
  if (!ref) return;
  if (typeof ref === "function") {
    ref(value);
    return;
  }
  (ref as { current: T | null }).current = value;
}

/**
 * 代码查看器 —— 移植自 ZCode `components/ui/code-viewer.tsx`。
 *
 * 在本项目 `DiffsCodeView` 的基础上补齐四件事：
 * 1. **拖选行 / 点 gutter `+`** → 生成评论草稿（`enableLineSelection` / `enableGutterUtility`）；
 * 2. **行内评论卡片**（`lineAnnotations` + `renderAnnotation`）；
 * 3. **滚动定位到指定行**（`focusedRange` + `focusRequestId`）；
 * 4. **指定行行号着色**（`markedLines` → `unsafeCSS`）。
 *
 * ⚠️ 定位有个非直觉点：`@pierre/diffs` 的真实代码行在**异步创建的 Shadow DOM** 里，
 * 只传 `selectedLines` 会高亮但**不会滚动**。所以这里必须用 `requestAnimationFrame`
 * 递归重试最多 45 帧，每帧穿透 shadow root 找 `[data-line="N"]`。
 */
const CodeViewerComponent: ReactorType.FC<CodeViewerProps> = (props) => {
  const {
    code,
    language,
    enableSyntaxHighlighting = true,
    showLineNumbers,
    theme: themeProp,
    wrapLongLines,
    fontSizePx,
    firstLineNumber = 1,
    comments = [],
    focusedRange = null,
    focusRequestId,
    markedLines,
    enableLineSelection = false,
    enableGutterUtility = false,
    labels: labelOverrides,
    onSubmitCodeComment,
    onDeleteCodeComment,
    scrollContainerRef,
    hideWrapToggle = false,
    headerActions,
    className,
    style,
    ...restProps
  } = props;

  const isDark = useIsDarkTheme();
  const settings = DEFAULT_CODE_PREVIEW_SETTINGS;
  const theme = themeProp ?? resolveCodePreviewTheme(isDark, settings);
  const resolvedShowLineNumbers = showLineNumbers ?? settings.showLineNumbers;
  // 换行：显式传入即受控（此时不显示内部开关）；否则跟随全局偏好。
  const [globalWrap, , toggleGlobalWrap] = useCodePreviewWrap();
  const isWrapControlled = wrapLongLines !== undefined;
  const resolvedWrapLongLines = wrapLongLines ?? globalWrap;
  const resolvedFontSizePx = fontSizePx ?? settings.fontSizePx;
  const showWrapToggle = !hideWrapToggle && !isWrapControlled;

  const handleToggleWrap = useCallback(() => {
    if (isWrapControlled) return;
    toggleGlobalWrap();
  }, [isWrapControlled, toggleGlobalWrap]);

  const viewerRef = useRef<HTMLDivElement>(null);
  const [activeDraftRange, setActiveDraftRange] = useState<CodeCommentRange | null>(null);
  const [draftText, setDraftText] = useState("");

  const canCreateComment = Boolean(onSubmitCodeComment);
  // 只读代码不该看起来仍可评论：把官方交互开关收敛到「有提交能力」上。
  const canUseCommentLineSelection = canCreateComment && enableLineSelection;
  const canUseCommentGutterUtility = canCreateComment && enableGutterUtility;

  const labels = useMemo(
    () => ({ ...DEFAULT_CODE_COMMENT_LABELS, ...labelOverrides }),
    [labelOverrides]
  );

  const file = useMemo(
    () => createCodeViewerFile({ code, enableSyntaxHighlighting, language, theme }),
    [code, enableSyntaxHighlighting, language, theme]
  );

  const lineAnnotations = useMemo<LineAnnotation<CodeViewerAnnotationMetadata>[]>(() => {
    const annotations: LineAnnotation<CodeViewerAnnotationMetadata>[] = [];

    for (const comment of comments) {
      const lineNumber = normalizeRange(comment).endLine - firstLineNumber + 1;
      if (lineNumber < 1) continue;
      annotations.push({ lineNumber, metadata: { kind: "comment", comment } });
    }

    if (canCreateComment && activeDraftRange) {
      annotations.push({
        lineNumber: normalizeRange(activeDraftRange).endLine - firstLineNumber + 1,
        metadata: { kind: "draft", range: activeDraftRange },
      });
    }

    return annotations;
  }, [activeDraftRange, canCreateComment, comments, firstLineNumber]);

  const selectedLines = useMemo(() => {
    if (canCreateComment && activeDraftRange) {
      return codeCommentRangeToSelectedRange(activeDraftRange, firstLineNumber);
    }
    return focusedRange ? codeCommentRangeToSelectedRange(focusedRange, firstLineNumber) : null;
  }, [activeDraftRange, canCreateComment, firstLineNumber, focusedRange]);

  const handleLineSelectionEnd = useCallback(
    (range: SelectedLineRange | null) => {
      if (!canCreateComment || !range) return;
      setActiveDraftRange(selectedRangeToCodeCommentRange(range, firstLineNumber));
      setDraftText("");
    },
    [canCreateComment, firstLineNumber]
  );

  const handleGutterUtilitySelection = useCallback(
    (range: SelectedLineRange) => {
      if (!canCreateComment) return;
      setActiveDraftRange(selectedRangeToCodeCommentRange(range, firstLineNumber));
      setDraftText("");
    },
    [canCreateComment, firstLineNumber]
  );

  const handleSubmitDraft = useCallback(() => {
    if (!activeDraftRange) return;
    const selectedText = getTextForLineRange(code, firstLineNumber, activeDraftRange);
    if (!selectedText.trim()) return;
    onSubmitCodeComment?.({
      range: normalizeRange(activeDraftRange),
      selectedText,
      comment: draftText.trim(),
    });
    setActiveDraftRange(null);
    setDraftText("");
  }, [activeDraftRange, code, draftText, firstLineNumber, onSubmitCodeComment]);

  // gutter `+` 按钮由库的 InteractionManager 生成，这里只补 title / aria-label，
  // 不接管渲染或 pointer 事件（接管会破坏拖拽多行评论）。
  useEffect(() => {
    if (!canUseCommentGutterUtility || !viewerRef.current) return;

    const root = viewerRef.current;
    const observers: MutationObserver[] = [];
    const observedRoots = new WeakSet<Node>();
    let animationFrame = 0;

    const observeRoot = (target: ParentNode) => {
      if (observedRoots.has(target)) return;
      observedRoots.add(target);
      const observer = new MutationObserver(schedulePatch);
      observer.observe(target, { childList: true, subtree: true });
      observers.push(observer);
    };

    const patch = () => {
      patchCodeCommentUtilityButtons(root, labels.addCommentTooltip);
      walkShadowRoots(root, (shadowRoot) => {
        observeRoot(shadowRoot);
        patchCodeCommentUtilityButtons(shadowRoot, labels.addCommentTooltip);
      });
    };

    function schedulePatch() {
      if (animationFrame) return;
      animationFrame = window.requestAnimationFrame(() => {
        animationFrame = 0;
        patch();
      });
    }

    observeRoot(root);
    patch();
    schedulePatch();

    return () => {
      if (animationFrame) window.cancelAnimationFrame(animationFrame);
      for (const observer of observers) observer.disconnect();
    };
  }, [canUseCommentGutterUtility, labels.addCommentTooltip]);

  const focusedStartLine = focusedRange?.startLine;
  const focusedEndLine = focusedRange?.endLine;
  useEffect(() => {
    if (!focusRequestId || focusedStartLine === undefined || !viewerRef.current) return;

    let animationFrame = 0;
    let attempts = 0;
    const targetLine = focusedStartLine - firstLineNumber + 1;
    const scrollToFocusedLine = () => {
      const container = viewerRef.current;
      const line = container ? findCodeViewerLineElement(container, targetLine) : null;
      const comment = container ? findCodeViewerCommentElement(container, focusRequestId) : null;

      if (container && comment) {
        const containerRect = container.getBoundingClientRect();
        const commentRect = comment.getBoundingClientRect();
        const horizontalScrollLeft = container.scrollLeft;
        const commentBottom = container.scrollTop + commentRect.bottom - containerRect.top;
        // 评论跟随代码滚动；点击卡片时把评论底部放到可视区下沿，让目标行与评论同时可见。
        container.scrollTop = Math.max(0, commentBottom - container.clientHeight + 12);
        container.scrollLeft = horizontalScrollLeft;
        return;
      }
      if (container && line) {
        const containerRect = container.getBoundingClientRect();
        const lineRect = line.getBoundingClientRect();
        const horizontalScrollLeft = container.scrollLeft;
        const lineCenter =
          container.scrollTop + lineRect.top - containerRect.top + lineRect.height / 2;
        container.scrollTop = Math.max(0, lineCenter - container.clientHeight / 2);
        container.scrollLeft = horizontalScrollLeft;
        return;
      }

      attempts += 1;
      if (attempts < 45) {
        animationFrame = window.requestAnimationFrame(scrollToFocusedLine);
      }
    };

    animationFrame = window.requestAnimationFrame(scrollToFocusedLine);
    return () => window.cancelAnimationFrame(animationFrame);
  }, [file.cacheKey, firstLineNumber, focusedEndLine, focusedStartLine, focusRequestId]);

  const markedLinesCss = codeViewerMarkedLinesCss(markedLines);

  const options = useMemo<FileOptions<CodeViewerAnnotationMetadata, undefined>>(
    () => ({
      disableFileHeader: true,
      disableLineNumbers: !resolvedShowLineNumbers,
      overflow: resolvedWrapLongLines ? "wrap" : "scroll",
      theme,
      // shiki 默认 JS 正则引擎会把 TextMate 语法编译成巨型 RegExp 并永久持有，
      // 长时间运行会占满 V8 code space（ZCode 已记录该 OOM）。
      preferredHighlighter: "shiki-wasm" as const,
      enableLineSelection: canUseCommentLineSelection,
      enableGutterUtility: canUseCommentGutterUtility,
      lineHoverHighlight:
        canUseCommentLineSelection || canUseCommentGutterUtility ? "both" : "disabled",
      onLineSelectionEnd: handleLineSelectionEnd,
      // 自定义 renderGutterUtility 只能拿 hover 行，拖拽时会退化成单行。
      // 用库的 gutter selection 回调，让点击与拖拽走同一套 range 计算。
      onGutterUtilityClick: canUseCommentGutterUtility ? handleGutterUtilitySelection : undefined,
      // gutter `+` 默认贴在行号右侧，拖拽时容易和代码起点混在一起，这里只挪位置。
      unsafeCSS: CODE_VIEWER_UNSAFE_CSS + markedLinesCss,
    }),
    [
      canUseCommentGutterUtility,
      canUseCommentLineSelection,
      handleGutterUtilitySelection,
      handleLineSelectionEnd,
      markedLinesCss,
      resolvedShowLineNumbers,
      resolvedWrapLongLines,
      theme,
    ]
  );

  const viewerStyle = useMemo<CodeViewerStyle>(
    () => ({
      // 应用暗色是 `.dark` class 驱动，而 @pierre/diffs 的 token 依赖 light-dark()。
      // 必须显式指定 colorScheme，否则暗色下代码会渲染成浅色主题的深色文字。
      colorScheme: isDark ? "dark" : "light",
      "--diffs-bg": "var(--chat-surface)",
      "--diffs-light-bg": "var(--chat-surface)",
      "--diffs-dark-bg": "var(--chat-surface)",
      // 库在 Shadow DOM 内用自己的等宽 fallback，Windows 中文会落到宋体。
      "--diffs-font-family": "var(--font-mono)",
      "--diffs-font-size": `${resolvedFontSizePx}px`,
      "--code-comment-add-tooltip": toCssString(
        canUseCommentGutterUtility ? labels.addCommentTooltip : ""
      ),
      ...style,
    }),
    [canUseCommentGutterUtility, isDark, labels.addCommentTooltip, resolvedFontSizePx, style]
  );

  const assignScrollContainerRef = useCallback(
    (node: HTMLDivElement | null) => {
      viewerRef.current = node;
      setOptionalRefValue(scrollContainerRef, node);
    },
    [scrollContainerRef]
  );

  return (
    <div
      className={cn("relative h-full w-full overflow-hidden bg-[var(--chat-surface)]", className)}
      data-language={language}
      data-code-viewer="diffs"
      style={viewerStyle}
      {...restProps}
    >
      <div ref={assignScrollContainerRef} className="h-full w-full overflow-auto">
        <Suspense
          fallback={
            <div className="flex h-full min-h-[200px] items-center justify-center">
              <Loading className={LOADING_CLASS} />
            </div>
          }
        >
          <LazyDiffsFile
            key={file.cacheKey}
            file={file}
            options={options}
            lineAnnotations={lineAnnotations}
            selectedLines={selectedLines}
            className="min-h-full w-full"
            style={viewerStyle}
            renderAnnotation={(annotation) =>
              annotation.metadata.kind === "draft" ? (
                <CommentDraft
                  range={annotation.metadata.range}
                  labels={labels}
                  value={draftText}
                  onValueChange={setDraftText}
                  onSubmit={handleSubmitDraft}
                  onCancel={() => {
                    setActiveDraftRange(null);
                    setDraftText("");
                  }}
                />
              ) : (
                <CodeCommentAnnotation
                  comment={annotation.metadata.comment}
                  labels={labels}
                  onDelete={onDeleteCodeComment}
                />
              )
            }
          />
        </Suspense>
      </div>
      {showWrapToggle || headerActions ? (
        <div className="absolute right-3 top-3 z-10 flex items-center gap-1.5">
          {headerActions}
          {showWrapToggle ? (
            <CodeWrapToggle wrap={resolvedWrapLongLines} onToggle={handleToggleWrap} />
          ) : null}
        </div>
      ) : null}
    </div>
  );
};

CodeViewerComponent.displayName = "CodeViewer";

export const CodeViewer = React.memo(CodeViewerComponent);
export default CodeViewer;
