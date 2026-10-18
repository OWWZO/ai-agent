import { createParser, type LibraryJSONSchema } from '@openuidev/lang-core';

import spec from '@/generated/openui/spec.json';

const parser = createParser(spec.schema as unknown as LibraryJSONSchema, spec.root);

/**
 * 与 openui 的 isValid 同义：没有截断、没有未知组件/参数错误、没有悬空引用、root 存在。
 *
 * 只在流结束后判定 —— 流中间 `incomplete` / `unresolved` 是正常状态，那时候报错会满屏闪。
 */
export function isRenderableProgram(text: string): boolean {
  if (!text.trim()) return false;
  try {
    const result = parser.parse(text);
    const meta = result.meta;
    return (
      !meta.incomplete &&
      meta.errors.length === 0 &&
      meta.unresolved.length === 0 &&
      meta.orphaned.length === 0 &&
      result.root !== null
    );
  } catch {
    return false;
  }
}
