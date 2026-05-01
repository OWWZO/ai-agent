import { describe, expect, it } from "vitest";

import {
  buildAgentStreamRequest,
  mapSessionFiles,
} from "./agentRequest";

describe("agentRequest", () => {
  it("会把当前轮上传附件映射为后端可消费的 sessionFiles", () => {
    const files: CHAT.TFile[] = [
      {
        name: "source-image.png",
        url: "https://file.example.com/preview/source-image.png",
        previewUrl: "https://file.example.com/preview/source-image.png",
        downloadUrl: "https://file.example.com/download/source-image.png",
        type: "png",
        size: 1024,
        resourceKey: "session-1:source-image.png:hash",
        mimeType: "image/png",
        originFileName: "原图.png",
      },
    ];

    expect(mapSessionFiles(files)).toEqual([
      {
        fileName: "source-image.png",
        ossUrl: "https://file.example.com/download/source-image.png",
        domainUrl: "https://file.example.com/preview/source-image.png",
        fileSize: 1024,
        fileType: "png",
        resourceKey: "session-1:source-image.png:hash",
        mimeType: "image/png",
        originFileName: "原图.png",
      },
    ]);
  });

  it("非聊天模式会携带 sessionFiles 且不会透传 aiAgentId", () => {
    const request = buildAgentStreamRequest({
      sessionId: "session-1",
      requestId: "req-1",
      message: "基于这张图改成赛博朋克风",
      deepThink: false,
      outputStyle: "html",
      files: [
        {
          name: "source-image.png",
          url: "https://file.example.com/preview/source-image.png",
          previewUrl: "https://file.example.com/preview/source-image.png",
          downloadUrl: "https://file.example.com/download/source-image.png",
          type: "png",
          size: 1024,
        },
      ],
      aiAgentId: "role-1",
      fallbackRoleAgentId: "role-2",
    });

    expect(request).toMatchObject({
      sessionId: "session-1",
      requestId: "req-1",
      query: "基于这张图改成赛博朋克风",
      deepThink: 0,
      outputStyle: "html",
      sessionFiles: [
        {
          fileName: "source-image.png",
        },
      ],
    });
    expect(request).not.toHaveProperty("aiAgentId");
  });
});
