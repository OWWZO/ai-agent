import { describe, expect, it, vi } from "vitest";

const getMock = vi.fn();

vi.mock("./index", () => ({default: {get: getMock,},}));

describe("featuredConversation service", () => {
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
});
