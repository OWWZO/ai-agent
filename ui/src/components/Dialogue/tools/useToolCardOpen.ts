import { useCallback, useEffect, useRef, useState } from "react";
import { getToolCardOpen, setToolCardOpen } from "./toolCardOpenState";

type SetOpen = (next: boolean | ((previous: boolean) => boolean)) => void;

/**
 * 工具卡开合状态：把折叠态从组件内部 `useState` 提升到模块级持久表。
 *
 * 对齐 ZCode `ToolLayout.tsx:107-111` 的状态所有权 —— 那边由壳组件持有 `isOpen`，
 * 初值从 `toolLayoutOpenState` 读取；Reactor 的 renderer 需要根据 open 决定摘要行
 * 显示什么，所以状态仍留在 renderer，只把「持久化」这一段抽出来复用。
 *
 * ⚠️ ZCode 的 `autoOpen` / `autoCollapseOnComplete` 是给 **agent renderer** 用的
 * （`renderers/agent.tsx:382`）：子代理运行中自动展开、完成时自动收起。
 * Reactor 的子代理子工具不在卡内渲染（由 `Timeline.tsx` 的 `SubAgentTimelineCard`
 * 独立承载，且刻意「完成后保持展开」，见该文件注释），因此这两个边沿在 Reactor
 * 没有落点 —— 不要为了「凑齐三件套」硬塞进来。
 *
 * `key` 为空串时视为「无稳定身份」，退化成普通 `useState`，避免所有匿名卡共用一条状态。
 */
export function useToolCardOpen(key: string, defaultOpen: boolean): [boolean, SetOpen] {
  const persistable = key.trim().length > 0;

  const [open, setOpenState] = useState<boolean>(() =>
    persistable ? (getToolCardOpen(key) ?? defaultOpen) : defaultOpen
  );
  const openRef = useRef(open);
  openRef.current = open;

  // 组件实例被复用到另一张卡（列表 key 变化 / 子代理列表重排）时，
  // 切到新 key 自己的持久态；defaultOpen 只作为「没有持久态」时的种子。
  useEffect(() => {
    setOpenState(persistable ? (getToolCardOpen(key) ?? defaultOpen) : defaultOpen);
    // defaultOpen 故意不进依赖：流式期间它会在 true/false 间抖动，
    // 进了依赖就会把用户的手动收起反复顶掉。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, persistable]);

  return [
    open,
    useCallback<SetOpen>(
      (next) => {
        const value =
          typeof next === "function"
            ? (next as (previous: boolean) => boolean)(openRef.current)
            : next;
        openRef.current = value;
        if (persistable) {
          setToolCardOpen(key, value);
        }
        setOpenState(value);
      },
      [key, persistable]
    ),
  ];
}
