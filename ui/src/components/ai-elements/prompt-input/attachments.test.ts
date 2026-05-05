import { describe, expect, it, vi } from "vitest";

import {
  revokePromptInputAttachmentUrls,
  validatePromptInputFiles,
} from "./attachments";

describe("prompt-input attachment helpers", () => {
  it("不符合 accept 的文件应触发 accept 错误", () => {
    const files = [
      new File(["abc"], "demo.exe", { type: "application/x-msdownload" }),
    ];

    const result = validatePromptInputFiles(files, {
      accept: "image/*,.pdf",
      maxFiles: 3,
    });

    expect(result.accepted).toEqual([]);
    expect(result.error?.code).toBe("accept");
  });

  it("超过 maxFiles 时会截断并返回 max_files 错误", () => {
    const files = [
      new File(["1"], "a.png", { type: "image/png" }),
      new File(["2"], "b.png", { type: "image/png" }),
      new File(["3"], "c.png", { type: "image/png" }),
    ];

    const result = validatePromptInputFiles(files, {
      accept: "image/*",
      maxFiles: 2,
    });

    expect(result.accepted).toHaveLength(2);
    expect(result.error?.code).toBe("max_files");
  });

  it("全部超出 maxFileSize 时返回 max_file_size 错误", () => {
    const files = [
      new File(["12345"], "big.png", { type: "image/png" }),
    ];

    const result = validatePromptInputFiles(files, {
      accept: "image/*",
      maxFileSize: 2,
    });

    expect(result.accepted).toEqual([]);
    expect(result.error?.code).toBe("max_file_size");
  });

  it("清理附件时会回收 object url", () => {
    const revokeSpy = vi
      .spyOn(URL, "revokeObjectURL")
      .mockImplementation(() => {});

    revokePromptInputAttachmentUrls([
      { url: "blob:file-1" },
      { url: "blob:file-2" },
    ]);

    expect(revokeSpy).toHaveBeenCalledWith("blob:file-1");
    expect(revokeSpy).toHaveBeenCalledWith("blob:file-2");
  });
});
