import { useCallback, useEffect, useRef, useState, type RefObject } from "react";
import { dispatchWebElementAddToChat, isWebElementPayload } from "@/lib/promptContexts";
import { executeScriptInIframe } from "@/lib/webElementPicker/frameExecutor";
import {
  buildCancelWebElementPickerScript,
  buildWebElementPickerScript,
  type WebElementPickerScriptResult,
} from "@/lib/webElementPicker/pickerScript";

function isPickerScriptResult(result: unknown): result is WebElementPickerScriptResult {
  if (typeof result !== "object" || result === null) {
    return false;
  }
  const candidate = result as WebElementPickerScriptResult;
  return (
    candidate.status === "cancelled" ||
    (candidate.status === "selected" &&
      typeof candidate.element === "object" &&
      candidate.element !== null)
  );
}

export interface UseHtmlElementPickerOptions {
  /** HTML 预览的 iframe（同源才可注入）。 */
  iframeRef: RefObject<HTMLIFrameElement | null>;
  /** 关掉时（无 URL / 流式中）不启动。 */
  enabled?: boolean;
}

export interface UseHtmlElementPickerResult {
  isPicking: boolean;
  error: string | null;
  startPicking: () => Promise<void>;
  cancelPicking: () => Promise<void>;
  togglePicking: () => Promise<void>;
}

/**
 * HTML 预览「选择网页元素加入聊天」。
 *
 * 与 ZCode 的差异：ZCode 的 `useWebElementPicker` 把「怎么执行脚本」抽象成注入的
 * `executeJs` 回调（Electron webview）；这里同样保留这一接缝，但实现换成同源 iframe 注入。
 */
export function useHtmlElementPicker({
  iframeRef,
  enabled = true,
}: UseHtmlElementPickerOptions): UseHtmlElementPickerResult {
  const [isPicking, setIsPicking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const activeRunRef = useRef(0);

  const executeJs = useCallback(
    (script: string) => executeScriptInIframe(iframeRef.current, script),
    [iframeRef]
  );

  const cancelPicking = useCallback(async () => {
    activeRunRef.current += 1;
    setIsPicking(false);
    try {
      await executeJs(buildCancelWebElementPickerScript());
    } catch {
      // 预览已卸载 / 跨源：取消失败无需打扰用户。
    }
  }, [executeJs]);

  const startPicking = useCallback(async () => {
    if (!enabled) return;

    const runId = activeRunRef.current + 1;
    activeRunRef.current = runId;
    setError(null);
    setIsPicking(true);

    try {
      const result = await executeJs(buildWebElementPickerScript());
      if (activeRunRef.current !== runId) return;
      if (!isPickerScriptResult(result) || result.status === "cancelled") return;

      const payload = result.element;
      if (!isWebElementPayload(payload)) return;
      dispatchWebElementAddToChat(payload);
    } catch (err) {
      if (activeRunRef.current === runId) {
        setError(err instanceof Error ? err.message : String(err));
      }
    } finally {
      if (activeRunRef.current === runId) {
        setIsPicking(false);
      }
    }
  }, [enabled, executeJs]);

  const togglePicking = useCallback(async () => {
    if (isPicking) {
      await cancelPicking();
      return;
    }
    await startPicking();
  }, [cancelPicking, isPicking, startPicking]);

  // 焦点还在外层工具栏时，Esc 由这里兜底取消；焦点在 iframe 内时由注入脚本自己处理。
  useEffect(() => {
    if (!isPicking) return;
    const handleWindowKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      void cancelPicking();
    };
    window.addEventListener("keydown", handleWindowKeyDown, true);
    return () => window.removeEventListener("keydown", handleWindowKeyDown, true);
  }, [cancelPicking, isPicking]);

  useEffect(() => {
    return () => {
      void cancelPicking();
    };
  }, [cancelPicking]);

  return { isPicking, error, startPicking, cancelPicking, togglePicking };
}
