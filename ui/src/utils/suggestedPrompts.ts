import type { SuggestedQuestion } from "./constants";

export type PromptPlaceholderRange = {
  token: string;
  index: number;
  start: number;
  end: number;
};

export type SuggestedPromptDraft = {
  text: string;
  placeholders: string[];
  placeholderRanges: PromptPlaceholderRange[];
};

export type PromptSelectionRequest = SuggestedPromptDraft & {
  requestId: number;
};

export function findPromptPlaceholderRanges(
  text: string,
  placeholders: string[]
): PromptPlaceholderRange[] {
  const nextSearchOffset = new Map<string, number>();

  return placeholders.flatMap((token, index) => {
    if (!token) {
      return [];
    }

    const start = text.indexOf(token, nextSearchOffset.get(token) ?? 0);
    if (start < 0) {
      return [];
    }

    nextSearchOffset.set(token, start + token.length);
    return [
      {
        token,
        index,
        start,
        end: start + token.length,
      },
    ];
  });
}

export function buildSuggestedPromptDraft(
  question: SuggestedQuestion
): SuggestedPromptDraft {
  const text = question.template || question.label;
  const placeholders = question.placeholders || [];

  return {
    text,
    placeholders,
    placeholderRanges: findPromptPlaceholderRanges(text, placeholders),
  };
}

export function resolveAdjacentPromptPlaceholder(
  text: string,
  request: PromptSelectionRequest,
  selectionStart: number,
  selectionEnd: number,
  direction: 1 | -1
): PromptPlaceholderRange | null {
  const ranges = findPromptPlaceholderRanges(text, request.placeholders);
  if (ranges.length === 0) {
    return null;
  }

  if (direction > 0) {
    return (
      ranges.find((range) => range.start > selectionEnd) || ranges[0] || null
    );
  }

  return (
    [...ranges].reverse().find((range) => range.end < selectionStart) ||
    ranges[ranges.length - 1] ||
    null
  );
}
