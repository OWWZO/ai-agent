import { describe, expect, it, vi } from "vitest";

const postMock = vi.fn();
const getMock = vi.fn();

vi.mock("./index", () => ({
  default: {
    post: postMock,
    get: getMock,
  },
}));

describe("imageGeneration service", () => {
  it("为生图请求覆盖默认超时时间，避免长耗时任务在前端提前超时", async () => {
    postMock.mockResolvedValueOnce({
      data: "生成完成",
      fileInfo: [],
      requestId: "req-1",
    });

    const { requestImageGenerationTool } = await import("./imageGeneration");

    await requestImageGenerationTool({
      requestId: "req-1",
      prompt: "生成一张海报",
      mode: "images",
      size: "1024x1024",
      n: 1,
      fileNames: [],
      maskFileNames: [],
    });

    expect(postMock).toHaveBeenCalledWith(
      "/api/agent/image-generation/generate",
      expect.objectContaining({
        requestId: "req-1",
        prompt: "生成一张海报",
      }),
      expect.objectContaining({
        timeout: 330000,
      })
    );
  });

  it("历史查询继续复用默认请求配置", async () => {
    getMock.mockResolvedValueOnce({
      total: 0,
      list: [],
    });

    const { requestImageGenerationHistory } = await import("./imageGeneration");

    await requestImageGenerationHistory({
      pageNo: 1,
      pageSize: 10,
    });

    expect(getMock).toHaveBeenCalledWith(
      "/api/agent/image-generation/history",
      {
        pageNo: 1,
        pageSize: 10,
      }
    );
  });
});
