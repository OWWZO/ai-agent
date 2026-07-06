import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import type { ConversationSessionItem } from "@/services/agentConversation";

import ConversationSessionActionMenu from "./ConversationSessionActionMenu";

const session: ConversationSessionItem = {
  sessionId: "session-001",
  title: "调研会话",
  status: "SUCCESS",
  latestQueryText: "请整理一份总结",
  runCount: 1,
  finishedRunCount: 1,
  failedRunCount: 0,
  startedAt: "2026-07-06T10:00:00",
  lastActiveAt: "2026-07-06T11:00:00",
};

describe("ConversationSessionActionMenu", () => {
  it("renders the featured conversation management action", () => {
    const html = renderToStaticMarkup(
      <ConversationSessionActionMenu
        session={session}
        canManageFeatured={true}
        onDelete={() => {}}
        onManageFeatured={() => {}}
        onPin={() => {}}
      />
    );

    expect(html).toContain("设为精品");
  });
});
