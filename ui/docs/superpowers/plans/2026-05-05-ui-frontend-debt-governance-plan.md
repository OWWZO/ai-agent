# UI Frontend Debt Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变前端可观察行为的前提下，按优先级治理 `ui/` 中剩余的高维护成本代码，先收口聊天主链路和首页/输入状态机，再处理 MRag 工作台、图表遗留区与预览渲染分支。

**Architecture:** 这次不做功能增强，只做职责收敛。策略是先锁住现有聊天核心拆分的行为基线，再把“页面级状态机”“上传状态机”“工作台异步编排”“遗留渲染策略”分别抽到纯函数、hook 和小组件里，让主页面文件退化为装配层。

**Tech Stack:** React 19、TypeScript 5、Vite 6、Vitest、Ant Design 5、Radix UI、`@microsoft/fetch-event-source`、现有 `ui/src/utils/chat.ts` 投影链与 `querySSE` 流式链路。

---

## Debt Governance Invariants

- 不改变 SSE 协议字段名、任务 `messageType` 语义和对话持久化消费格式。
- 不改变首页默认进入的产品类型和角色兜底规则。
- 不改变附件上传接口、上传完成后的 `CHAT.TFile` 结构。
- 不改变 MRag 工作台的 Tool Base URL 存储键、知识库接口和流式问答接口。
- DataChat 仅做类型化与纯函数化，不改变既有图表结果与表格结果的可见文案。

## Phase Gate

- `src/components/ChatView/useConversationStream.ts`
- `src/utils/chat.ts`
- `src/components/Dialogue/index.tsx`
- `src/components/ActionPanel/useContent.ts`
- `src/pages/WorkspaceImageGeneration/index.tsx`

只有以上在途改动对应测试与构建恢复为绿色后，才进入后续债务治理任务。

---

## File Structure

### Wave 0：先收口当前在途聊天核心拆分

- Modify: `ui/src/components/ChatView/useConversationStream.ts`
  - 收口多智能体流式状态、工作区跟随与节流刷新逻辑。
- Create: `ui/src/components/ChatView/streamState.ts`
  - 承担 `showAction`、workspace follow、运行态派生等纯函数。
- Modify: `ui/src/utils/chat.ts`
  - 继续缩减分发与兼容壳责任，只保留对外入口。
- Modify: `ui/src/utils/chat/index.ts`
  - 统一对外导出，避免后续调用方继续直接依赖 `chat.ts` 内部实现。
- Test: `ui/src/components/ChatView/useConversationStream.test.ts`
- Test: `ui/src/utils/chat.test.ts`

### Wave 1：治理最高频入口状态机

- Modify: `ui/src/pages/Home/index.tsx`
  - 从入口组件中移出会话 bootstrap、历史 hydrate、模式切换同步。
- Create: `ui/src/pages/Home/homeState.ts`
  - 承担首页会话元数据派生与 bootstrap 判断纯函数。
- Create: `ui/src/pages/Home/useRecentSessions.ts`
  - 承担近期会话拉取与详情 hydrate。
- Create: `ui/src/pages/Home/useConversationBootstrap.ts`
  - 承担默认角色、session 恢复与空白态 bootstrap。
- Create: `ui/src/pages/Home/WelcomeView.tsx`
  - 承担欢迎态渲染，避免 `Home` 同时维护状态和大段 JSX。
- Test: `ui/src/pages/Home/homeState.test.ts`
- Test: `ui/src/pages/Home/RecentSessionList.test.tsx`

- Modify: `ui/src/components/GeneralInput/index.tsx`
  - 从输入组件中移出模式映射、上传队列状态机与上传副作用。
- Create: `ui/src/components/GeneralInput/inputMode.ts`
  - 承担模式/输出类型推导与发送 payload 组装。
- Create: `ui/src/components/GeneralInput/uploadQueue.ts`
  - 承担上传附件状态变更纯函数。
- Create: `ui/src/components/GeneralInput/useAttachmentUploads.ts`
  - 承担上传副作用与重试逻辑。
- Create: `ui/src/components/GeneralInput/UploadAttachmentChip.tsx`
  - 承担单个附件展示。
- Test: `ui/src/components/GeneralInput/inputMode.test.ts`
- Test: `ui/src/components/GeneralInput/uploadQueue.test.ts`

### Wave 2：治理工作台编排型页面

- Modify: `ui/src/pages/WorkspaceMRag/index.tsx`
  - 从工作台页面中移出知识库目录、文件轮询、流式问答三类异步编排。
- Create: `ui/src/pages/WorkspaceMRag/knowledgeBaseState.ts`
  - 承担知识库选择、轮询判断、查询状态重置纯函数。
- Create: `ui/src/pages/WorkspaceMRag/useKnowledgeBaseCatalog.ts`
  - 承担知识库列表拉取与创建。
- Create: `ui/src/pages/WorkspaceMRag/useKnowledgeBaseFiles.ts`
  - 承担文件列表、上传、网页入库、删除与轮询。
- Create: `ui/src/pages/WorkspaceMRag/useMragQuery.ts`
  - 承担问答流、Abort、chunk 累积与停止。
- Test: `ui/src/pages/WorkspaceMRag/knowledgeBaseState.test.ts`
- Test: `ui/src/pages/WorkspaceMRag/view.test.tsx`

### Wave 3：治理遗留渲染与弱类型区

