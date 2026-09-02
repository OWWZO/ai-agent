import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import ChatView from "./index";

vi.mock("motion/react", () => ({
  motion: {div: ({ children, ...props }: any) => <div {...props}>{children}</div>,},
  AnimatePresence: ({ children }: any) => <>{children}</>,
}));

vi.mock("@/utils/querySSE", () => ({default: vi.fn(),}));

vi.mock("@/components/Dialogue", () => ({default: ({ chat }: any) => <div data-chat-id={chat.requestId}>{chat.query}</div>,}));

const dataDialogueMock = vi.fn(({ chat }: any) => (
  <div data-data-chat={chat.query} data-loading={String(Boolean(chat.loading))}>
    {chat.query}
  </div>
));

vi.mock("@/components/Dialogue/DataDialogue", () => ({default: (props: any) => dataDialogueMock(props),}));

const generalInputMock = vi.fn((props: any) => (
  <div
    data-general-input="true"
    data-session-id={props?.sessionId || ""}
  >
    input
  </div>
));

vi.mock("@/components/GeneralInput", () => ({default: (props: any) => generalInputMock(props),}));

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
  const dataAgentProduct = {
    type: "dataAgent",
    name: "数据分析",
    placeholder: "请输入问题",
    img: "icon-data",
    color: "text-[#4040FF]",
  };
  const taskProduct = {
    type: "task",
    name: "通用任务",
    placeholder: "请输入问题",
    img: "icon-task",
    color: "text-[#4040FF]",
  };

  return {
    defaultProduct: taskProduct,
    productList: [taskProduct, dataAgentProduct],
    getProductByType: (type?: string) => {
      return type === "dataAgent" ? dataAgentProduct : taskProduct;
    },
  };
});

vi.mock("ahooks", () => ({useMemoizedFn: (fn: unknown) => fn,}));

vi.mock("antd", () => ({Modal: {useModal: () => [{ info: vi.fn() }, null],},}));

const workspaceLayoutState = vi.hoisted(() => ({
  rightCollapsed: false,
  showAction: false,
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
  FolderOpen: () => <span>folder</span>,
  PanelLeftClose: () => <span>left</span>,
  PanelRightClose: () => <span>right</span>,
  PanelRightOpen: () => <span>right-open</span>,
}));

vi.mock("./useConversationStream", () => ({
  createConversationDraftController: vi.fn(),
  createDraftConversation: vi.fn(),
  useConversationStream: () => ({
    taskList: [],
    workspaceStreamTask: undefined,
    workspaceCaption: undefined,
    activeRunState: undefined,
    setActiveRunState: vi.fn(),
    plan: undefined,
    showAction: workspaceLayoutState.showAction,
    changeActionStatus: vi.fn(),
    loading: false,
    streamingThoughtMap: {},
    sendMessage: vi.fn(),
    stopActiveRun: vi.fn(),
    injectActiveRun: vi.fn(),
    regenerateLastMessage: vi.fn(),
    undoLastUserTurn: vi.fn(() => null),
  }),
}));

