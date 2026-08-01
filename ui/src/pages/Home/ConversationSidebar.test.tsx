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
          onNewChat={() => {}}
          onSelectSession={() => {}}
          onChangeView={() => {}}
          onManageFeaturedConversation={() => {}}
        />
      </MemoryRouter>
    );

    expect(html).toContain("精品对话");
    expect(html).toContain("子 Agent");
    expect(html).toContain("查看当前任务的文件");
  });

  it("renders task file panel with back action", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ConversationSidebar
          activeView="chat"
          recentSessions={[]}
          recentSessionsLoading={false}
          sidebarPanel="task-files"
          taskList={[]}
          onNewChat={() => {}}
          onSelectSession={() => {}}
          onChangeView={() => {}}
          onManageFeaturedConversation={() => {}}
          onCloseTaskFiles={() => {}}
        />
      </MemoryRouter>
    );

    expect(html).toContain("文件");
    expect(html).toContain("返回");
    expect(html).toContain("当前任务暂无文件");
  });
});
