import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionEvent } from '@openuidev/react-lang';

import MarkdownRenderer from '@/components/ActionPanel/MarkdownRenderer';
import { normalizeOpenUiProgram } from '@/lib/openui/normalize';
import { applyIntents, type ReplaceBlock } from '@/lib/openui/repairIntent';
import { splitOpenUiSegments } from '@/lib/openui/segments';
import {
  computeProgramHash,
  fetchOpenUiRepairIntents,
  repairOpenUiProgram,
  repairOpenUiProgramWithIntent,
  saveOpenUiRepairIntents,
} from '@/services/openUiRepair';
import { dispatchOpenUiAction } from './openUiActionBridge';
import { OpenUiRender } from './OpenUiRender';
import { diagnoseProgram, isRenderableProgram } from './openUiValidation';

export interface OpenUiMarkdownProps {
  content: string;
  streaming?: boolean;
  artifactFiles?: CHAT.TFile[];
  className?: string;
  onAction?: (event: ActionEvent) => void;
  /** 意图落库/重放用；缺省则跳过持久化，修复只在内存生效 */
  sessionId?: string;
  /** 用 requestId 充当消息身份（ChatItem 上没有独立 messageId） */
  messageId?: string;
}

/**
 * assistant 正文渲染：按 `]]>openui:` marker 分段，markdown 段交给原来的渲染器，
 * 程序段交给 OpenUI Renderer。
 *
 * 程序段在流结束（closed）后校验一次，失败后按 plans/038 v4 分层处置：
 * 本地纠偏（normalize）→ 意图修复（SEARCH/REPLACE 块，重试 ≤2 次）→ 整段 Autofix
 * （intent 未启用时的回退）→ 降级显示原文。宁可让用户看到没渲染的 DSL，也不静默吞掉回复。
 */
export function OpenUiMarkdown({
  content,
  streaming = false,
  artifactFiles,
  className,
  onAction,
  sessionId,
  messageId,
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
            sessionId={sessionId}
            messageId={messageId}
          />
        );
      })}
    </>
  );
}

/**
 * 修好的程序按原文缓存：滚动、切会话、重挂载都不该再打一次修复（那是按次计费的）。
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

/** 意图修复的最大模型调用次数：首次 + 重试（plans/038 §1.1）。 */
const INTENT_MAX_ATTEMPTS = 3;

function OpenUiSegment({
  text,
  streaming,
  onAction,
  sessionId,
  messageId,
}: {
  text: string;
  streaming: boolean;
  onAction?: (event: ActionEvent) => void;
  sessionId?: string;
  messageId?: string;
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
  // 整段被判 incomplete。这是纯形态错误，字符串替换就能掰正，不必花钱打后端。
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

  const adoptRepair = useCallback(
    (repairedProgram: string, blocks?: ReplaceBlock[], model?: string) => {
      rememberRepair(text, repairedProgram);
      setRepaired(repairedProgram);
      setState('idle');
      // 只存通过验收的意图；落库失败不影响渲染（刷新后大不了再修一次）。
      if (sessionId && messageId && blocks?.length) {
        void computeProgramHash(program).then((hash) =>
          saveOpenUiRepairIntents({
            sessionId,
            messageId,
            programHash: hash
          }, blocks, model)
        );
      }
    },
    [text, sessionId, messageId, program]
  );

  /**
   * 意图修复轮：把程序 + 诊断上送，拿回 SEARCH/REPLACE 块，前端执行 + 全量 parse 验收。
   * 验收不过带失败原因重试（上限 INTENT_MAX_ATTEMPTS）。
   * 返回 'repaired'（成功）/ 'unsupported'（后端未启用，走整段回退）/ 'exhausted'（重试耗尽）。
   */
  const runIntentRepair = useCallback(
    async (signal: AbortSignal): Promise<'repaired' | 'unsupported' | 'exhausted'> => {
      const diagnosis = diagnoseProgram(program);
      let feedback: NonNullable<Parameters<typeof repairOpenUiProgramWithIntent>[1]>['feedback'];
      for (let attempt = 0; attempt < INTENT_MAX_ATTEMPTS; attempt++) {
        if (signal.aborted) return 'exhausted';
        const response = await repairOpenUiProgramWithIntent(program, {
          diagnosis: {
            incomplete: diagnosis.incomplete,
            errors: diagnosis.errors
          },
          feedback,
          signal,
        });
        if (signal.aborted) return 'exhausted';
        if (response.status === 'disabled') return 'unsupported';
        const blocks = response.status === 'intent_ok' ? (response.blocks ?? []) : [];
        if (blocks.length === 0) {
          feedback = {
            invalidBlocks: [],
            parseErrors: diagnosis.errors.slice(0, 5)
          };
          continue;
        }
        const { result, failures } = applyIntents(program, blocks);
        if (result !== null && isRenderableProgram(result)) {
          adoptRepair(result, blocks, response.model);
          return 'repaired';
        }
        feedback = {
          invalidBlocks: failures.map((failure) => ({
            index: failure.index,
            searchText: blocks[failure.index]?.searchText?.slice(0, 200) ?? '',
            reason: failure.reason,
          })),
          parseErrors: diagnoseProgram(result ?? program).errors.slice(0, 5),
        };
      }
      return 'exhausted';
    },
    [program, adoptRepair]
  );

  useEffect(() => {
    // 流还没结束不判定；已经修过 / 修过没成功都不再重复调用。
    if (streaming || !invalid || attemptedRef.current) return;
    attemptedRef.current = true;
    const controller = new AbortController();
    setState('running');

    const run = async () => {
      // ① 刷新/重放：库里已有通过验收的意图就不必再调模型。hash 不符会被 applyIntents
      //    的 SEARCH 校验自然挡住，这里先用 hash 做快速短路。
      if (sessionId && messageId) {
        const hash = await computeProgramHash(program);
        if (!controller.signal.aborted) {
          const stored = await fetchOpenUiRepairIntents(sessionId, messageId, hash);
          if (controller.signal.aborted) return;
          if (stored && stored.length > 0) {
            const { result } = applyIntents(
              program,
              stored.map(({ searchText, replaceText }) => ({
                searchText,
                replaceText
              }))
            );
            if (result !== null && isRenderableProgram(result)) {
              adoptRepair(result);
              return;
            }
          }
        }
      }
      // ② 意图修复（v4 主链路）。
      const intentOutcome = await runIntentRepair(controller.signal);
      if (controller.signal.aborted) return;
      if (intentOutcome === 'repaired') return;
      // ③ 后端没启用意图模式（intent-enabled=false）→ 回退整段 Autofix，行为与旧版一致。
      if (intentOutcome === 'unsupported') {
        const legacy = await repairOpenUiProgram(program, { signal: controller.signal });
        if (controller.signal.aborted) return;
        const content = legacy.content;
        if (
          (legacy.status === 'fixed' || legacy.status === 'already_valid') &&
          content &&
          isRenderableProgram(content)
        ) {
          adoptRepair(content);
          return;
        }
      }
      // ④ 重试耗尽 / 整段也修不好 → 降级显示原文。
      setState('failed');
    };

    void run();
    return () => controller.abort();
  }, [text, program, streaming, invalid, sessionId, messageId, runIntentRepair, adoptRepair]);

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
