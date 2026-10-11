import { useCallback, useEffect, useRef, useState, type RefObject } from "react";

/**
 * 统一鼠标、键盘与触控选区的监听（移植自 ZCode `hooks/useTextSelection.ts`）。
 *
 * 滚动 / resize / Esc 都会关闭浮层，避免选区位置失效后菜单还挂在半空。
 */
export function useTextSelection<T>({
  rootRef,
  enabled,
  inspect,
  observeSelectionChange = false,
}: {
  rootRef: RefObject<HTMLElement | null>;
  enabled: boolean;
  inspect: () => T | null;
  observeSelectionChange?: boolean;
}): { state: T | null; close: () => void } {
  const [snapshot, setSnapshot] = useState<T | null>(null);
  const frameRef = useRef(0);
  const inspectRef = useRef(inspect);
  inspectRef.current = inspect;

  const close = useCallback(() => {
    window.cancelAnimationFrame(frameRef.current);
    setSnapshot(null);
  }, []);

  useEffect(() => {
    close();
    const root = rootRef.current;
    if (!root || !enabled) return;

    const schedule = () => {
      window.cancelAnimationFrame(frameRef.current);
      frameRef.current = window.requestAnimationFrame(() => {
        setSnapshot(inspectRef.current());
      });
    };
    const onKey = (event: KeyboardEvent) => (event.key === "Escape" ? close() : schedule());
    const onSelection = () => {
      if (window.getSelection()?.isCollapsed) close();
      else if (observeSelectionChange) schedule();
    };

    root.addEventListener("mouseup", schedule);
    root.addEventListener("touchend", schedule, { passive: true });
    document.addEventListener("keyup", onKey);
    document.addEventListener("selectionchange", onSelection);
    root.addEventListener("scroll", close, { passive: true, capture: true });
    window.addEventListener("resize", close);

    return () => {
      window.cancelAnimationFrame(frameRef.current);
      root.removeEventListener("mouseup", schedule);
      root.removeEventListener("touchend", schedule);
      document.removeEventListener("keyup", onKey);
      document.removeEventListener("selectionchange", onSelection);
      root.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [close, enabled, observeSelectionChange, rootRef]);

  return { state: enabled ? snapshot : null, close };
}
