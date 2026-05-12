import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import ChatView from "./index";

vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: any) => <div {...props}>{children}</div>,
  },
  AnimatePresence: ({ children }: any) => <>{children}</>,
}));

vi.mock("@/utils/querySSE", () => ({
  default: vi.fn(),
}));

vi.mock("@/components/Dialogue", () => ({
  default: ({ chat }: any) => <div data-chat-id={chat.requestId}>{chat.query}</div>,
}));

vi.mock("@/components/Dialogue/DataDialogue", () => ({
  default: ({ chat }: any) => <div data-data-chat={chat.query}>{chat.query}</div>,
}));

vi.mock("@/components/GeneralInput", () => ({
  default: () => <div data-general-input="true">input</div>,
}));

vi.mock("@/components/ActionView", () => ({
  default: Object.assign(
    () => <div data-action-view="true">action-view</div>,
    {
      useActionView: () => ({
        current: {
          changeActionView: vi.fn(),
          setFilePreview: vi.fn(),
          openPlanView: vi.fn(),
        },
      }),
    }
  ),
}));

vi.mock("@/utils/constants", () => {
  const chatProduct = {
    type: "chat",
    name: "聊天模式",
    placeholder: "请输入问题",
    img: "icon-chat",
    color: "text-[#4040FF]",
  };

  return {
    defaultProduct: chatProduct,
    productList: [chatProduct],
  };
});

vi.mock("ahooks", () => ({
  useMemoizedFn: (fn: unknown) => fn,
}));

vi.mock("antd", () => ({
  Modal: {
    useModal: () => [{ info: vi.fn() }, null],
  },
}));

vi.mock("@/components/ai-elements/conversation", () => ({
  Conversation: ({ className, children }: any) => (
    <div className={className}>{children}</div>
  ),
  ConversationContent: ({ className, children }: any) => (
    <div className={className}>{children}</div>
  ),
  ConversationScrollButton: () => <div data-scroll-button="true">scroll</div>,
}));

vi.mock("lucide-react", () => ({
  PanelLeftClose: () => <span>left</span>,
  PanelRightClose: () => <span>right</span>,
}));

vi.mock("./useConversationStream", () => ({
  createConversationDraftController: vi.fn(),
  createDraftConversation: vi.fn(),
  useConversationStream: () => ({
    taskList: [],
    workspaceStreamTask: undefined,
    activeRunState: undefined,
    setActiveRunState: vi.fn(),
    plan: undefined,
    showAction: false,
    changeActionStatus: vi.fn(),
    loading: false,
    streamingThoughtMap: {},
    sendMessage: vi.fn(),
    regenerateLastMessage: vi.fn(),
  }),
}));

vi.mock("./useWorkspacePanels", () => ({
  useWorkspacePanels: () => ({
    leftPanelWidth: 50,
    isDragging: false,
    isLeftCollapsed: false,
    isRightCollapsed: false,
    containerRef: { current: null },
    handleDragStart: vi.fn(),
    setIsRightCollapsed: vi.fn(),
    toggleLeftPanel: vi.fn(),
    toggleRightPanel: vi.fn(),
  }),
}));

describe("ChatView layout", () => {
  it("single panel chat layout keeps the input inside a locked viewport shell", () => {
    const product: CHAT.Product = {
      type: "chat",
      name: "聊天模式",
      placeholder: "请输入问题",
      img: "icon-chat",
      color: "text-[#4040FF]",
    };

    const conversation = {
      id: "conversation-1",
      sessionId: "session-1",
      title: "测试会话",
      productType: "chat",
      deepThink: false,
      role: null,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      chatTitle: "",
      chatList: [
        {
          sessionId: "session-1",
          requestId: "req-1",
          query: "你好",
          files: [],
          forceStop: false,
          multiAgent: {},
          loading: false,
          tasks: [],
          response: "你好",
        },
      ],
      dataChatList: [],
    } as unknown as CHAT.ConversationHistory;

    const html = renderToStaticMarkup(
      <ChatView
        inputInfo={{ message: "", deepThink: false }}
        product={product}
        conversation={conversation}
        chatRoles={[]}
        onConversationChange={vi.fn()}
        onRoleSelect={vi.fn()}
      />
    );

    expect(html).toContain(
      'class="flex h-full min-h-0 w-full max-w-[980px] flex-col overflow-hidden" id="chat-view"'
    );
    expect(html).toContain(
      'class="shrink-0 bg-gradient-to-t from-[var(--page-gradient)] via-[var(--page-gradient)]/95 to-transparent pb-5 pt-4"'
    );
    expect(html).toContain('<div data-general-input="true">input</div>');
    expect(html).not.toContain("sticky bottom-0");
  });
});