- Modify: `ui/src/components/DataChat/index.tsx`
  - 从视图组件中移出图表策略和查询条件格式化。
- Delete: `ui/src/components/DataChat/ChartUtils.js`
- Create: `ui/src/components/DataChat/chartConfig.ts`
  - 用 TypeScript 承接原 `ChartUtils.js` 的配置转换纯函数。
- Create: `ui/src/components/DataChat/chartPresets.ts`
  - 承担 ECharts 模板常量。
- Create: `ui/src/components/DataChat/querySummary.ts`
  - 承担维度/指标/筛选/公式展示文案。
- Test: `ui/src/components/DataChat/chartConfig.test.ts`

- Modify: `ui/src/components/ActionPanel/ActionPanel.tsx`
  - 从主渲染组件中移出 renderer 选择逻辑。
- Modify: `ui/src/components/ActionView/FilePreview.tsx`
  - 从预览组件中移出标题、预览能力和翻页状态派生。
- Create: `ui/src/components/ActionPanel/panelResolver.ts`
  - 承担 `taskItem -> renderer` 的纯函数分发。
- Create: `ui/src/components/ActionView/filePreviewModel.ts`
  - 承担预览标题、导航索引、图标等纯函数。
- Test: `ui/src/components/ActionPanel/fileContentRender.test.ts`
- Create: `ui/src/components/ActionView/filePreviewModel.test.ts`

### 收尾

- Modify: `ui/CLAUDE.md`
  - 记录新的前端维护边界。
- Modify: `ui/docs/superpowers/plans/2026-05-05-ui-fat-code-simplification-plan.md`
  - 补充与本计划的依赖关系，避免两个计划冲突执行。

---

### Task 1: 锁住当前在途改动的基线

**Files:**
- Modify: `ui/docs/superpowers/plans/2026-05-05-ui-fat-code-simplification-plan.md`
- Modify: `ui/docs/superpowers/plans/2026-05-05-ui-frontend-debt-governance-plan.md`
- Test: `ui/src/utils/chat.test.ts`
- Test: `ui/src/components/ChatView/useConversationStream.test.ts`
- Test: `ui/src/components/Dialogue/plannerHistory.test.ts`
- Test: `ui/src/components/Dialogue/Timeline.test.tsx`
- Test: `ui/src/pages/WorkspaceImageGeneration/batch.test.ts`
- Test: `ui/src/pages/WorkspaceImageGeneration/useImageEditor.test.ts`

- [ ] **Step 1: 在计划文档里声明本轮不变量**

```md
## Debt Governance Invariants

- 不改变 SSE 协议字段名、任务 messageType 语义和对话持久化消费格式。
- 不改变首页默认进入的产品类型和角色兜底规则。
- 不改变附件上传接口、上传完成后的 `CHAT.TFile` 结构。
- 不改变 MRag 工作台的 Tool Base URL 存储键、知识库接口和流式问答接口。
- DataChat 仅做类型化与纯函数化，不改变既有图表结果与表格结果的可见文案。
```

- [ ] **Step 2: 跑当前聊天与图片工作台基线测试**

Run: `cd ui && npm run test -- src/utils/chat.test.ts src/components/ChatView/useConversationStream.test.ts src/components/Dialogue/plannerHistory.test.ts src/components/Dialogue/Timeline.test.tsx src/pages/WorkspaceImageGeneration/batch.test.ts src/pages/WorkspaceImageGeneration/useImageEditor.test.ts`

Expected: `PASS`，以上 6 个测试文件全部通过。

- [ ] **Step 3: 跑 lint 和 build 锁定基线**

Run: `cd ui && npm run lint && npm run build`

Expected: `eslint` 无新增 error，`tsc -b && vite build --mode production` 成功。

- [ ] **Step 4: 记录当前在途改动必须先收口的文件**

```md
## Phase Gate

- `src/components/ChatView/useConversationStream.ts`
- `src/utils/chat.ts`
- `src/components/Dialogue/index.tsx`
- `src/components/ActionPanel/useContent.ts`
- `src/pages/WorkspaceImageGeneration/index.tsx`

只有以上在途改动对应测试与构建恢复为绿色后，才进入后续债务治理任务。
```

- [ ] **Step 5: 提交计划与基线约束**

```bash
git add ui/docs/superpowers/plans/2026-05-05-ui-fat-code-simplification-plan.md ui/docs/superpowers/plans/2026-05-05-ui-frontend-debt-governance-plan.md
git commit -m "docs: add ui frontend debt governance plan"
```

---

### Task 2: 收口聊天核心拆分，避免继续把复杂度倒回主文件

**Files:**
- Create: `ui/src/components/ChatView/streamState.ts`
- Modify: `ui/src/components/ChatView/useConversationStream.ts`
- Modify: `ui/src/utils/chat.ts`
- Modify: `ui/src/utils/chat/index.ts`
- Test: `ui/src/components/ChatView/useConversationStream.test.ts`
- Test: `ui/src/utils/chat.test.ts`

- [ ] **Step 1: 为工作区显示判断补失败测试**

```ts
import { describe, expect, it } from "vitest";

import { resolveActionPanelVisibility } from "./streamState";

describe("streamState", () => {
  it("存在 plan 但没有 renderable task 时仍应打开右侧工作区", () => {
    expect(
      resolveActionPanelVisibility({
        plan: {
          stages: [{ title: "分析需求", status: "completed" }],
        } as unknown as CHAT.Plan,
        taskList: [],
      })
    ).toBe(true);
  });
});
```

