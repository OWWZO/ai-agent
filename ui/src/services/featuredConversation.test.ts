import { beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.fn();

vi.mock("./index", () => ({default: {get: getMock,},}));

describe("featuredConversation service", () => {
  beforeEach(() => {
    getMock.mockReset();
  });

  it("requests home cards from the dedicated public endpoint", async () => {
    getMock.mockResolvedValueOnce([]);
    const { featuredConversationApi } = await import("./featuredConversation");

    await featuredConversationApi.listHome(6);

    expect(getMock).toHaveBeenCalledWith("/api/agent/featured-conversations/home", {limit: 6,});
  });

  it("requests list page with pageNo and pageSize", async () => {
    getMock.mockResolvedValueOnce({
      total: 0,
      list: []
    });
    const { featuredConversationApi } = await import("./featuredConversation");

    await featuredConversationApi.list({
      pageNo: 2,
      pageSize: 12
    });

    expect(getMock).toHaveBeenCalledWith("/api/agent/featured-conversations", {
      pageNo: 2,
      pageSize: 12,
    });
  });

  it("requests featured replay by featuredId and requestId", async () => {
    getMock.mockResolvedValueOnce({});
    const { featuredConversationApi } = await import("./featuredConversation");

    await featuredConversationApi.getFeaturedRunReplay(
      "featured-001",
      "request-001"
    );

    expect(getMock).toHaveBeenCalledWith(
      "/api/agent/featured-conversations/featured-001/runs/request-001/replay"
    );
  });
});
