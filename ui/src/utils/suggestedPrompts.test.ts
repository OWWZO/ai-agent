import { describe, expect, it } from "vitest";

import type { SuggestedQuestion } from "./constants";
import {
  buildSuggestedPromptDraft,
  resolveAdjacentPromptPlaceholder,
} from "./suggestedPrompts";

const productComparisonPrompt: SuggestedQuestion = {
  id: "product-comparison",
  category: "深度调研",
  label: "做一份产品竞品对比研究",
  template: "比较【产品 A】、【产品 B】和【产品 C】。",
  icon: "research",
  placeholders: ["【产品 A】", "【产品 B】", "【产品 C】"],
};

describe("suggested prompt helpers", () => {
  it("builds the inserted text and the first placeholder range", () => {
    const draft = buildSuggestedPromptDraft(productComparisonPrompt);

    expect(draft.text).toBe("比较【产品 A】、【产品 B】和【产品 C】。");
    expect(draft.placeholderRanges).toEqual([
      {
        token: "【产品 A】",
        index: 0,
        start: 2,
        end: 8,
      },
      {
        token: "【产品 B】",
        index: 1,
        start: 9,
        end: 15,
      },
      {
        token: "【产品 C】",
        index: 2,
        start: 16,
        end: 22,
      },
    ]);
  });

  it("moves to the next remaining placeholder after a replacement", () => {
    const draft = buildSuggestedPromptDraft(productComparisonPrompt);
    const request = {
      ...draft,
      requestId: 1,
    };
    const editedText = "比较 Notion、【产品 B】和【产品 C】。";
    const next = resolveAdjacentPromptPlaceholder(
      editedText,
      request,
      9,
      9,
      1
    );

    expect(next).toEqual({
      token: "【产品 B】",
      index: 1,
      start: 10,
      end: 16,
    });
  });

  it("wraps placeholder navigation in both directions", () => {
    const draft = buildSuggestedPromptDraft(productComparisonPrompt);
    const request = {
      ...draft,
      requestId: 1,
    };

    expect(
      resolveAdjacentPromptPlaceholder(draft.text, request, 16, 22, 1)
    ).toEqual({
      token: "【产品 A】",
      index: 0,
      start: 2,
      end: 8,
    });
    expect(
      resolveAdjacentPromptPlaceholder(draft.text, request, 2, 8, -1)
    ).toEqual({
      token: "【产品 C】",
      index: 2,
      start: 16,
      end: 22,
    });
  });
});
