import { describe, expect, it } from "vitest";
import {
  resolveFilePreviewKind,
  resolvePreviewExtension,
  isBinaryBackedKind,
  isTextBackedKind,
} from "./kinds";
import { extractExtension, resolveCodeLanguage } from "./language";

describe("resolveFilePreviewKind — 扩展名命中", () => {
  it.each([
    ["a.md", "markdown"],
    ["a.markdown", "markdown"],
    ["a.mdx", "markdown"],
    ["a.svg", "svg"],
    ["a.png", "image"],
    ["a.apng", "image"],
    ["a.avif", "image"],
    ["a.mp4", "video"],
    ["a.m4v", "video"],
    ["a.mp3", "audio"],
    ["a.flac", "audio"],
    ["a.weba", "audio"],
    ["a.html", "html"],
    ["a.htm", "html"],
    ["a.pdf", "pdf"],
    ["a.docx", "docx"],
    ["a.doc", "doc"],
    ["a.xlsx", "xlsx"],
    ["a.xlsm", "xlsx"],
    ["a.xls", "xlsx"],
    ["a.csv", "csv"],
    ["a.tsv", "csv"],
    ["a.pptx", "pptx"],
    ["a.pps", "pptx"],
    ["a.glb", "model3d"],
    ["a.diff", "diff"],
    ["a.patch", "diff"],
    ["a.zip", "binary"],
    ["a.wasm", "binary"],
    ["a.ttf", "binary"],
    ["a.avi", "binary"],
    ["a.mkv", "binary"],
  ])("%s → %s", (name, kind) => {
    expect(resolveFilePreviewKind({ name })).toBe(kind);
  });

  it("已知代码扩展名走 code", () => {
    expect(resolveFilePreviewKind({ name: "main.ts" })).toBe("code");
    expect(resolveFilePreviewKind({ name: "index.py" })).toBe("code");
    expect(resolveFilePreviewKind({ name: "App.tsx" })).toBe("code");
  });

  it("未知扩展名按文本兜底", () => {
    expect(resolveFilePreviewKind({ name: "a.unknownext" })).toBe("text");
  });
});

describe("resolveFilePreviewKind — 大小写 / 路径 / URL", () => {
  it("大小写不敏感", () => {
    expect(resolveFilePreviewKind({ name: "REPORT.PDF" })).toBe("pdf");
    expect(resolveFilePreviewKind({ name: "Photo.JPEG" })).toBe("image");
  });

  it("能从 URL 取扩展名", () => {
    expect(resolveFilePreviewKind({ url: "https://x.com/a/report.pdf" })).toBe("pdf");
  });

  it("带 query / hash 的 URL 仍能正确取扩展名", () => {
    expect(resolveFilePreviewKind({ url: "https://x.com/a.pdf?v=2" })).toBe("pdf");
    expect(resolveFilePreviewKind({ url: "https://x.com/a.pdf#page=3" })).toBe("pdf");
  });

  it("反斜杠 Windows 路径", () => {
    expect(resolveFilePreviewKind({ name: "C:\\work\\out\\Q3.pptx" })).toBe("pptx");
  });

  it("无扩展名但有目录点号时不误判", () => {
    expect(resolveFilePreviewKind({ name: "my.folder/report" })).toBe("unknown");
  });
});

describe("resolveFilePreviewKind — MIME 兜底", () => {
  it("无扩展名时用 MIME", () => {
    expect(resolveFilePreviewKind({ mimeType: "application/pdf" })).toBe("pdf");
    expect(resolveFilePreviewKind({ mimeType: "image/png" })).toBe("image");
    expect(resolveFilePreviewKind({ mimeType: "video/mp4" })).toBe("video");
    expect(resolveFilePreviewKind({ mimeType: "audio/mpeg" })).toBe("audio");
    expect(resolveFilePreviewKind({ mimeType: "text/plain" })).toBe("text");
  });

  it("image/svg+xml 优先于 image/ 前缀", () => {
    expect(resolveFilePreviewKind({ mimeType: "image/svg+xml" })).toBe("svg");
  });

  it("扩展名比 MIME 更优先", () => {
    // 后端常把 .md 标成 octet-stream，此时必须以扩展名为准
    expect(
      resolveFilePreviewKind({ name: "readme.md", mimeType: "application/octet-stream" })
    ).toBe("markdown");
  });
});

describe("resolveFilePreviewKind — 空值", () => {
  it("空输入 → unknown", () => {
    expect(resolveFilePreviewKind()).toBe("unknown");
    expect(resolveFilePreviewKind(null)).toBe("unknown");
    expect(resolveFilePreviewKind({})).toBe("unknown");
    expect(resolveFilePreviewKind({ name: "" })).toBe("unknown");
  });

  it("字符串入参等价于 name", () => {
    expect(resolveFilePreviewKind("a.pdf")).toBe("pdf");
  });
});

describe("extractExtension / resolvePreviewExtension", () => {
  it("转小写并去掉前导点", () => {
    expect(extractExtension("Report.PDF")).toBe("pdf");
    expect(resolvePreviewExtension("Report.PDF")).toBe("pdf");
  });

  it("隐藏文件（点开头）不算扩展名", () => {
    expect(extractExtension(".gitignore")).toBe("");
    expect(extractExtension(".PDF")).toBe("");
    // 但以点开头的「类型串」仍应被规整成扩展名
    expect(resolvePreviewExtension(".pdf")).toBe("pdf");
  });

  it("没有扩展名时返回空串", () => {
    expect(extractExtension("Makefile")).toBe("");
    expect(extractExtension("a/b/c")).toBe("");
  });
});

describe("resolveCodeLanguage", () => {
  it("常见别名映射", () => {
    expect(resolveCodeLanguage("py")).toBe("python");
    expect(resolveCodeLanguage("yml")).toBe("yaml");
    expect(resolveCodeLanguage("sh")).toBe("bash");
    expect(resolveCodeLanguage("htm")).toBe("html");
  });

  it("接受完整文件名", () => {
    expect(resolveCodeLanguage("src/App.tsx")).toBe("tsx");
  });

  it("未知语言回退 text", () => {
    expect(resolveCodeLanguage("unknownext")).toBe("text");
    expect(resolveCodeLanguage("")).toBe("text");
  });
});

describe("kind 分组谓词", () => {
  it("文本类 kind", () => {
    expect(isTextBackedKind("markdown")).toBe(true);
    expect(isTextBackedKind("code")).toBe(true);
    expect(isTextBackedKind("pdf")).toBe(false);
  });

  it("二进制类 kind", () => {
    expect(isBinaryBackedKind("pdf")).toBe(true);
    expect(isBinaryBackedKind("video")).toBe(true);
    expect(isBinaryBackedKind("markdown")).toBe(false);
  });
});
