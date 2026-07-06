import { beforeEach, describe, expect, it, vi } from "vitest";

const postMock = vi.fn();
const putMock = vi.fn();

vi.mock("./index", () => ({
  default: {
    post: postMock,
    put: putMock,
  },
}));

describe("featuredConversationAdmin service", () => {
  beforeEach(() => {
    postMock.mockReset();
    putMock.mockReset();
  });

  it("queries the admin list with filter payload", async () => {
    postMock.mockResolvedValueOnce({
      total: 0,
      list: [],
    });
    const { featuredConversationAdminApi } = await import(
      "./featuredConversationAdmin"
    );

    await featuredConversationAdminApi.queryList({
      sessionId: "session-001",
      status: "OFFLINE",
      pageNo: 1,
      pageSize: 10,
    });

    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/admin/featured-conversations/query-list",
      {
        sessionId: "session-001",
        status: "OFFLINE",
        pageNo: 1,
        pageSize: 10,
      }
    );
  });

  it("creates a featured conversation with the upsert payload", async () => {
    postMock.mockResolvedValueOnce(true);
    const { featuredConversationAdminApi } = await import(
      "./featuredConversationAdmin"
    );

    await featuredConversationAdminApi.create({
      sessionId: "session-002",
      title: "精品案例",
      summary: "适合公开展示的会话",
      coverUrl: "https://file.example.com/cover.png",
      tags: ["研究", "案例"],
      sortOrder: 88,
      operator: "admin-ui",
    });

    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/admin/featured-conversations/create",
      {
        sessionId: "session-002",
        title: "精品案例",
        summary: "适合公开展示的会话",
        coverUrl: "https://file.example.com/cover.png",
        tags: ["研究", "案例"],
        sortOrder: 88,
        operator: "admin-ui",
      }
    );
  });

  it("updates a featured conversation through the dedicated endpoint", async () => {
    putMock.mockResolvedValueOnce(true);
    const { featuredConversationAdminApi } = await import(
      "./featuredConversationAdmin"
    );

    await featuredConversationAdminApi.update({
      featuredId: "featured-session-003",
      sessionId: "session-003",
      title: "精品案例-更新",
      summary: "更新后的摘要",
      tags: ["报告"],
      sortOrder: 99,
      operator: "admin-ui",
    });

    expect(putMock).toHaveBeenCalledWith(
      "/api/v1/admin/featured-conversations/update",
      {
        featuredId: "featured-session-003",
        sessionId: "session-003",
        title: "精品案例-更新",
        summary: "更新后的摘要",
        tags: ["报告"],
        sortOrder: 99,
        operator: "admin-ui",
      }
    );
  });

  it("toggles online and offline status with operator in query params", async () => {
    postMock.mockResolvedValue(true);
    const { featuredConversationAdminApi } = await import(
      "./featuredConversationAdmin"
    );

    await featuredConversationAdminApi.online(
      "featured-session-004",
      "admin-ui"
    );
    await featuredConversationAdminApi.offline(
      "featured-session-004",
      "admin-ui"
    );

    expect(postMock).toHaveBeenNthCalledWith(
      1,
      "/api/v1/admin/featured-conversations/online/featured-session-004",
      undefined,
      { params: { operator: "admin-ui" } }
    );
    expect(postMock).toHaveBeenNthCalledWith(
      2,
      "/api/v1/admin/featured-conversations/offline/featured-session-004",
      undefined,
      { params: { operator: "admin-ui" } }
    );
  });
});
