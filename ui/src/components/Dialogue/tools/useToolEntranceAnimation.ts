import { useEffect, useState } from "react";

/**
 * 工具卡进场淡入去重（移植 ZCode `ToolCallBlocks.tsx:26-67`）。
 *
 * 流式对话中新出现的工具卡需要一次淡入，否则会和同一段文字的渐入节奏割裂。
 * 但历史消息重放、虚拟列表重挂、切任务再切回来都会让同一张卡重新挂载 ——
 * 没有去重的话整屏工具卡会一起闪一遍。
 *
 * 这里按 toolId 记录「已经展示过」的卡：记录动作延迟到 effect 里执行，
 * 避免 React 开发态的重挂把第一次动画误吞掉。表上限 800 条，超出按时间淘汰。
 */
const MAX_TOOL_ENTRANCE_ANIMATION_KEYS = 800;
const TOOL_ENTRANCE_ANIMATION_CLEANUP_MS = 1000;

const toolEntranceAnimationKeys = new Map<string, number>();

function pruneToolEntranceAnimationKeys() {
  if (toolEntranceAnimationKeys.size <= MAX_TOOL_ENTRANCE_ANIMATION_KEYS) {
    return;
  }

  const staleKeys = Array.from(toolEntranceAnimationKeys.entries())
    .sort(([, left], [, right]) => left - right)
    .slice(0, toolEntranceAnimationKeys.size - MAX_TOOL_ENTRANCE_ANIMATION_KEYS)
    .map(([key]) => key);

  for (const key of staleKeys) {
    toolEntranceAnimationKeys.delete(key);
  }
}

function normalizeToolEntranceAnimationKey(key: string) {
  const normalizedKey = key.trim();
  return normalizedKey.length > 0 ? normalizedKey : null;
}

function hasPlayedToolEntranceAnimation(key: string) {
  const normalizedKey = normalizeToolEntranceAnimationKey(key);
  return normalizedKey === null || toolEntranceAnimationKeys.has(normalizedKey);
}

function recordToolEntranceAnimation(key: string) {
  const normalizedKey = normalizeToolEntranceAnimationKey(key);
  if (normalizedKey === null) {
    return;
  }

  toolEntranceAnimationKeys.set(normalizedKey, Date.now());
  pruneToolEntranceAnimationKeys();
}

function canPlayToolEntranceAnimation(key: string, active: boolean) {
  return active && !hasPlayedToolEntranceAnimation(key);
}

/** 仅测试使用：清空去重表。 */
export function resetToolEntranceAnimationKeys(): void {
  toolEntranceAnimationKeys.clear();
}

export function useToolEntranceAnimation(key: string, active: boolean): boolean {
  const [shouldPlay, setShouldPlay] = useState(() => canPlayToolEntranceAnimation(key, active));

  useEffect(() => {
    if (!active || shouldPlay) {
      return;
    }
    if (canPlayToolEntranceAnimation(key, active)) {
      setShouldPlay(true);
    }
  }, [active, key, shouldPlay]);

  useEffect(() => {
    if (!shouldPlay) {
      return;
    }

    const markAnimatedTimer = window.setTimeout(() => {
      recordToolEntranceAnimation(key);
    }, 0);
    const cleanupTimer = window.setTimeout(() => {
      setShouldPlay(false);
    }, TOOL_ENTRANCE_ANIMATION_CLEANUP_MS);

    return () => {
      window.clearTimeout(markAnimatedTimer);
      window.clearTimeout(cleanupTimer);
    };
  }, [key, shouldPlay]);

  return shouldPlay;
}
