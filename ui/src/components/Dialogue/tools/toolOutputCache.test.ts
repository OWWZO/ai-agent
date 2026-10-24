import { describe, expect, it } from "vitest";
import { hashContent } from "@/lib/hashContent";
import { parseToolJson } from "@/components/Dialogue/tools/ToolJsonBlock";

/**
 * 验证 `ToolOutputBlock` 用**原文**而非哈希作为 memo 依赖的理由。
 *
 * 这里保留对 `hashContent` 的断言，是因为它现在是「不要用哈希做唯一身份」的
 * 反例来源：32 位 djb2 在几十个样本内就能撞到，而一旦碰撞，`useMemo` 会返回
 * 上一次的解析结果 —— 卡片把**上一个工具**的 JSON 显示在当前工具名下，
 * 且完全静默。这比多解析一次的代价严重得多，所以组件改用原文比较。
 */
describe("工具输出缓存的依赖选择", () => {
  it("hashContent 会碰撞，不能作为唯一身份", () => {
    // 实测这两个短串落在同一个 32 位值上。
    expect(hashContent("o4r")).toBe(hashContent("o60"));
  });

  it("原文比较不会把不同内容判成相同", () => {
    expect("o4r").not.toBe("o60");
    expect('{"a":1}').not.toBe('{"a":2}');
  });

  it("解析结果值相等（引用稳定由调用方的 useMemo 保证）", () => {
    const text = '{"a":1}';
    expect(parseToolJson(text)).toEqual(parseToolJson(text));
  });

  it("相同原文解析出同一份值（内容不变即可复用）", () => {
    const text = JSON.stringify({
      total: 2,
      rows: [{ id: 1 }, { id: 2 }]
    });
    expect(parseToolJson(text)).toEqual(parseToolJson(text));
  });
});
