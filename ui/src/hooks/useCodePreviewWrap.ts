import { useCallback, useSyncExternalStore } from "react";
import {
  readCodePreviewWrap,
  setCodePreviewWrap,
  subscribeCodePreviewWrap,
  toggleCodePreviewWrap,
} from "@/lib/codePreviewWrap";

/**
 * 读取 / 切换代码预览的自动换行偏好。
 *
 * 返回 `[wrap, setWrap, toggleWrap]`。所有调用方共享同一份模块级 store，
 * 所以任意一处切换都会同步到其它代码查看器。
 */
export function useCodePreviewWrap(): [boolean, (next: boolean) => void, () => void] {
  const wrap = useSyncExternalStore(
    subscribeCodePreviewWrap,
    readCodePreviewWrap,
    readCodePreviewWrap
  );

  const setWrap = useCallback((next: boolean) => {
    setCodePreviewWrap(next);
  }, []);

  const toggleWrap = useCallback(() => {
    toggleCodePreviewWrap();
  }, []);

  return [wrap, setWrap, toggleWrap];
}

export default useCodePreviewWrap;
