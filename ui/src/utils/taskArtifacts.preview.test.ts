import { describe, expect, it } from "vitest";
import {
  isBinaryPreviewFileLike,
  isDocxFileLike,
  isLegacyDocFileLike,
  isPdfFileLike,
  isTextCopyableFileLike,
  isWordFileLike,
} from "./taskArtifacts";

describe("taskArtifacts document type helpers", () => {
  it("detects pdf by extension and mime", () => {
    expect(isPdfFileLike({ name: "a.PDF", type: "pdf" })).toBe(true);
    expect(
      isPdfFileLike({ name: "x", type: "", mimeType: "application/pdf" })
    ).toBe(true);
    expect(isPdfFileLike({ name: "a.md", type: "md" })).toBe(false);
  });

  it("detects docx vs legacy doc", () => {
    expect(isDocxFileLike({ name: "a.docx", type: "docx" })).toBe(true);
    expect(isLegacyDocFileLike({ name: "a.doc", type: "doc" })).toBe(true);
    expect(isLegacyDocFileLike({ name: "a.docx", type: "docx" })).toBe(false);
    expect(isWordFileLike({ name: "a.doc", type: "doc" })).toBe(true);
    expect(isWordFileLike({ name: "a.docx", type: "docx" })).toBe(true);
  });

  it("marks office/binary as non-text-copyable", () => {
    expect(isBinaryPreviewFileLike({ name: "a.pdf", type: "pdf" })).toBe(true);
    expect(isBinaryPreviewFileLike({ name: "a.docx", type: "docx" })).toBe(
      true
    );
    expect(isTextCopyableFileLike({ name: "a.pdf", type: "pdf" })).toBe(false);
    expect(isTextCopyableFileLike({ name: "a.md", type: "md" })).toBe(true);
  });
});