- [ ] **Step 2: 先把工作区派生逻辑抽到纯函数**

```ts
// ui/src/components/ChatView/streamState.ts
export function resolveActionPanelVisibility(params: {
  plan?: CHAT.Plan;
  taskList: CHAT.Task[];
}) {
  return Boolean(params.plan) || params.taskList.some((task) => task.messageType !== "task_summary");
}

export function resolveLatestRunState(chat?: CHAT.ChatItem) {
  if (!chat) {
    return undefined;
  }
  return {
    status: chat.metrics?.status,
    finishedAt: chat.finishedAt,
  };
}
```

- [ ] **Step 3: 让 `useConversationStream.ts` 只负责编排与节流，不再自己决定所有派生规则**

```ts
// ui/src/components/ChatView/useConversationStream.ts
import { resolveActionPanelVisibility, resolveLatestRunState } from "./streamState";

setActiveRunState(resolveLatestRunState(latestChatSnapshot));
setShowAction(
  resolveActionPanelVisibility({
    plan: conversationTaskData.plan,
    taskList: conversationTaskData.taskList,
  })
);
```

- [ ] **Step 4: 缩减 `chat.ts` 对外面向调用方的表面积**

```ts
// ui/src/utils/chat/index.ts
export {
  buildAction,
  buildConversationTaskData,
  buildTaskFromEventData,
  combineData,
  getStableTaskIdentity,
  handleTaskData,
  normalizeEventData,
} from "../chat";
```

- [ ] **Step 5: 跑聊天核心相关测试**

Run: `cd ui && npm run test -- src/components/ChatView/useConversationStream.test.ts src/utils/chat.test.ts`

Expected: `PASS`，工作区显示、guard error、deep search 投影相关断言全部通过。

- [ ] **Step 6: 跑构建，确认当前拆分没有把类型打散**

Run: `cd ui && npm run build`

Expected: `PASS`。

- [ ] **Step 7: 提交聊天核心收口**

```bash
git add ui/src/components/ChatView/streamState.ts ui/src/components/ChatView/useConversationStream.ts ui/src/utils/chat.ts ui/src/utils/chat/index.ts ui/src/components/ChatView/useConversationStream.test.ts ui/src/utils/chat.test.ts
git commit -m "refactor: stabilize chat stream core"
```

---

### Task 3: 拆掉 `Home/index.tsx` 的入口状态机

**Files:**
- Create: `ui/src/pages/Home/homeState.ts`
- Create: `ui/src/pages/Home/homeState.test.ts`
- Create: `ui/src/pages/Home/useRecentSessions.ts`
- Create: `ui/src/pages/Home/useConversationBootstrap.ts`
- Create: `ui/src/pages/Home/WelcomeView.tsx`
- Modify: `ui/src/pages/Home/index.tsx`
- Test: `ui/src/pages/Home/RecentSessionList.test.tsx`

- [ ] **Step 1: 为首页模式切换元数据补失败测试**

```ts
import { describe, expect, it } from "vitest";

import { deriveConversationMetaFromInput } from "./homeState";

describe("homeState", () => {
  it("切到 dataAgent 时应清空角色并关闭 deepThink", () => {
    expect(
      deriveConversationMetaFromInput(
        {
          outputStyle: "dataAgent",
          deepThink: true,
        },
        {
          productType: "html",
          currentRole: {
            agentId: "agent-1",
            agentName: "默认角色",
            available: true,
            defaultRole: true,
          },
        }
      )
    ).toMatchObject({
      productType: "dataAgent",
      deepThink: false,
      role: null,
    });
  });
});
```

- [ ] **Step 2: 抽出首页纯函数，先锁住切换规则**

```ts
// ui/src/pages/Home/homeState.ts
export function deriveConversationMetaFromInput(
  info: Pick<CHAT.TInputInfo, "outputStyle" | "deepThink">,
  params: {
    productType: string;
    currentRole: CHAT.ConversationRole | null;
  }
) {
  const outputStyle = info.outputStyle || params.productType;
  const isChatMode = outputStyle === "chat";
  const deepThink =
    isChatMode || outputStyle === "dataAgent" ? false : Boolean(info.deepThink);

  return {
    productType: outputStyle,
    deepThink,
    role: isChatMode ? params.currentRole : null,
  };
}

export function shouldHydrateConversationHistory(params: {
  conversation: CHAT.ConversationHistory;
  hydratedSessionIds: Set<string>;
}) {
  return Boolean(
    params.conversation.sessionId &&
      params.conversation.chatList.length === 0 &&
      params.conversation.dataChatList.length === 0 &&
      !params.hydratedSessionIds.has(params.conversation.sessionId)
  );
}
```

- [ ] **Step 3: 抽出近期会话与 bootstrap hook**

```ts
// ui/src/pages/Home/useRecentSessions.ts
export function useRecentSessions() {
  const [recentSessions, setRecentSessions] = useState<ConversationSessionItem[]>([]);
  const [recentSessionsLoading, setRecentSessionsLoading] = useState(false);

  const refreshRecentSessions = useCallback(() => {
    setRecentSessionsLoading(true);
    return conversationHistoryApi
      .listSessions(20)
      .then((sessions) => {
        setRecentSessions(sessions || []);
      })
      .finally(() => {
        setRecentSessionsLoading(false);
      });
  }, []);

  return {
    recentSessions,
    recentSessionsLoading,
    refreshRecentSessions,
  };
}
```

