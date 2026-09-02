import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getTaskFiles,
  isBinaryPreviewFileLike,
  isDocxFileLike,
  isLegacyDocFileLike,
  isPdfFileLike,
  isTextCopyableFileLike,
  normalizeTaskFile,
  isWordFileLike,
} from "./taskArtifacts";

afterEach(() => {
  vi.unstubAllGlobals();
});

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

  it("resolves bare image names through the current file service", () => {
    vi.stubGlobal("window", {
      location: {
        host: "localhost:3000",
        protocol: "http:",
      },
    });

    const file = normalizeTaskFile({
      fileName: "pexels-cat-1931369-fixed.jpg",
      url: "pexels-cat-1931369-fixed.jpg",
      requestId: "session-image-001",
    });

    expect(file?.url).toBe(
      "http://localhost:3000/tool/v1/file_tool/preview/session-image-001/pexels-cat-1931369-fixed.jpg"
    );
    expect(file?.missing).toBe(false);
  });

  it("uses the nested session id for bare files in task results", () => {
    vi.stubGlobal("window", {
      location: {
        host: "localhost:3000",
        protocol: "http:",
      },
    });

    const files = getTaskFiles({
      messageType: "file",
      resultMap: {
        sessionId: "session-image-002",
        fileInfo: [
          {
            fileName: "pexels-cat-1931369-fixed.jpg",
            url: "pexels-cat-1931369-fixed.jpg",
          },
        ],
      },
    });

    expect(files[0]?.url).toBe(
      "http://localhost:3000/tool/v1/file_tool/preview/session-image-002/pexels-cat-1931369-fixed.jpg"
    );
  });

  it("keeps relativePath when history fileInfo only has basename", () => {
    const files = getTaskFiles({
      messageType: "tool_result",
      artifactRefs: [
        {
          resourceKey: "artifact-style",
          fileName: "style.css",
          relativePath: "site/css/style.css",
          originFileName: "site/css/style.css",
          downloadUrl: "https://file.example.com/site/css/style.css",
          previewUrl: "https://file.example.com/preview/site/css/style.css",
        },
      ],
      resultMap: {
        fileInfo: [
          {
            fileName: "style.css",
            ossUrl: "https://file.example.com/site/css/style.css",
            domainUrl: "https://file.example.com/preview/site/css/style.css",
            downloadUrl: "https://file.example.com/site/css/style.css",
          },
        ],
      },
    });

    expect(files).toHaveLength(1);
    expect(files[0]?.relativePath).toBe("site/css/style.css");
    expect(files[0]?.name).toBe("style.css");
  });
});
