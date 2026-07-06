import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import WelcomeView from "./WelcomeView";

describe("WelcomeView featured cards", () => {
  it("renders featured section and view-all link when cards are provided", () => {
    const baseProps = {
      currentConversation: {
        id: "conversation-1",
        sessionId: "session-1",
        title: "新对话",
        productType: "chat",
        deepThink: false,
        role: null,
        createdAt: 0,
        updatedAt: 0,
        chatTitle: "",
        chatList: [],
        dataChatList: [],
      } as unknown as CHAT.ConversationHistory,
      product: {
        type: "chat",
        name: "聊天",
        placeholder: "请输入问题",
        img: "icon-chat",
        color: "text-[#4040FF]",
      } as unknown as CHAT.Product,
      displayOutput: {
        type: "chat",
        name: "聊天",
        placeholder: "请输入问题",
        img: "icon-chat",
        color: "text-[#4040FF]",
      } as unknown as CHAT.Product,
      currentConversationRole: null,
      fixRoles: [],
      visitorUsername: "visitor",
      videoModalOpen: undefined,
      onSelectionChange: () => {},
      onRoleSelect: () => {},
      onSend: () => {},
      onSendQuestion: () => {},
      onOpenVideo: () => {},
      onCloseVideo: () => {},
    };

    const html = renderToStaticMarkup(
      <MemoryRouter>
        <WelcomeView
          featuredCards={[
            {
              featuredId: "featured-home-001",
              sessionId: "session-featured-001",
              title: "精品案例",
              summary: "公开展示的会话",
              coverUrl: "https://file.example.com/cover.png",
              tags: ["研究"],
              publishedAt: "2026-07-06T10:00:00",
              contentLastActiveAt: "2026-07-06T11:00:00",
            },
          ]}
          {...baseProps}
        />
      </MemoryRouter>
    );

    expect(html).toContain("精品对话");
    expect(html).toContain("查看全部");
    expect(html).toContain("精品案例");
  });
});
