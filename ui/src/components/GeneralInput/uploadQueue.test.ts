import { describe, expect, it } from "vitest";

import { markUploadSuccess, type UploadAttachmentState } from "./uploadQueue";

describe("uploadQueue", () => {
  it("上传成功后应更新对应附件状态", () => {
    const queue: Record<string, UploadAttachmentState> = {
      "att-1": {
        id: "att-1",
        file: new File(["demo"], "demo.txt"),
        status: "uploading",
      },
    };
    const uploadedFile = {
      name: "demo.txt",
      type: "txt",
      size: 4,
      url: "https://example.com/demo.txt",
    } as CHAT.TFile;

    const next = markUploadSuccess(queue, "att-1", uploadedFile);

    expect(next["att-1"]).toMatchObject({
      status: "success",
      uploadedFile,
    });
  });
});
