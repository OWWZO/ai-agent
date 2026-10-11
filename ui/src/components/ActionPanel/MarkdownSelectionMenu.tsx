import { useCallback, useLayoutEffect, useRef, type RefObject } from "react";
import { createPortal } from "react-dom";
import { useTextSelection } from "@/hooks/useTextSelection";
import {
  CONVERSATION_SELECTION_MAX_TEXT_LENGTH,
  dispatchConversationSelectionAddToChat,
} from "@/lib/promptContexts";

interface MarkdownSelection {
  text: string;
  top: number;
  bottom: number;
  center: number;
}

function SelectionMenuPortal({
  selection,
  tooLong,
  onAdd,
}: {
  selection: MarkdownSelection;
  tooLong: boolean;
  onAdd: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const menu = ref.current;
    if (!menu) return;
    const position = () => {
      const rect = menu.getBoundingClientRect();
      menu.style.left = `${Math.max(
        12,
        Math.min(window.innerWidth - rect.width - 12, selection.center - rect.width / 2)
      )}px`;
      const preferredTop =
        selection.top - rect.height - 8 >= 12 ? selection.top - rect.height - 8 : selection.bottom + 8;
      menu.style.top = `${Math.max(
        12,
        Math.min(window.innerHeight - rect.height - 12, preferredTop)
      )}px`;
    };
    position();
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(position);
    observer?.observe(menu);
    return () => observer?.disconnect();
  }, [selection]);

  return createPortal(
    <div
      ref={ref}
      data-markdown-selection-tooltip="true"
      style={{ width: "max-content", left: 12, top: 12 }}
      className="fixed z-50 flex overflow-hidden rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] text-ui-sm text-[var(--chat-text)] shadow-md"
      onPointerDown={(event) => event.preventDefault()}
      onMouseDown={(event) => event.preventDefault()}
    >
      {tooLong ? (
        <div className="px-2.5 py-1.5 text-[var(--color-warning)]">
          选中的内容过长，请缩短后再试
        </div>
      ) : (
        <button
          type="button"
          className="min-w-0 px-2.5 py-1.5 hover:bg-[var(--chat-interactive-hover)]"
          onClick={onAdd}
        >
          加入聊天
        </button>
      )}
    </div>,
    document.body
  );
}

/**
 * Markdown 预览里的「选中正文 → 加入聊天」（移植自 ZCode `MarkdownSelectionTooltip`）。
 *
 * 挂在 `MarkdownFileRenderer` 的渲染分支上：选区落在正文容器内时弹出菜单，
 * 点击后走第三条链路（`# userselect:` JSON 块）。
 */
export function MarkdownSelectionMenu({
  rootRef,
  sourcePath,
}: {
  rootRef: RefObject<HTMLElement | null>;
  sourcePath?: string;
}) {
  const inspect = useCallback((): MarkdownSelection | null => {
    const root = rootRef.current;
    const selection = window.getSelection();
    if (!root || !selection || selection.isCollapsed || selection.rangeCount !== 1) return null;
    const range = selection.getRangeAt(0);
    if (!root.contains(range.startContainer) || !root.contains(range.endContainer)) return null;
    const text = selection.toString().trim();
    if (!text) return null;
    const rect = range.getBoundingClientRect();
    if (!rect.width && !rect.height) return null;
    return { text, top: rect.top, bottom: rect.bottom, center: rect.left + rect.width / 2 };
  }, [rootRef]);

  const { state, close } = useTextSelection({
    rootRef,
    enabled: true,
    inspect,
    observeSelectionChange: true,
  });

  if (!state) return null;

  const tooLong = state.text.length > CONVERSATION_SELECTION_MAX_TEXT_LENGTH;

  return (
    <SelectionMenuPortal
      selection={state}
      tooLong={tooLong}
      onAdd={() => {
        const path = sourcePath?.trim();
        dispatchConversationSelectionAddToChat({
          text: state.text,
          ...(path ? { path } : {}),
        });
        close();
      }}
    />
  );
}

export default MarkdownSelectionMenu;
