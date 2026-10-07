import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import ConversationSidebar from "./ConversationSidebar";

describe("ConversationSidebar", () => {
  it("renders the featured conversations navigation entry", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ConversationSidebar
          activeView="chat"
          recentSessions={[]}
          recentSessionsLoading={false}
          recentSessionsLoadingMore={false}
          recentSessionsHasMore={false}
          user={null}
          onNewChat={() => {}}
          onSelectSession={() => {}}
          onLoadMoreRecentSessions={() => {}}
          onChangeView={() => {}}
          onManageFeaturedConversation={() => {}}
          onLogout={() => {}}
        />
      </MemoryRouter>
    );

    expect(html).toContain("精品对话");
    expect(html).toContain("子 Agent");
    expect(html).toContain("工具");
    expect(html).toContain("查看当前会话的文件");
  });

  it("renders task file panel with back action", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ConversationSidebar
          activeView="chat"
          recentSessions={[]}
          recentSessionsLoading={false}
          recentSessionsLoadingMore={false}
          recentSessionsHasMore={false}
          user={null}
          sidebarPanel="task-files"
          taskList={[]}
          onNewChat={() => {}}
          onSelectSession={() => {}}
          onLoadMoreRecentSessions={() => {}}
          onChangeView={() => {}}
          onManageFeaturedConversation={() => {}}
          onLogout={() => {}}
          onCloseTaskFiles={() => {}}
        />
      </MemoryRouter>
    );

    expect(html).toContain("文件");
    expect(html).toContain("返回");
    expect(html).toContain("当前会话暂无文件");
  });
});
