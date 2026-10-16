import { createContext, useContext } from "react";

/**
 * 文件预览头部的「插槽」。
 *
 * 有些操作**只有某个 renderer 自己才做得了** —— 典型是 HTML 预览的「选择网页元素」：
 * picker 要挂到 iframe 内部的 DOM 上，ref 与逻辑都在 `HTMLRenderer` 里。但按钮不该
 * 浮在预览右上角：它和头部那排「预览 / 源码 / 复制链接 / 刷新 / 下载」是同一类东西，
 * 飘在内容区上既显得重复，也挡内容。
 *
 * 于是用一个最小契约打通：
 * - 工作区（`ActionView`）在文件头部放一个空节点，并通过这个 Context 暴露；
 * - renderer 拿到节点后用 `createPortal` 把按钮**渲染进那一行**。
 *
 * 按钮的 DOM 在头部（于是和同排按钮共享布局与样式），状态与逻辑仍留在 renderer 内部
 * —— 不需要把 iframe ref 往上提。
 *
 * 两种「没有插槽」的情形要分清，否则按钮会先飘一下再跳进头部：
 * - **没有 Provider**（`useFilePreviewHeaderSlot()` 返回 `null`，例如对话区里的文件卡）
 *   → renderer 回落到自带的浮层按钮；
 * - **有 Provider 但节点还没挂上**（`{ node: null }`）→ 这一帧不渲染，等节点就绪。
 */
export type FilePreviewHeaderSlot = {
  /** 头部那一行的插槽节点；`null` 表示尚未挂载 */
  node: HTMLElement | null;
};

export const FilePreviewHeaderSlotContext =
  createContext<FilePreviewHeaderSlot | null>(null);

/** 取当前文件头部的插槽；不在工作区文件预览里时返回 `null`。 */
export function useFilePreviewHeaderSlot(): FilePreviewHeaderSlot | null {
  return useContext(FilePreviewHeaderSlotContext);
}
