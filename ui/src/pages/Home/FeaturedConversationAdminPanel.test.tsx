import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import type { ConversationSessionItem } from "@/services/agentConversation";
import type { FeaturedConversationAdminRecord } from "@/services/featuredConversationAdmin";

import FeaturedConversationAdminPanel from "./FeaturedConversationAdminPanel";
import { buildFeaturedConversationFormState } from "./featuredConversationAdminModel";

const session: ConversationSessionItem = {
  sessionId: "session-001",
  title: "调研会话",
  status: "SUCCESS",
  latestQueryText: "请整理一份总结",
  runCount: 3,
  finishedRunCount: 3,
  failedRunCount: 0,
  startedAt: "2026-07-06T10:00:00",
  lastActiveAt: "2026-07-06T11:00:00",
};

const onlineRecord: FeaturedConversationAdminRecord = {
  featuredId: "featured-session-001",
  sessionId: "session-001",
  title: "精品调研案例",
  summary: "适合公开展示",
  tags: ["调研", "案例"],
  coverUrl: "",
  sortOrder: 100,
  status: "ONLINE",
  publishedAt: "2026-07-06T12:00:00",
  updatedAt: "2026-07-06T12:10:00",
};

describe("FeaturedConversationAdminPanel", () => {
  it("renders create-state actions for a new featured conversation draft", () => {
    const html = renderToStaticMarkup(
      <FeaturedConversationAdminPanel
        session={session}
        form={buildFeaturedConversationFormState({
          session,
          operator: "admin-ui",
        })}
        record={null}
        loading={false}
        submitting={false}
        onChange={() => {}}
        onClose={() => {}}
        onSaveDraft={() => {}}
        onPublish={() => {}}
      />
    );

    expect(html).toContain("设为精品");
    expect(html).toContain("session-001");
    expect(html).toContain("保存草稿");
    expect(html).toContain("创建并上线");
  });

  it("renders online-state actions for an existing featured conversation", () => {
    const html = renderToStaticMarkup(
      <FeaturedConversationAdminPanel
        session={session}
        form={buildFeaturedConversationFormState({
          session,
          existingRecord: onlineRecord,
          operator: "admin-ui",
        })}
        record={onlineRecord}
        loading={false}
        submitting={false}
        onChange={() => {}}
        onClose={() => {}}
        onSaveDraft={() => {}}
        onPublish={() => {}}
      />
    );

    expect(html).toContain("当前状态");
    expect(html).toContain("已上线");
    expect(html).toContain("保存修改");
    expect(html).toContain("下线");
  });
});