```ts
// ui/src/pages/Home/useConversationBootstrap.ts
export function useConversationBootstrap(params: {
  conversation: CHAT.ConversationHistory;
  hydratedSessionIdsRef: React.MutableRefObject<Set<string>>;
  onHydrated: (nextConversation: CHAT.ConversationHistory) => void;
}) {
  useEffect(() => {
    if (
      !shouldHydrateConversationHistory({
        conversation: params.conversation,
        hydratedSessionIds: params.hydratedSessionIdsRef.current,
      })
    ) {
      return;
    }
    // 复用现有 hydrateConversationFromReplayFrames
  }, [params]);
}
```

- [ ] **Step 4: 把欢迎态 JSX 抽成独立组件，让 `Home` 退化成装配层**

```tsx
// ui/src/pages/Home/WelcomeView.tsx
export default function WelcomeView(props: {
  currentConversation: CHAT.ConversationHistory;
  product: CHAT.Product;
  displayOutput: CHAT.Product;
  currentConversationRole: CHAT.ConversationRole | null;
  fixRoles: CHAT.FixRole[];
  onSelectionChange: (selection: { product: CHAT.Product; deepThink: boolean }) => void;
  onRoleSelect: (role: CHAT.FixRole) => void;
  onSend: (inputInfo: CHAT.TInputInfo) => void;
}) {
  return (
    <AiChatSurface className="w-full rounded-[32px] bg-[var(--chat-surface)]/90 p-5 shadow-none">
      <GeneralInput
        key={`welcome-input-${props.currentConversation.sessionId}`}
        sessionId={props.currentConversation.sessionId}
        placeholder={props.product.placeholder}
        showBtn={true}
        size="big"
        disabled={false}
        product={props.product}
        deepThink={props.currentConversation.deepThink}
        displayOutput={props.displayOutput}
        chatRole={props.currentConversationRole}
        chatRoles={props.fixRoles}
        showRoleSelector={props.product.type === "chat"}
        send={props.onSend}
        onSelectionChange={props.onSelectionChange}
        onRoleSelect={props.onRoleSelect}
      />
    </AiChatSurface>
  );
}
```

- [ ] **Step 5: 跑首页相关测试与构建**

Run: `cd ui && npm run test -- src/pages/Home/homeState.test.ts src/pages/Home/RecentSessionList.test.tsx && npm run build`

Expected: `PASS`。

- [ ] **Step 6: 提交首页状态机拆分**

```bash
git add ui/src/pages/Home/homeState.ts ui/src/pages/Home/homeState.test.ts ui/src/pages/Home/useRecentSessions.ts ui/src/pages/Home/useConversationBootstrap.ts ui/src/pages/Home/WelcomeView.tsx ui/src/pages/Home/index.tsx ui/src/pages/Home/RecentSessionList.test.tsx
git commit -m "refactor: split home conversation state"
```

---

### Task 4: 拆掉 `GeneralInput/index.tsx` 的上传与模式状态机

**Files:**
- Create: `ui/src/components/GeneralInput/inputMode.ts`
- Create: `ui/src/components/GeneralInput/inputMode.test.ts`
- Create: `ui/src/components/GeneralInput/uploadQueue.ts`
- Create: `ui/src/components/GeneralInput/uploadQueue.test.ts`
- Create: `ui/src/components/GeneralInput/useAttachmentUploads.ts`
- Create: `ui/src/components/GeneralInput/UploadAttachmentChip.tsx`
- Modify: `ui/src/components/GeneralInput/index.tsx`

- [ ] **Step 1: 为发送 payload 组装补失败测试**

```ts
import { describe, expect, it } from "vitest";

import { buildSubmitPayload } from "./inputMode";

describe("inputMode", () => {
  it("深度研究模式应保留结构化输出类型并打开 deepThink", () => {
    expect(
      buildSubmitPayload({
        question: "帮我调研竞品",
        visibleMode: "research",
        isDataAgent: false,
        visibleOutputProduct: { type: "html" } as CHAT.Product,
        uploadedFiles: [],
        chatRole: null,
      })
    ).toMatchObject({
      outputStyle: "html",
      deepThink: true,
    });
  });
});
```

- [ ] **Step 2: 抽出模式推导与 payload 组装纯函数**

```ts
// ui/src/components/GeneralInput/inputMode.ts
export function buildSubmitPayload(params: {
  question: string;
  visibleMode: "quick" | "think" | "research";
  isDataAgent: boolean;
  visibleOutputProduct: CHAT.Product;
  uploadedFiles: CHAT.TFile[];
  chatRole: CHAT.ConversationRole | null;
}) {
  const outputStyle = params.isDataAgent
    ? "dataAgent"
    : params.visibleMode === "quick"
      ? "chat"
      : params.visibleOutputProduct.type;

  return {
    message: params.question.trim(),
    outputStyle,
    deepThink:
      outputStyle !== "chat" && outputStyle !== "dataAgent"
        ? params.visibleMode === "research"
        : false,
    files: params.uploadedFiles.length > 0 ? params.uploadedFiles : undefined,
    aiAgentId: outputStyle === "chat" ? params.chatRole?.agentId : undefined,
  };
}
```

