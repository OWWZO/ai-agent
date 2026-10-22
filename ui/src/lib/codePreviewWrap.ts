import { DEFAULT_CODE_PREVIEW_SETTINGS } from "./codePreviewSettings";

/**
 * 代码预览「自动换行」全局偏好。
 *
 * 为什么是模块级 store 而不是 React state：
 * 同一个页面里可能同时挂着多个代码查看器（右侧文件预览 + 动态产物面板 +
 * markdown/svg 的源码模式），用户只在一个地方点开关，其余都要立刻跟随。
 * 走 `useSyncExternalStore` 才能做到「一处点击、全体同步」。
 *
 * 与 ZCode 的差异（有意为之）：ZCode 是「全局设置 + `PreviewPane` 局部覆盖」两层，
 * 本项目没有设置页，收敛成**一层全局偏好**，行为更可预期。
 *
 * 默认值取 `DEFAULT_CODE_PREVIEW_SETTINGS.wrapLongLines`（当前为 `true`，
 * 即默认开启自动换行），保持「默认值只有一个出口」。
 */

const STORAGE_KEY = "cute:code-preview-wrap";

const DEFAULT_WRAP = DEFAULT_CODE_PREVIEW_SETTINGS.wrapLongLines;

const listeners = new Set<() => void>();

function canUseStorage(): boolean {
  try {
    return typeof window !== "undefined" && Boolean(window.localStorage);
  } catch {
    // 某些沙箱里访问 localStorage 本身就会抛 SecurityError
    return false;
  }
}

function readStoredWrap(): boolean | null {
  if (!canUseStorage()) return null;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw === "true") return true;
    if (raw === "false") return false;
    return null;
  } catch {
    return null;
  }
}

let currentWrap: boolean = readStoredWrap() ?? DEFAULT_WRAP;

/** 读取当前偏好（`useSyncExternalStore` 的 getSnapshot）。 */
export function readCodePreviewWrap(): boolean {
  return currentWrap;
}

/** 写入偏好并广播；存储失败（隐私模式 / 配额）只影响持久化，不阻塞交互。 */
export function setCodePreviewWrap(next: boolean): void {
  if (next === currentWrap) return;
  currentWrap = next;
  if (canUseStorage()) {
    try {
      window.localStorage.setItem(STORAGE_KEY, next ? "true" : "false");
    } catch {
      // 忽略：偏好保留在内存中即可
    }
  }
  for (const listener of listeners) listener();
}

/** 取反并返回新值。 */
export function toggleCodePreviewWrap(): boolean {
  const next = !currentWrap;
  setCodePreviewWrap(next);
  return next;
}

/** 订阅偏好变化，返回取消订阅函数。 */
export function subscribeCodePreviewWrap(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
