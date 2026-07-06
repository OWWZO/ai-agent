import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { FeaturedConversationsView } from "./view";

describe("FeaturedConversationsView", () => {
  it("renders page title and featured conversation cards", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <FeaturedConversationsView
          page={{
            total: 1,
            list: [
              {
                featuredId: "featured-list-001",
                sessionId: "session-list-001",
                title: "公开案例",
                summary: "用于列表页渲染",
                coverUrl: "",
                tags: ["写作"],
                publishedAt: "2026-07-06T10:00:00",
                contentLastActiveAt: "2026-07-06T11:00:00",
              },
            ],
          }}
          loading={false}
          pageNo={1}
          pageSize={20}
          onPageChange={vi.fn()}
        />
      </MemoryRouter>
    );

    expect(html).toContain("精品对话");
    expect(html).toContain("公开案例");
    expect(html).toContain("用于列表页渲染");
  });
});