- [ ] **Step 3: 抽出上传队列纯函数和副作用 hook**

```ts
// ui/src/components/GeneralInput/uploadQueue.ts
export function markUploadSuccess(
  queue: Record<string, UploadAttachmentState>,
  id: string,
  uploadedFile: CHAT.TFile
) {
  const current = queue[id];
  if (!current) {
    return queue;
  }
  return {
    ...queue,
    [id]: {
      ...current,
      status: "success",
      error: undefined,
      uploadedFile,
    },
  };
}
```

```ts
// ui/src/components/GeneralInput/useAttachmentUploads.ts
export function useAttachmentUploads(sessionId: string) {
  const [attachmentUploads, setAttachmentUploads] = useState<Record<string, UploadAttachmentState>>({});
  const [attachmentOrder, setAttachmentOrder] = useState<string[]>([]);

  const uploadAttachment = useCallback(async (attachmentId: string, file: File) => {
    const uploadedFile = normalizeUploadedFile(
      await agentFileApi.uploadConversationFile(sessionId, file)
    );
    setAttachmentUploads((prev) => markUploadSuccess(prev, attachmentId, uploadedFile));
  }, [sessionId]);

  return {
    attachmentUploads,
    attachmentOrder,
    uploadAttachment,
    setAttachmentUploads,
    setAttachmentOrder,
  };
}
```

- [ ] **Step 4: 提取附件展示组件，收缩主文件 JSX**

```tsx
// ui/src/components/GeneralInput/UploadAttachmentChip.tsx
export default function UploadAttachmentChip(props: {
  attachment: PromptInputAttachmentItem;
  uploadState?: UploadAttachmentState;
  onRemoveAttachment: (id: string) => void;
  onRetryAttachment: (id: string) => void;
}) {
  const isError = props.uploadState?.status === "error";
  return (
    <div className="group flex min-w-0 max-w-full items-center gap-2 rounded-2xl bg-[var(--chat-surface-muted)]/78 px-2.5 py-2 text-[13px] shadow-[var(--shadow-xs)]">
      <div className="min-w-0 flex-1">
        <div className="truncate text-[13px] font-medium text-[var(--chat-text)]">
          {props.attachment.filename || "未命名文件"}
        </div>
        <div className={isError ? "text-[#d14343]" : "text-[var(--chat-text-soft)]"}>
          {resolveUploadStatusLabel(props.uploadState)}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 5: 跑输入组件相关测试**

Run: `cd ui && npm run test -- src/components/GeneralInput/inputMode.test.ts src/components/GeneralInput/uploadQueue.test.ts`

Expected: `PASS`。

- [ ] **Step 6: 跑 lint 和 build，确认导出与类型兼容**

Run: `cd ui && npm run lint && npm run build`

Expected: `PASS`。

- [ ] **Step 7: 提交输入组件拆分**

```bash
git add ui/src/components/GeneralInput/inputMode.ts ui/src/components/GeneralInput/inputMode.test.ts ui/src/components/GeneralInput/uploadQueue.ts ui/src/components/GeneralInput/uploadQueue.test.ts ui/src/components/GeneralInput/useAttachmentUploads.ts ui/src/components/GeneralInput/UploadAttachmentChip.tsx ui/src/components/GeneralInput/index.tsx
git commit -m "refactor: split general input state machine"
```

---

### Task 5: 拆掉 `WorkspaceMRag/index.tsx` 的异步编排

**Files:**
- Create: `ui/src/pages/WorkspaceMRag/knowledgeBaseState.ts`
- Create: `ui/src/pages/WorkspaceMRag/knowledgeBaseState.test.ts`
- Create: `ui/src/pages/WorkspaceMRag/useKnowledgeBaseCatalog.ts`
- Create: `ui/src/pages/WorkspaceMRag/useKnowledgeBaseFiles.ts`
- Create: `ui/src/pages/WorkspaceMRag/useMragQuery.ts`
- Modify: `ui/src/pages/WorkspaceMRag/index.tsx`
- Test: `ui/src/pages/WorkspaceMRag/view.test.tsx`

- [ ] **Step 1: 为知识库选中规则补失败测试**

```ts
import { describe, expect, it } from "vitest";

import { resolveSelectedKnowledgeBaseId, shouldPollKnowledgeBaseFiles } from "./knowledgeBaseState";

describe("knowledgeBaseState", () => {
  it("优先使用 preferredKnowledgeBaseId，缺失时回退当前选中，再回退第一项", () => {
    const knowledgeBases = [{ id: "kb-1" }, { id: "kb-2" }] as Array<{ id: string }>;

    expect(
      resolveSelectedKnowledgeBaseId(knowledgeBases, "kb-1", "kb-2")
    ).toBe("kb-2");
    expect(
      resolveSelectedKnowledgeBaseId(knowledgeBases, "kb-1", "kb-x")
    ).toBe("kb-1");
  });

  it("只有存在处理中文件时才轮询文件列表", () => {
    expect(
      shouldPollKnowledgeBaseFiles([
        { fileStatus: "RUNNING" },
      ] as Array<{ fileStatus: string }>)
    ).toBe(true);
  });
});
```

- [ ] **Step 2: 抽出知识库状态纯函数**

```ts
// ui/src/pages/WorkspaceMRag/knowledgeBaseState.ts
export function resolveSelectedKnowledgeBaseId(
  knowledgeBases: Array<{ id: string }>,
  currentKnowledgeBaseId: string,
  preferredKnowledgeBaseId?: string
) {
  const preferred = preferredKnowledgeBaseId?.trim();
  if (preferred && knowledgeBases.some((item) => item.id === preferred)) {
    return preferred;
  }
  if (currentKnowledgeBaseId && knowledgeBases.some((item) => item.id === currentKnowledgeBaseId)) {
    return currentKnowledgeBaseId;
  }
  return knowledgeBases[0]?.id || "";
}

