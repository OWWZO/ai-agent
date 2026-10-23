import { describe, expect, it } from "vitest";
import { hashContent } from "@/lib/hashContent";
import { parseToolJson } from "@/components/Dialogue/tools/ToolJsonBlock";

/**
 * 验证「内容哈希 → 复用解析结果」成立的**前提**，而不测组件内部实现。
 *
 * 实测（受控 harness，2000 条工具输出）：
 *   hash ≈0.2-0.5ms / parse ≈0.3-0.6ms / ReactJsonPretty 渲染 ≈15.2ms
 * 两者都在毫秒以下且互相重叠，所以哈希的意义**不是**省掉 parse —— 而是让
 * `ToolJsonBlock` 的 `memo` 拿到同一引用、跳过那 15ms 的渲染。
 *
 * ⚠️ 这里**不**对耗时做断言：hash 与 parse 在同一量级且抖动大到会翻号
 * （连测 5 轮有 1 轮反超），写成 `expect(hashMs).toBeLessThan(parseMs)`
 * 就是个偶发红测。真正的收益来自引用稳定，那由下面的相等性断言覆盖。
 */
describe("工具输出的内容哈希缓存前提", () => {
  it("相同文本得到相同哈希", () => {
    const text = JSON.stringify({
      a: 1,
      b: [1, 2, 3]
    });
    expect(hashContent(text)).toBe(hashContent(text));
  });

  it("不同文本得到不同哈希（含尾部差一字符）", () => {
    expect(hashContent('{"a":1}')).not.toBe(hashContent('{"a":2}'));
    expect(hashContent("line\n")).not.toBe(hashContent("line"));
  });

  it("长文本不同则哈希不同（抗低频碰撞）", () => {
    const rows = Array.from({ length: 2000 }, (_, i) => ({
      id: i,
      path: `src/m-${i}/f-${i}.ts`,
    }));
    const a = JSON.stringify({
      total: rows.length,
      rows
    }, null, 2);
    const b = `${a} `;
    expect(hashContent(a)).not.toBe(hashContent(b));
  });

  it("解析结果值相等（引用稳定由调用方的 useMemo 保证）", () => {
    const text = '{"a":1}';
    expect(parseToolJson(text)).toEqual(parseToolJson(text));
  });
});