vi.mock("./useWorkspacePanels", () => ({
  useWorkspacePanels: () => ({
    leftPanelWidth: 50,
    isDragging: false,
    isLeftCollapsed: false,
    isRightCollapsed: workspaceLayoutState.rightCollapsed,
    isFocusMode: false,
    containerRef: { current: null },
    handleDragStart: vi.fn(),
    handleDragMove: vi.fn(),
    handleDragEnd: vi.fn(),
    setIsRightCollapsed: vi.fn(),
    setIsFocusMode: vi.fn(),
    toggleLeftPanel: vi.fn(),
    toggleRightPanel: vi.fn(),
    toggleFocusMode: vi.fn(),
    exitFocusMode: vi.fn(),
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
         onConversationChange={vi.fn()}
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
       type: "task",
       name: "通用任务",
      placeholder: "请输入问题",
      img: "icon-html",
      color: "text-[#29CC29]",
    };

    const conversation = {
      id: "conversation-2",
      sessionId: "session-2",
      title: "深度思考会话",
       productType: "task",
      deepThink: false,
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
        inputInfo={{
          message: "",
          deepThink: false
        }}
        product={product}
        conversation={conversation}
         onConversationChange={vi.fn()}
      />
    );

    const lastCall = generalInputMock.mock.calls[generalInputMock.mock.calls.length - 1]?.[0];
    expect(lastCall).toMatchObject({
      product: expect.objectContaining({ type: "task" }),
      deepThink: false,
       showBtn: false,
    });
  });

  it("deep research conversation keeps deepThink flag in chat view input", () => {
    generalInputMock.mockClear();

    const product: CHAT.Product = {
       type: "task",
       name: "通用任务",
      placeholder: "请输入问题",
      img: "icon-docs",
      color: "text-[#4040FF]",
    };

    const conversation = {
      id: "conversation-3",
      sessionId: "session-3",
      title: "深度研究会话",
       productType: "task",
      deepThink: true,
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
        inputInfo={{
          message: "",
          deepThink: false
        }}
        product={product}
        conversation={conversation}
         onConversationChange={vi.fn()}
      />
    );

    const lastCall = generalInputMock.mock.calls[generalInputMock.mock.calls.length - 1]?.[0];
    expect(lastCall).toMatchObject({
      product: expect.objectContaining({ type: "task" }),
      deepThink: true,
       showBtn: false,
    });
  });

  it("single panel chat layout keeps the input inside a locked viewport shell", () => {
    generalInputMock.mockClear();

    const product: CHAT.Product = {
       type: "task",
       name: "通用任务",
      placeholder: "请输入问题",
      img: "icon-chat",
      color: "text-[#4040FF]",
    };

    const conversation = {
      id: "conversation-1",
      sessionId: "session-1",
      title: "测试会话",
       productType: "task",
      deepThink: false,
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
        inputInfo={{
          message: "",
          deepThink: false
        }}
        product={product}
        conversation={conversation}
         onConversationChange={vi.fn()}
      />
    );

    expect(html).toContain(
      'class="flex h-full min-h-0 w-full max-w-[980px] flex-col overflow-hidden" id="chat-view"'
    );
    expect(html).toContain(
      'class="shrink-0 bg-[var(--color-bg)] pb-5 pt-4"'
    );
    expect(html).toContain('data-general-input="true"');
    expect(html).not.toContain("sticky bottom-0");
  });

  it("collapsed right workspace returns to centered single-chat shell", () => {
    workspaceLayoutState.rightCollapsed = true;
    workspaceLayoutState.showAction = true;

    try {
      const product: CHAT.Product = {
         type: "task",
         name: "通用任务",
        placeholder: "请输入问题",
        img: "icon-chat",
        color: "text-[#4040FF]",
      };

      const conversation = {
        id: "conversation-collapsed-workspace",
        sessionId: "session-collapsed-workspace",
        title: "折叠工作区会话",
         productType: "task",
        deepThink: false,
        createdAt: Date.now(),
        updatedAt: Date.now(),
        chatTitle: "",
        chatList: [],
        dataChatList: [],
      } as unknown as CHAT.ConversationHistory;

      const html = renderToStaticMarkup(
        <ChatView
          inputInfo={{
            message: "",
            deepThink: false,
          }}
          product={product}
          conversation={conversation}
           onConversationChange={vi.fn()}
        />
      );

      expect(html).toContain('data-workspace-open="false"');
      expect(html).toContain("reactor-single-chat-shell");
      expect(html).toMatch(/class="reactor-chat-panel-left[^"]*max-w-\[980px\]/);
      expect(html).not.toContain("reactor-workspace-panel");
      expect(html).not.toContain('style="width:50%"');
    } finally {
      workspaceLayoutState.rightCollapsed = false;
      workspaceLayoutState.showAction = false;
    }
  });

  it("read-only chat view hides the input while keeping the transcript shell", () => {
    generalInputMock.mockClear();

    const product: CHAT.Product = {
       type: "task",
       name: "通用任务",
      placeholder: "请输入问题",
      img: "icon-chat",
      color: "text-[#4040FF]",
    };

    const conversation = {
      id: "conversation-readonly-1",
      sessionId: "session-readonly-1",
      title: "只读会话",
       productType: "task",
      deepThink: false,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      chatTitle: "",
      chatList: [
        {
          sessionId: "session-readonly-1",
          requestId: "req-readonly-1",
          query: "展示历史消息",
          files: [],
          forceStop: false,
          multiAgent: {},
          loading: false,
          tasks: [],
          response: "这是历史回复",
        },
      ],
      dataChatList: [],
    } as unknown as CHAT.ConversationHistory;

    const html = renderToStaticMarkup(
      <ChatView
        inputInfo={{
          message: "",
          deepThink: false
        }}
        product={product}
        conversation={conversation}
         readOnly
         onConversationChange={vi.fn()}
      />
    );

    expect(html).toContain('id="chat-view"');
    expect(html).toContain("展示历史消息");
    expect(html).not.toContain('<div data-general-input="true">input</div>');
    expect(generalInputMock).not.toHaveBeenCalled();
  });
});
