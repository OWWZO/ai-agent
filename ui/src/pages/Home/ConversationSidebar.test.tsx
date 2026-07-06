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
          onOpenFeaturedConversations={() => {}}
          onManageFeaturedConversation={() => {}}
        />
      </MemoryRouter>
    );

    expect(html).toContain("精品对话");
  });
});
