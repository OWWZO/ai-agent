import {
  Suspense,
  lazy,
  memo,
  useMemo,
  type ComponentType,
  type CSSProperties,
} from "react";
import type { FileDiffOptions, PatchDiffProps } from "@pierre/diffs/react";
import { useIsDarkTheme } from "@/hooks/useIsDarkTheme";
import { useCodePreviewWrap } from "@/hooks/useCodePreviewWrap";
import {
  DEFAULT_CODE_PREVIEW_SETTINGS,
  resolveCodePreviewTheme,
} from "@/lib/codePreviewSettings";
import {
  getLightweightDiffLineParts,
  getPlainTextPatchPreviewLines,
} from "@/lib/patchPreview";
import { cn } from "@/lib/utils";
import { buildDiffViewerStyle } from "./utils";
import Loading from "../Loading";

/**
 * `@pierre/diffs` 的 `PatchDiff` 懒加载：自带 shiki + oniguruma WASM，
 * 必须独立分包，只在真的打开 diff 预览时才拉取（与 `CodeViewer` 同一套约定）。
 */
const LazyPatchDiff = lazy(async () => {
  const mod = await import("@pierre/diffs/react");
  return {
    default: mod.PatchDiff as unknown as ComponentType<
      PatchDiffProps<undefined, undefined>
    >,
  };
});

/**
 * 大 patch 的安全阈值（与 ZCode `patchDiffPreview.ts` 同值）。
 *
 * `PatchDiff` 会同步解析 + 跑 Shiki 高亮；千行级 patch 会把主线程卡住，
 * 所以超过阈值时退化成纯文本，宁可丢高亮也不能让面板卡死。
 */
const MAX_PATCH_SAFE_LINE_COUNT = 1_200;
const MAX_PATCH_SAFE_CHAR_COUNT = 180_000;

const LOADING_CLASS = "mr-32";

type PatchViewerStyle = CSSProperties & {
  "--diffs-bg"?: string;
  "--diffs-light-bg"?: string;
  "--diffs-dark-bg"?: string;
  "--diffs-font-family"?: string;
  "--diffs-font-size"?: string;
};

type PatchViewerProps = {
  /** unified diff 文本（后端 `workspace_edit` 的 `diff` 字段） */
  patch: string;
  className?: string;
};

/**
 * 右侧 patch 预览（对齐 ZCode `components/ui/diff-viewer.tsx` 的 PatchDiff 分支）。
 *
 * 与内联的 `LightweightDiffPreview` 是**两套东西**，刻意不合并：
 * - 内联要的是「一眼看清改了哪几行」，行号用渲染序号（与 ZCode 内联一致）；
 * - 右侧是完整 diff 查看器，展示的是 hunk 头里的**文件真实行号**与 3 行上下文。
 */
export const PatchViewer = memo(function PatchViewer({ patch, className }: PatchViewerProps) {
  const isDark = useIsDarkTheme();
  const settings = DEFAULT_CODE_PREVIEW_SETTINGS;
  const theme = resolveCodePreviewTheme(isDark, settings);
  const [globalWrap] = useCodePreviewWrap();

  const oversized = useMemo(() => {
    if (patch.length > MAX_PATCH_SAFE_CHAR_COUNT) return true;
    let lines = 1;
    for (let index = 0; index < patch.length; index += 1) {
      if (patch.charCodeAt(index) === 10) lines += 1;
    }
    return lines > MAX_PATCH_SAFE_LINE_COUNT;
  }, [patch]);

  const viewerStyle = useMemo<PatchViewerStyle>(
    () => ({
      // 主题适配与 CodeViewer 共用，见 buildDiffViewerStyle 的说明
      ...buildDiffViewerStyle({
        isDark,
        fontSizePx: settings.fontSizePx,
      }),
    }),
    [isDark, settings.fontSizePx]
  );

  const options = useMemo<FileDiffOptions<undefined, undefined>>(
    () => ({
      diffStyle: "unified",
      diffIndicators: "bars",
      disableFileHeader: true,
      // patch 只有 hunk 内的局部上下文，没有完整 before/after；
      // 用 simple 避免展示一堆点不开的 "unmodified lines" 占位。
      hunkSeparators: "simple",
      overflow: globalWrap ? "wrap" : "scroll",
      theme,
      // shiki 默认 JS 正则引擎会把 TextMate 语法编译成巨型 RegExp 并永久持有，
      // 长时间运行会占满 V8 code space（ZCode 已记录该 OOM）。
      preferredHighlighter: "shiki-wasm" as const,
    }),
    [globalWrap, theme]
  );

  if (oversized) {
    return <PatchPlainTextFallback patch={patch} wrap={globalWrap} className={className} />;
  }

  return (
    <div
      className={cn(
        "relative h-full w-full overflow-hidden bg-[var(--chat-surface)]",
        className
      )}
      data-patch-viewer="diffs"
      style={viewerStyle}
    >
      <div className="h-full w-full overflow-auto">
        <Suspense
          fallback={
            <div className="flex h-full min-h-[200px] items-center justify-center">
              <Loading className={LOADING_CLASS} />
            </div>
          }
        >
          <LazyPatchDiff
            patch={patch}
            options={options}
            className="min-h-full w-full"
            style={viewerStyle}
          />
        </Suspense>
      </div>
    </div>
  );
});

/** 超大 patch 的降级：剥协议头后的纯文本行，不做 Shiki 高亮。 */
const PatchPlainTextFallback = memo(function PatchPlainTextFallback({
  patch,
  wrap,
  className,
}: {
  patch: string;
  wrap: boolean;
  className?: string;
}) {
  const lines = useMemo(() => getPlainTextPatchPreviewLines(patch), [patch]);

  return (
    <div
      className={cn(
        "h-full w-full overflow-auto bg-[var(--chat-surface)]",
        className
      )}
      data-patch-viewer="plain"
    >
      <div className="px-3 py-2 text-ui-xs text-foreground-subtle">
        patch 过大，已停用 diff 高亮
      </div>
      <pre
        className={cn(
          "px-3 pb-3 font-mono text-ui-xs leading-[1.7]",
          wrap ? "whitespace-pre-wrap break-words" : "w-max min-w-full whitespace-pre"
        )}
      >
        <code>
          {lines.map((line, index) => {
            const parts = getLightweightDiffLineParts(line);
            return (
              <span
                key={index}
                className={cn(
                  "kimi-diff-line",
                  parts.kind === "added" && "kimi-diff-add",
                  parts.kind === "removed" && "kimi-diff-del"
                )}
              >
                {parts.marker ? <span className="kimi-diff-sign">{parts.marker}</span> : null}
                <span className="kimi-diff-text">{parts.content}</span>
              </span>
            );
          })}
        </code>
      </pre>
    </div>
  );
});

export default PatchViewer;
