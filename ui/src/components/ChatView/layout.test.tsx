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

const dataDialogueMock = vi.fn(({ chat }: any) => (
  <div data-data-chat={chat.query} data-loading={String(Boolean(chat.loading))}>
    {chat.query}
  </div>
));

vi.mock("@/components/Dialogue/DataDialogue", () => ({
  default: (props: any) => dataDialogueMock(props),
}));

const generalInputMock = vi.fn((_props: any) => <div data-general-input="true">input</div>);

vi.mock("@/components/GeneralInput", () => ({
  default: (props: any) => generalInputMock(props),
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
  const htmlProduct = {
    type: "html",
    name: "网页模式",
    placeholder: "请输入问题",
    img: "icon-html",
    color: "text-[#29CC29]",
  };
  const docsProduct = {
    type: "docs",
    name: "文档模式",
    placeholder: "请输入问题",
    img: "icon-docs",
    color: "text-[#4040FF]",
  };
  const dataAgentProduct = {
    type: "dataAgent",
    name: "数据分析",
    placeholder: "请输入问题",
    img: "icon-data",
    color: "text-[#4040FF]",
  };

  return {
    defaultProduct: chatProduct,
    productList: [chatProduct, dataAgentProduct, htmlProduct, docsProduct],
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
  it("data agent pending first input renders optimistic loading dialogue", () => {
    dataDialogueMock.mockClear();

    const product: CHAT.Product = {
      type: "dataAgent",
      name: "数据分析",
      placeholder: "请输入问题",
      img: "icon-data",
      color: "text-[#4040FF]",
    };

    const conversation = {
      id: "conversation-data-1",
      sessionId: "session-data-1",
      title: "数据分析会话",
      productType: "dataAgent",
      deepThink: false,
      role: null,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      chatTitle: "",
      chatList: [],
      dataChatList: [],
    } as unknown as CHAT.ConversationHistory;

    renderToStaticMarkup(
      <ChatView
        inputInfo={{
          message: "帮我分析最近7天销量",
          outputStyle: "dataAgent",
          deepThink: false,
        }}
        product={product}
        conversation={conversation}
        chatRoles={[]}
        onConversationChange={vi.fn()}
        onRoleSelect={vi.fn()}
      />
    );

    expect(dataDialogueMock).toHaveBeenCalledTimes(1);
    expect(dataDialogueMock.mock.calls[0]?.[0]).toEqual(
      expect.objectContaining({
        chat: expect.objectContaining({
          query: "帮我分析最近7天销量",
          loading: true,
          think: "",
          error: "",
        }),
      })
    );
  });

  it("deep think conversation keeps structured input mode props in chat view", () => {
    generalInputMock.mockClear();

    const product: CHAT.Product = {
      type: "html",
      name: "网页模式",
      placeholder: "请输入问题",
      img: "icon-html",
      color: "text-[#29CC29]",
    };

    const conversation = {
      id: "conversation-2",
      sessionId: "session-2",
      title: "深度思考会话",
      productType: "html",
      deepThink: false,
      role: null,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      chatTitle: "",
      chatList: [
        {
          sessionId: "session-2",
          requestId: "req-2",
          query: "帮我分析这个需求",
          files: [],
          forceStop: false,
          multiAgent: {},
          loading: false,
          tasks: [],
          response: "好的",
        },
      ],
      dataChatList: [],
    } as unknown as CHAT.ConversationHistory;

    renderToStaticMarkup(
      <ChatView
        inputInfo={{ message: "", deepThink: false }}
        product={product}
        conversation={conversation}
        chatRoles={[]}
        onConversationChange={vi.fn()}
        onRoleSelect={vi.fn()}
      />
    );

    const lastCall = generalInputMock.mock.calls[generalInputMock.mock.calls.length - 1]?.[0];
    expect(lastCall).toMatchObject({
      product,
      deepThink: false,
      displayOutput: product,
      showRoleSelector: false,
      showBtn: false,
    });
  });

  it("deep research conversation keeps deepThink flag in chat view input", () => {
    generalInputMock.mockClear();

    const product: CHAT.Product = {
      type: "docs",
      name: "文档模式",
      placeholder: "请输入问题",
      img: "icon-docs",
      color: "text-[#4040FF]",
    };

    const conversation = {
      id: "conversation-3",
      sessionId: "session-3",
      title: "深度研究会话",
      productType: "docs",
      deepThink: true,
      role: null,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      chatTitle: "",
      chatList: [
        {
          sessionId: "session-3",
          requestId: "req-3",
          query: "帮我做行业调研",
          files: [],
          forceStop: false,
          multiAgent: {},
          loading: false,
          tasks: [],
          response: "好的",
        },
      ],
      dataChatList: [],
    } as unknown as CHAT.ConversationHistory;

    renderToStaticMarkup(
      <ChatView
        inputInfo={{ message: "", deepThink: false }}
        product={product}
        conversation={conversation}
        chatRoles={[]}
        onConversationChange={vi.fn()}
        onRoleSelect={vi.fn()}
      />
    );

    const lastCall = generalInputMock.mock.calls[generalInputMock.mock.calls.length - 1]?.[0];
    expect(lastCall).toMatchObject({
      product,
      deepThink: true,
      displayOutput: product,
      showRoleSelector: false,
      showBtn: false,
    });
  });

  it("single panel chat layout keeps the input inside a locked viewport shell", () => {
    generalInputMock.mockClear();

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
