import { describe, expect, it } from "vitest";

import { normalizeMarkdownForDisplay } from "./markdown";

describe("normalizeMarkdownForDisplay", () => {
  it("splits sticky structured-summary headings without space", () => {
    const sample =
      "已为你整理并生成 HTML资讯分析报告，可直接打开查看完整可视化内容。###核心结论2025–2026年间，Linux内核接连披露多个高危漏洞。###报告内容概览HTML报告围绕以下内容展开：\n安全启示：人机协同###交付文件请查看最终 HTML报告文件。";

    const normalized = normalizeMarkdownForDisplay(sample, {
      scope: "structured_summary",
    });

    expect(normalized).toContain("内容。\n\n### 核心结论2025");
    expect(normalized).toContain("漏洞。\n\n### 报告内容概览HTML");
    expect(normalized).toContain("协同\n\n### 交付文件请查看");
  });

  it("keeps already valid headings intact", () => {
    const sample = "前言\n\n### 核心结论\n\n正文";

    const normalized = normalizeMarkdownForDisplay(sample, {
      scope: "structured_summary",
    });

    expect(normalized).toBe("前言\n\n### 核心结论\n\n正文");
  });

  it("does not rewrite code fences", () => {
    const sample = "说明\n\n```md\n###标题\n```\n\n结束";

    const normalized = normalizeMarkdownForDisplay(sample, {
      scope: "structured_summary",
    });

    expect(normalized).toContain("```md\n###标题\n```");
  });

  it("fixes line-start headings missing spaces", () => {
    const sample = "###1）经典必去\n##你如果想继续聊";

    const normalized = normalizeMarkdownForDisplay(sample, {
      scope: "structured_summary",
    });

    expect(normalized).toContain("### 1）经典必去");
    expect(normalized).toContain("## 你如果想继续聊");
  });

  it("splits sticky table rows and adds missing header", () => {
    const sample =
      "| --- | --- | --- | |需求分析 |20%–30% |明确目标、降低返工风险的基础阶段 | |设计 |15%–20% |架构、数据库、界面与流程设计 | |编码开发 |30%–40% |通常占比最高，直接影响进度与质量 | |测试 |15%–20% |功能、性能、安全与体验验证 | |维护优化 |10%–15% | Bug修复、性能优化、版本迭代 |";

    const normalized = normalizeMarkdownForDisplay(sample, {
      scope: "structured_summary",
    });

    expect(normalized).toContain("| --- | --- | --- |");
    expect(normalized).toContain("|需求分析 |20%–30% |明确目标、降低返工风险的基础阶段 |");
    expect(normalized).toContain("|设计 |15%–20% |架构、数据库、界面与流程设计 |");
    expect(normalized).toContain("|编码开发 |30%–40% |通常占比最高，直接影响进度与质量 |");
    expect(normalized).toContain("|测试 |15%–20% |功能、性能、安全与体验验证 |");
    expect(normalized).toContain("|维护优化 |10%–15% | Bug修复、性能优化、版本迭代 |");
    expect(normalized.split("\n")[0]).toMatch(/^\|\s*\|\s*\|\s*\|$/);
    expect(normalized).toMatch(/\|\s*\|\s*\|\s*\|\n\| --- \| --- \| --- \|/);
  });

  it("keeps valid multi-line tables intact", () => {
    const sample = [
      "| 阶段 | 占比 | 说明 |",
      "| --- | --- | --- |",
      "| 需求分析 | 20%–30% | 明确目标 |",
    ].join("\n");

    const normalized = normalizeMarkdownForDisplay(sample, {
      scope: "structured_summary",
    });

    expect(normalized).toBe(sample);
  });

  it("passes through empty content", () => {
    expect(normalizeMarkdownForDisplay(undefined)).toBe("");
    expect(normalizeMarkdownForDisplay("")).toBe("");
  });

  it("normalizes BOM and newlines only for default scope sticky headings still split", () => {
    expect(normalizeMarkdownForDisplay("\uFEFFa\r\nb\rc")).toBe("a\nb\nc");
    expect(normalizeMarkdownForDisplay("前言## 标题")).toBe("前言\n\n## 标题");
  });
});
