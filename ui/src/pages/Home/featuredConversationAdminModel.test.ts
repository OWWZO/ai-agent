import { describe, expect, it } from "vitest";

import type { ConversationSessionItem } from "@/services/agentConversation";
import type { FeaturedConversationAdminRecord } from "@/services/featuredConversationAdmin";

import {
  buildFeaturedConversationFormState,
  canFeatureConversationSession,
  parseFeaturedConversationTags,
  toFeaturedConversationUpsertPayload,
  validateFeaturedConversationForm,
} from "./featuredConversationAdminModel";

const session: ConversationSessionItem = {
  sessionId: "session-001",
  title: "原始会话标题",
  status: "SUCCESS",
  latestQueryText: "请帮我总结这个调研结果",
  runCount: 2,
  finishedRunCount: 2,
  failedRunCount: 0,
  startedAt: "2026-07-06T10:00:00",
  lastActiveAt: "2026-07-06T11:00:00",
};

const existingRecord: FeaturedConversationAdminRecord = {
  featuredId: "featured-session-001",
  sessionId: "session-001",
  title: "精品标题",
  summary: "精品摘要",
  coverUrl: "https://file.example.com/cover.png",
  tags: ["研究", "案例"],
  sortOrder: 66,
  status: "ONLINE",
  publishedAt: "2026-07-06T12:00:00",
  updatedAt: "2026-07-06T12:10:00",
};

describe("featuredConversationAdminModel", () => {
  it("builds an edit form from the existing featured record", () => {
    const form = buildFeaturedConversationFormState({
      session,
      existingRecord,
      operator: "admin-ui",
    });

    expect(form.sessionId).toBe("session-001");
    expect(form.title).toBe("精品标题");
    expect(form.summary).toBe("精品摘要");
    expect(form.tagsText).toBe("研究, 案例");
    expect(form.sortOrder).toBe("66");
    expect(form.operator).toBe("admin-ui");
  });

  it("parses comma-separated tags and removes blanks", () => {
    expect(parseFeaturedConversationTags("研究, 案例， 复盘 ,, ")).toEqual([
      "研究",
      "案例",
      "复盘",
    ]);
  });

  it("validates required fields before submit", () => {
    const form = buildFeaturedConversationFormState({
      session,
      operator: "",
    });

    expect(
      validateFeaturedConversationForm({
        ...form,
        operator: "",
      })
    ).toBe("请填写操作人");
  });

  it("converts the form to the admin upsert payload", () => {
    const form = buildFeaturedConversationFormState({
      session,
      existingRecord,
      operator: "admin-ui",
    });

    const payload = toFeaturedConversationUpsertPayload(form, existingRecord);

    expect(payload).toEqual({
      featuredId: "featured-session-001",
      sessionId: "session-001",
      title: "精品标题",
      summary: "精品摘要",
      coverUrl: "https://file.example.com/cover.png",
      tags: ["研究", "案例"],
      sortOrder: 66,
      operator: "admin-ui",
    });
  });

  it("blocks empty draft sessions from being marked as featured", () => {
    expect(
      canFeatureConversationSession({
        ...session,
        runCount: 0,
      })
    ).toBe(false);
    expect(canFeatureConversationSession(session)).toBe(true);
  });
});