export function shouldPollKnowledgeBaseFiles(
  files: Array<{ fileStatus?: string }>
) {
  return files.some((file) => file.fileStatus === "RUNNING");
}
```

- [ ] **Step 3: 抽出知识库目录与文件管理 hook**

```ts
// ui/src/pages/WorkspaceMRag/useKnowledgeBaseCatalog.ts
export function useKnowledgeBaseCatalog(toolBaseUrl: string) {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [knowledgeBasesLoading, setKnowledgeBasesLoading] = useState(false);
  const [knowledgeBasesError, setKnowledgeBasesError] = useState("");

  const refreshKnowledgeBases = useCallback(async (options?: { preferredKnowledgeBaseId?: string }) => {
    setKnowledgeBasesLoading(true);
    try {
      const nextKnowledgeBases = await listKnowledgeBases(toolBaseUrl);
      setKnowledgeBases(nextKnowledgeBases);
      setKnowledgeBasesError("");
      return nextKnowledgeBases;
    } catch (error) {
      setKnowledgeBasesError(mapMragError(error));
      setKnowledgeBases([]);
      return [];
    } finally {
      setKnowledgeBasesLoading(false);
    }
  }, [toolBaseUrl]);

  return { knowledgeBases, knowledgeBasesLoading, knowledgeBasesError, refreshKnowledgeBases };
}
```

```ts
// ui/src/pages/WorkspaceMRag/useMragQuery.ts
export function useMragQuery(toolBaseUrl: string, selectedKnowledgeBaseId: string) {
  const queryAbortRef = useRef<AbortController | null>(null);
  const [querying, setQuerying] = useState(false);
  const [queryAnswer, setQueryAnswer] = useState("");
  const [queryError, setQueryError] = useState("");
  const [queryRawChunks, setQueryRawChunks] = useState<unknown[]>([]);

  const submitQuery = useCallback(async (question: string) => {
    const abortController = new AbortController();
    queryAbortRef.current = abortController;
    setQuerying(true);
    setQueryAnswer("");
    setQueryError("");
    setQueryRawChunks([]);
    await streamMragQuery({
      toolBaseUrl,
      kbId: selectedKnowledgeBaseId,
      question,
      signal: abortController.signal,
      onChunk(chunk) {
        if (chunk.content) {
          setQueryAnswer((previous) => previous + chunk.content);
        }
        setQueryRawChunks((previous) => [...previous, chunk.raw].slice(-50));
      },
    });
    setQuerying(false);
  }, [selectedKnowledgeBaseId, toolBaseUrl]);

  return { querying, queryAnswer, queryError, queryRawChunks, submitQuery };
}
```

- [ ] **Step 4: 让 `WorkspaceMRag/index.tsx` 只负责拼装 view**

```tsx
// ui/src/pages/WorkspaceMRag/index.tsx
const catalog = useKnowledgeBaseCatalog(workspaceState.toolBaseUrl);
const filesState = useKnowledgeBaseFiles(workspaceState.toolBaseUrl, selectedKnowledgeBaseId);
const queryState = useMragQuery(workspaceState.toolBaseUrl, selectedKnowledgeBaseId);

return (
  <WorkspaceMRagView
    embedded={embedded}
    knowledgeBases={catalog.knowledgeBases}
    knowledgeBasesLoading={catalog.knowledgeBasesLoading}
    files={filesState.files}
    filesLoading={filesState.filesLoading}
    querying={queryState.querying}
    queryAnswer={queryState.queryAnswer}
    queryError={queryState.queryError}
  />
);
```

- [ ] **Step 5: 跑 MRag 相关测试与构建**

Run: `cd ui && npm run test -- src/pages/WorkspaceMRag/knowledgeBaseState.test.ts src/pages/WorkspaceMRag/view.test.tsx && npm run build`

Expected: `PASS`。

- [ ] **Step 6: 提交 MRag 工作台拆分**

```bash
git add ui/src/pages/WorkspaceMRag/knowledgeBaseState.ts ui/src/pages/WorkspaceMRag/knowledgeBaseState.test.ts ui/src/pages/WorkspaceMRag/useKnowledgeBaseCatalog.ts ui/src/pages/WorkspaceMRag/useKnowledgeBaseFiles.ts ui/src/pages/WorkspaceMRag/useMragQuery.ts ui/src/pages/WorkspaceMRag/index.tsx ui/src/pages/WorkspaceMRag/view.test.tsx
git commit -m "refactor: split mrag workspace orchestration"
```

---

### Task 6: 清理 `DataChat` 遗留 JS 和可变配置写法

**Files:**
- Delete: `ui/src/components/DataChat/ChartUtils.js`
- Create: `ui/src/components/DataChat/chartPresets.ts`
- Create: `ui/src/components/DataChat/chartConfig.ts`
- Create: `ui/src/components/DataChat/querySummary.ts`
- Create: `ui/src/components/DataChat/chartConfig.test.ts`
- Modify: `ui/src/components/DataChat/index.tsx`

- [ ] **Step 1: 为图表配置纯函数补失败测试**

```ts
import { describe, expect, it } from "vitest";

