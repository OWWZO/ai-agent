import type { CSSProperties, HTMLAttributes, ReactNode, Ref } from "react";

/**
 * 代码评论能力相关类型。
 *
 * 移植自 ZCode `packages/ui/src/components/ui/code-viewer.tsx`（790 行封装），
 * 底层同样是 `@pierre/diffs` 的 `File`。
 */

export interface CodeCommentRange {
  startLine: number;
  endLine: number;
}

export interface CodeCommentPreview extends CodeCommentRange {
  id: string;
  comment: string;
}

export interface CodeCommentLabels {
  addComment: string;
  addCommentTooltip: string;
  commentPlaceholder: string;
  submitComment: string;
  cancelComment: string;
  deleteComment: string;
  /** `{line}` 占位。 */
  commentLine: string;
  /** `{startLine}` / `{endLine}` 占位。 */
  commentRange: string;
}

/** `renderAnnotation` 收到的元数据：已提交评论 or 正在编辑的草稿。 */
export type CodeViewerAnnotationMetadata =
  | { kind: "comment"; comment: CodeCommentPreview }
  | { kind: "draft"; range: CodeCommentRange };

export type CodeViewerStyle = CSSProperties & {
  "--code-comment-add-tooltip"?: string;
  "--diffs-bg"?: string;
  "--diffs-light-bg"?: string;
  "--diffs-dark-bg"?: string;
  "--diffs-font-family"?: string;
  "--diffs-font-size"?: string;
};

/** `@pierre/diffs` 的 `SelectedLineRange` 子集（本项目只用行号，不关心 side）。 */
export interface SelectedLineRange {
  start: number;
  end: number;
}

export interface CodeViewerProps extends HTMLAttributes<HTMLDivElement> {
  code: string;
  language: string;
  enableSyntaxHighlighting?: boolean;
  showLineNumbers?: boolean;
  theme?: string;
  /**
   * 显式指定换行。**不传时读全局偏好**（`useCodePreviewWrap`），
   * 传了则以传入值为准（受控），此时不显示内置换行开关。
   */
  wrapLongLines?: boolean;
  fontSizePx?: number;
  firstLineNumber?: number;
  comments?: readonly CodeCommentPreview[];
  focusedRange?: CodeCommentRange | null;
  focusRequestId?: string;
  /**
   * 需要点名的行号（1 起，与 `firstLineNumber` 同坐标）：只把这些行的**行号**染成警示色，
   * 代码本身不动。
   */
  markedLines?: readonly number[];
  enableLineSelection?: boolean;
  enableGutterUtility?: boolean;
  labels?: Partial<CodeCommentLabels>;
  onSubmitCodeComment?: (params: {
    range: CodeCommentRange;
    selectedText: string;
    comment: string;
  }) => void;
  onDeleteCodeComment?: (commentId: string) => void;
  scrollContainerRef?: Ref<HTMLDivElement>;
  /** 不显示右上角的自动换行开关（例如外层已有同义控件时）。 */
  hideWrapToggle?: boolean;
  /**
   * 与自动换行开关同排渲染的额外控件（右上角浮动条）。
   * 用于「图形/源码」这类只有调用方才知道语义的切换，避免各自绝对定位打架。
   */
  headerActions?: ReactNode;
}
