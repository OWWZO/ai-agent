import { useEffect, useMemo, useRef, useState } from 'react';
import type { ActionEvent } from '@openuidev/react-lang';

import MarkdownRenderer from '@/components/ActionPanel/MarkdownRenderer';
import { normalizeOpenUiProgram } from '@/lib/openui/normalize';
import { splitOpenUiSegments } from '@/lib/openui/segments';
import { repairOpenUiProgram } from '@/services/openUiRepair';
import { dispatchOpenUiAction } from './openUiActionBridge';
import { OpenUiRender } from './OpenUiRender';
import { isRenderableProgram } from './openUiValidation';

export interface OpenUiMarkdownProps {
  content: string;
  streaming?: boolean;
  artifactFiles?: CHAT.TFile[];
  className?: string;
  onAction?: (event: ActionEvent) => void;
}

/**
 * assistant 正文渲染：按 `]]>openui:` marker 分段，markdown 段交给原来的渲染器，
 * 程序段交给 OpenUI Renderer。
 *
 * 程序段在流结束（closed）后校验一次；不通过就交给后端 Autofix 修一次，还不行才降级
 * 显示原文 —— 宁可让用户看到一段没渲染的 DSL，也不要静默吞掉整段回复。
 */
export function OpenUiMarkdown({
  content,
  streaming = false,
  artifactFiles,
  className,
  onAction,
}: OpenUiMarkdownProps) {
  const segments = useMemo(() => splitOpenUiSegments(content), [content]);

  return (
    <>
      {segments.map((segment, index) => {
        if (segment.type === 'markdown') {
          return (
            <MarkdownRenderer
              key={`md-${index}`}
              markDownContent={segment.text}
              isStreaming={streaming && index === segments.length - 1}
              artifactFiles={artifactFiles}
              className={className}
            />
          );
        }
        const segmentStreaming = streaming && !segment.closed;
        return (
          <OpenUiSegment
            key={`ui-${index}`}
            text={segment.text}
            streaming={segmentStreaming}
            onAction={onAction ?? dispatchOpenUiAction}
          />
        );
      })}
    </>
  );
}

/**
 * 修好的程序按原文缓存：滚动、切会话、重挂载都不该再打一次 Autofix（那是按次计费的）。
 * 只留最近 50 条，避免长会话里无限涨。
 */
const repairedCache = new Map<string, string>();
const REPAIRED_CACHE_LIMIT = 50;

function rememberRepair(source: string, repaired: string) {
  if (repairedCache.size >= REPAIRED_CACHE_LIMIT) {
    const oldest = repairedCache.keys().next().value;
    if (oldest !== undefined) repairedCache.delete(oldest);
  }
  repairedCache.set(source, repaired);
}

type RepairState = 'idle' | 'running' | 'failed';

function OpenUiSegment({
  text,
  streaming,
  onAction,
}: {
  text: string;
  streaming: boolean;
  onAction?: (event: ActionEvent) => void;
}) {
  const [repaired, setRepaired] = useState<string | null>(() => repairedCache.get(text) ?? null);
  const [state, setState] = useState<RepairState>('idle');
  const attemptedRef = useRef(false);

  // 程序文本变了（同一段落在流式中不断增长）就把修复状态归零，避免把上一次的结果套到新程序上。
  useEffect(() => {
    setRepaired(repairedCache.get(text) ?? null);
    setState('idle');
    attemptedRef.current = false;
  }, [text]);

  // 模型常把 action 写成 `@//set/(` 这类路径形态，parser 会在那一行直接中断，
  // 整段被判 incomplete。这是纯形态错误，字符串替换就能掰正，不必花钱打 Autofix。
  // 只有「原文不可渲染 + 纠偏后可渲染」才采用，所以永远不会把本就能渲染的程序改坏。
  const normalized = useMemo(() => normalizeOpenUiProgram(text), [text]);
  const program = useMemo(
    () => (normalized === null || isRenderableProgram(text) ? text : normalized),
    [text, normalized]
  );

  const invalid = useMemo(
    () => (streaming ? false : !isRenderableProgram(repaired ?? program)),
    [program, repaired, streaming]
  );

  useEffect(() => {
    // 流还没结束不判定；已经修过 / 修过没成功都不再重复调用。
    if (streaming || !invalid || attemptedRef.current) return;
    attemptedRef.current = true;
    const controller = new AbortController();
    setState('running');
    // 送纠偏后的程序：Autofix 少修一处，且来回的文本更接近正确形态。
    void repairOpenUiProgram(program, { signal: controller.signal }).then((result) => {
      if (controller.signal.aborted) return;
      const content = result.content;
      if ((result.status === 'fixed' || result.status === 'already_valid') && content && isRenderableProgram(content)) {
        rememberRepair(text, content);
        setRepaired(content);
        setState('idle');
        return;
      }
      setState('failed');
    });
    return () => controller.abort();
  }, [text, program, streaming, invalid]);

  if (invalid) {
    return (
      <div className="my-3 rounded-lg border border-amber-300 bg-amber-50 p-3">
        <div className="mb-2 flex items-center gap-2 text-xs font-medium text-amber-700">
          <span>界面渲染失败，已显示原始内容</span>
          {state === 'running' ? <span className="text-amber-600">· 正在自动修复…</span> : null}
        </div>
        <pre className="overflow-auto whitespace-pre-wrap break-words font-mono text-xs leading-relaxed text-neutral-700">
          {repaired ?? program}
        </pre>
      </div>
    );
  }

  return (
    <div className="openui-block my-3 w-full">
      <OpenUiRender text={repaired ?? program} streaming={streaming} onAction={onAction} />
    </div>
  );
}

export default OpenUiMarkdown;
