import { describe, expect, it } from "vitest";

import {
  buildImageBatchPlans,
  runImageBatchRequests,
  shouldUseImageBatchMode,
} from "./batch";

describe("workspace image generation batch helpers", () => {
  it("returns batch mode when edits has multiple images and switch is enabled", () => {
    expect(
      shouldUseImageBatchMode({
        mode: "edits",
        imageCount: 3,
        batchMode: true,
      })
    ).toBe(true);
  });

  it("does not use batch mode for non-edits or single image", () => {
    expect(
      shouldUseImageBatchMode({
        mode: "images",
        imageCount: 3,
        batchMode: true,
      })
    ).toBe(false);
    expect(
      shouldUseImageBatchMode({
        mode: "edits",
        imageCount: 1,
        batchMode: true,
      })
    ).toBe(false);
  });

  it("splits multi-image edit request into one request per image", () => {
    const plans = buildImageBatchPlans({
      prompt: "统一改成电影海报",
      size: "1024x1024",
      images: [
        {
          fileName: "1.png",
          source: "data:image/png;base64,1",
          mask: ""
        },
        {
          fileName: "2.png",
          source: "data:image/png;base64,2",
          mask: "data:image/png;base64,mask"
        },
      ],
    });

    expect(plans).toHaveLength(2);
    expect(plans[0]).toMatchObject({
      key: "1",
      fileNames: ["data:image/png;base64,1"],
      maskFileNames: [""],
      fileName: "1",
    });
    expect(plans[1]).toMatchObject({
      key: "2",
      fileNames: ["data:image/png;base64,2"],
      maskFileNames: ["data:image/png;base64,mask"],
      fileName: "2",
    });
  });

  it("runs one request per batch plan and keeps per-item success and failure", async () => {
    const plans = buildImageBatchPlans({
      prompt: "统一改成电影海报",
      size: "1024x1024",
      images: [
        {
          fileName: "1.png",
          source: "data:image/png;base64,1",
          mask: ""
        },
        {
          fileName: "2.png",
          source: "data:image/png;base64,2",
          mask: ""
        },
      ],
    });

    const results = await runImageBatchRequests({
      prompt: "统一改成电影海报",
      size: "1024x1024",
      n: 1,
      plans,
      createRequestId: (index) => `req-${index + 1}`,
      request: async (payload) => {
        if (payload.fileName === "2") {
          throw new Error("第二张失败");
        }
        return {
          data: "ok",
          fileInfo: [],
          requestId: payload.requestId,
          usedFallback: false,
        };
      },
    });

    expect(results).toHaveLength(2);
    expect(results[0]).toMatchObject({
      success: true,
      index: 0,
      plan: { fileName: "1" },
      response: { requestId: "req-1" },
    });
    expect(results[1]).toMatchObject({
      success: false,
      index: 1,
      plan: { fileName: "2" },
    });
  });
});