import { buildChartConfig } from "./chartConfig";

describe("chartConfig", () => {
  it("构建图表配置时不应修改输入对象", () => {
    const input = {
      chartSuggest: "line",
      dimCols: ["dt"],
      measureCols: ["gmv"],
      columnList: [],
      dataList: [{ dt: "2026-05-01", gmv: 10 }],
    };
    const frozen = Object.freeze({ ...input });

    const result = buildChartConfig(frozen as typeof input);

    expect(result.chartType).toBe("line");
    expect(frozen.chartSuggest).toBe("line");
  });
});
```

- [ ] **Step 2: 把常量模板和转换逻辑改成 TypeScript 纯函数**

```ts
// ui/src/components/DataChat/chartConfig.ts
import { defaultChartPresets } from "./chartPresets";

export function buildChartConfig(
  chartCfg: Record<string, unknown>
) {
  const nextConfig = {
    ...chartCfg,
  };
  const chartType = String(nextConfig.chartSuggest || "table");

  if (defaultChartPresets.chartTypes.includes(chartType)) {
    return {
      chartType,
      option: initChartOption(nextConfig, chartType),
    };
  }

  if (chartType === "table") {
    return {
      chartType,
      ...initTable(nextConfig),
    };
  }

  return {
    chartType,
    dataList: nextConfig.dataList || [],
    columnList: nextConfig.columnList || [],
  };
}
```

- [ ] **Step 3: 抽出查询条件展示文案**

```ts
// ui/src/components/DataChat/querySummary.ts
export function buildQuerySummary(chartCfg: Record<string, any>) {
  return {
    dims: (chartCfg.dimCols || []).map((item: string) => item),
    measures: (chartCfg.measureCols || []).map((item: string) => item),
    filters: (chartCfg.filters || []).map((item: { name: string; optName: string; val?: string }) => {
      const value = item.val?.replace(/^\%+/g, "").replace(/\%+$/g, "") || "";
      return `${item.name}(${item.optName}${value})`;
    }),
    formula: chartCfg.overwriteCalc || "",
  };
}
```

- [ ] **Step 4: 更新 `DataChat/index.tsx`，去掉对 `chartCfg` 的原地修改**

```ts
// ui/src/components/DataChat/index.tsx
const transConfig = useMemo(() => {
  return buildChartConfig({
    ...chartCfg,
    chartSuggest: currentType,
  });
}, [chartCfg, currentType]);
```

- [ ] **Step 5: 跑 DataChat 相关测试**

Run: `cd ui && npm run test -- src/components/DataChat/chartConfig.test.ts`

Expected: `PASS`。

- [ ] **Step 6: 提交 DataChat 遗留治理**

```bash
git add ui/src/components/DataChat/chartPresets.ts ui/src/components/DataChat/chartConfig.ts ui/src/components/DataChat/querySummary.ts ui/src/components/DataChat/chartConfig.test.ts ui/src/components/DataChat/index.tsx
git rm ui/src/components/DataChat/ChartUtils.js
git commit -m "refactor: type data chat config builders"
```

---

### Task 7: 收敛 `ActionPanel` / `FilePreview` 的渲染分发

**Files:**
- Create: `ui/src/components/ActionPanel/panelResolver.ts`
- Create: `ui/src/components/ActionView/filePreviewModel.ts`
- Create: `ui/src/components/ActionView/filePreviewModel.test.ts`
- Modify: `ui/src/components/ActionPanel/ActionPanel.tsx`
- Modify: `ui/src/components/ActionView/FilePreview.tsx`
- Test: `ui/src/components/ActionPanel/fileContentRender.test.ts`

- [ ] **Step 1: 为预览标题派生补失败测试**

```ts
import { describe, expect, it } from "vitest";

import { resolvePreviewTitle } from "./filePreviewModel";

describe("filePreviewModel", () => {
  it("deep_search report 阶段应优先显示查询标题", () => {
    expect(
      resolvePreviewTitle({
        messageType: "deep_search",
        resultMap: {
          messageType: "report",
          query: "帮我总结 5 月投放效果",
        },
      } as unknown as CHAT.Task)
    ).toContain("帮我总结 5 月投放效果");
  });
});
```

- [ ] **Step 2: 抽出 renderer 选择和预览标题纯函数**

```ts
// ui/src/components/ActionPanel/panelResolver.ts
export function resolvePanelView(taskItem?: PanelItemType) {
  if (!taskItem) {
    return { type: "empty" } as const;
  }
  if (taskItem.messageType === "deep_search") {
    return { type: "search" } as const;
  }
  if (taskItem.messageType === "html" || taskItem.messageType === "ppt") {
    return { type: "html" } as const;
  }
  return { type: "markdown" } as const;
}
```

```ts
// ui/src/components/ActionView/filePreviewModel.ts
export function resolvePreviewTitle(taskItem?: CHAT.Task) {
  if (!taskItem) {
    return "";
  }
  if (taskItem.messageType === "deep_search") {
    return String(taskItem.resultMap?.query || taskItem.resultMap?.searchResult?.query || "deep_search");
  }
  return String(taskItem.messageType || "");
}
```

- [ ] **Step 3: 让主组件只负责渲染，不再承载分发表**

```ts
// ui/src/components/ActionPanel/ActionPanel.tsx
const panelView = useMemo(() => resolvePanelView(taskItem), [taskItem]);

