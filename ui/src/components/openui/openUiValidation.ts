import { createParser, type LibraryJSONSchema } from '@openuidev/lang-core';

import spec from '@/generated/openui/spec.json';

const parser = createParser(spec.schema as unknown as LibraryJSONSchema, spec.root);

export interface OpenUiDiagnosticError {
  code?: string;
  message?: string;
  statementId?: string;
  path?: string;
}

/**
 * parser 诊断结果：isRenderableProgram 的展开形态。
 *
 * 意图修复链路（plans/038）靠它把"错在哪"交给模型 —— `incomplete` 的程序拿不到
 * statementId，但 REPLACE 块不需要 id，所以 incomplete 也照常送修。
 */
export interface OpenUiDiagnostic {
  renderable: boolean;
  incomplete: boolean;
  hasRoot: boolean;
  errors: OpenUiDiagnosticError[];
  /** 解析中断/游离的语句名（供排查，不进模型 prompt） */
  orphaned: string[];
  /** 悬空引用的语句名（供排查，不进模型 prompt） */
  unresolved: string[];
}

/**
 * 与 openui 的 isValid 同义：没有截断、没有未知组件/参数错误、没有悬空引用、root 存在。
 *
 * 只在流结束后判定 —— 流中间 `incomplete` / `unresolved` 是正常状态，那时候报错会满屏闪。
 */
export function isRenderableProgram(text: string): boolean {
  return diagnoseProgram(text).renderable;
}

/** 跑一次 parser 并保留完整诊断信息；parse 抛异常时按"结构崩坏"处理。 */
export function diagnoseProgram(text: string): OpenUiDiagnostic {
  if (!text.trim()) {
    return {
      renderable: false,
      incomplete: true,
      hasRoot: false,
      errors: [],
      orphaned: [],
      unresolved: []
    };
  }
  try {
    const result = parser.parse(text);
    const meta = result.meta;
    const errors: OpenUiDiagnosticError[] = (meta.errors ?? []).map((error) => {
      const raw = error as unknown as Record<string, unknown>;
      return {
        code: typeof raw.code === 'string' ? raw.code : undefined,
        message: typeof raw.message === 'string' ? raw.message : undefined,
        statementId: typeof raw.statementId === 'string' ? raw.statementId : undefined,
        path: typeof raw.path === 'string' ? raw.path : undefined,
      };
    });
    const renderable =
      !meta.incomplete &&
      errors.length === 0 &&
      meta.unresolved.length === 0 &&
      meta.orphaned.length === 0 &&
      result.root !== null;
    return {
      renderable,
      incomplete: Boolean(meta.incomplete),
      hasRoot: result.root !== null,
      errors,
      orphaned: [...meta.orphaned],
      unresolved: [...meta.unresolved],
    };
  } catch {
    return {
      renderable: false,
      incomplete: true,
      hasRoot: false,
      errors: [],
      orphaned: [],
      unresolved: []
    };
  }
}