if (panelView.type === "html") {
  return <HTMLRenderer htmlUrl={htmlUrl} className="h-full" />;
}
```

```ts
// ui/src/components/ActionView/FilePreview.tsx
const title = useMemo(() => resolvePreviewTitle(taskItem), [taskItem]);
```

- [ ] **Step 4: 跑预览与渲染相关测试**

Run: `cd ui && npm run test -- src/components/ActionPanel/fileContentRender.test.ts src/components/ActionView/filePreviewModel.test.ts`

Expected: `PASS`。

- [ ] **Step 5: 提交预览渲染收敛**

```bash
git add ui/src/components/ActionPanel/panelResolver.ts ui/src/components/ActionView/filePreviewModel.ts ui/src/components/ActionView/filePreviewModel.test.ts ui/src/components/ActionPanel/ActionPanel.tsx ui/src/components/ActionView/FilePreview.tsx ui/src/components/ActionPanel/fileContentRender.test.ts
git commit -m "refactor: extract preview and panel resolvers"
```

---

### Task 8: 完整回归并更新前端维护约定

**Files:**
- Modify: `ui/CLAUDE.md`
- Modify: `ui/docs/superpowers/plans/2026-05-05-ui-fat-code-simplification-plan.md`
- Modify: `ui/docs/superpowers/plans/2026-05-05-ui-frontend-debt-governance-plan.md`

- [ ] **Step 1: 跑第一轮完整回归**

Run: `cd ui && npm run test && npm run lint && npm run build`

Expected: `PASS`，若存在历史遗留失败，需明确记录不是本轮引入。

- [ ] **Step 2: 在 `ui/CLAUDE.md` 中记录新的维护边界**

```md
## Frontend Maintenance Notes

- `Home/index.tsx` 只保留入口装配；会话 bootstrap 和近期会话逻辑进入 `pages/Home` 子模块。
- `GeneralInput/index.tsx` 只保留输入编排；模式推导与上传状态机进入 `components/GeneralInput` 子模块。
- `WorkspaceMRag/index.tsx` 只保留 view 装配；知识库目录、文件管理、问答流分别进入独立 hook。
- `DataChat` 不再新增 JS 配置工具；图表配置一律走 TypeScript 纯函数。
- `ActionPanel` 和 `FilePreview` 的 renderer / title / navigation 派生统一走 resolver/model 纯函数。
```

- [ ] **Step 3: 回写计划收尾说明，声明新旧计划关系**

```md
## Relationship To 2026-05-05-ui-fat-code-simplification-plan

- 旧计划聚焦聊天主链路第一阶段拆分。
- 本计划聚焦剩余高维护成本热点，且依赖旧计划的聊天核心基线先稳定。
- 执行顺序以本计划 Task 1 的 Phase Gate 为准。
```

- [ ] **Step 4: 提交收尾文档**

```bash
git add ui/CLAUDE.md ui/docs/superpowers/plans/2026-05-05-ui-fat-code-simplification-plan.md ui/docs/superpowers/plans/2026-05-05-ui-frontend-debt-governance-plan.md
git commit -m "docs: record frontend maintenance boundaries"
```

---

## Self-Review

### Spec coverage

- 覆盖了当前最高维护成本的 6 个区域：`useConversationStream` / `utils/chat.ts`、`Home`、`GeneralInput`、`WorkspaceMRag`、`DataChat`、`ActionPanel` / `FilePreview`。
- 每个区域都给出了文件边界、保护性测试、最小实现方向和验证命令。
- 特别补上了当前在途聊天核心改动的 Phase Gate，避免在未收口的 diff 上继续叠债。

### Placeholder scan

- 本计划未使用 `TODO`、`TBD`、`后续再做`、`类似 Task N` 这类占位写法。
- 每个任务都提供了明确的文件路径、测试命令和提交粒度。
- 代码步骤都给出了具体函数名或组件名，没有留空接口。

### Type consistency

- 首页相关统一使用 `homeState.ts`、`useRecentSessions.ts`、`useConversationBootstrap.ts`。
- 输入组件相关统一使用 `inputMode.ts`、`uploadQueue.ts`、`useAttachmentUploads.ts`。
- MRag 相关统一使用 `knowledgeBaseState.ts`、`useKnowledgeBaseCatalog.ts`、`useKnowledgeBaseFiles.ts`、`useMragQuery.ts`。
- 预览渲染相关统一使用 `panelResolver.ts` 与 `filePreviewModel.ts`。

### Notes

- 本计划默认在 `ui/` 子项目内执行，优先按 Task 粒度提交，不建议一次性跨多个波次混做。
- 如果 Task 2 的聊天核心收口过程中发现现有未提交改动和计划边界冲突，应先暂停 Wave 1 之后的任务，先把聊天主链路恢复到可构建可测试状态。

## Relationship To 2026-05-05-ui-fat-code-simplification-plan

- 旧计划聚焦聊天主链路第一阶段拆分。
- 本计划聚焦剩余高维护成本热点，且依赖旧计划的聊天核心基线先稳定。
- 执行顺序以本计划 Task 1 的 Phase Gate 为准。
