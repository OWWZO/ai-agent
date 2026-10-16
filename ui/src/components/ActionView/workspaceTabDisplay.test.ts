import { describe, expect, it } from "vitest";
import { resolveWorkspaceTabDisplay } from "./workspaceTabDisplay";
import { workspaceTabKey, type WorkspaceTab } from "./workspaceFiles";

function fileTab(): WorkspaceTab {
  return {
    name: "report.pdf",
    type: "pdf",
    url: "https://x/report.pdf",
    relativePath: "out/report.pdf",
  } as unknown as WorkspaceTab;
}

function patchTab(): WorkspaceTab {
  return {
    kind: "source",
    key: "patch:src/a.ts:h1",
    source: {
      type: "patch",
      title: "a.ts",
      path: "src/a.ts",
      patch: "@@ -1 +1,3 @@\n-old line\n+new line\n+another line",
    },
  } as unknown as WorkspaceTab;
}

function agentTab(): WorkspaceTab {
  return {
    kind: "agent",
    key: "agent:tc-1",
    toolCallId: "tc-1",
    title: "继续完善 HTML 页面",
    subagentType: "general-purpose",
    fallbackTool: {},
    fallbackChat: {},
  } as unknown as WorkspaceTab;
}

function directoryTab(): WorkspaceTab {
  return {
    kind: "agent-directory",
    key: "agent-directory"
  } as unknown as WorkspaceTab;
}

function taskTab(): WorkspaceTab {
  return {
    kind: "task",
    key: "task:tc-9",
    taskKey: "tc-9",
    title: "读取 package.json",
    fallbackTask: {
      id: "tc-9",
      messageType: "tool_result",
      resultMap: { toolName: "Read" },
    },
  } as unknown as WorkspaceTab;
}

describe("resolveWorkspaceTabDisplay", () => {
  it("文件 tab：标题取文件名，图标是类型角标，类型词是扩展名", () => {
    const display = resolveWorkspaceTabDisplay(fileTab());
    expect(display.title).toBe("report.pdf");
    expect(display.icon).toEqual({
      kind: "badge",
      badge: "P"
    });
    expect(display.typeHint).toBe("pdf");
    expect(display.hint).toBe("out/report.pdf");
    expect(display.diffBadge).toBe("");
  });

  it("patch tab：图标按被改文件类型取，变更量单独出 `+A −B`", () => {
    const display = resolveWorkspaceTabDisplay(patchTab());
    expect(display.title).toBe("a.ts");
    // a.ts → code 角标，而不是「补丁」专用图标（对齐 ZCode 的 FileDisplayIcon + diffBadgeLabel）
    expect(display.icon).toEqual({
      kind: "badge",
      badge: "</>"
    });
    expect(display.diffBadge).toBe("+2 −1");
    expect(display.typeHint).toBe("补丁");
    expect(display.hint).toBe("src/a.ts");
  });

  it("子智能体 tab：Bot 图标 + 类型词「子智能体」，副标题是 agent 类型", () => {
    const display = resolveWorkspaceTabDisplay(agentTab());
    expect(display.title).toBe("继续完善 HTML 页面");
    expect(display.icon).toEqual({
      kind: "glyph",
      glyph: "bot"
    });
    expect(display.typeHint).toBe("子智能体");
    expect(display.hint).toBe("general-purpose");
  });

  it("目录 tab：ListTree 图标 + 固定标题", () => {
    const display = resolveWorkspaceTabDisplay(directoryTab());
    expect(display.title).toBe("子智能体目录");
    expect(display.icon).toEqual({
      kind: "glyph",
      glyph: "list-tree"
    });
    expect(display.typeHint).toBe("目录");
  });

  it("任务预览 tab：activity 图标 + 类型词「任务」，工具名进 hint", () => {
    // 关键回归：task tab 没有文件身份，不能被当成「没有扩展名的文件」渲染成
    // 文件角标 + 「未命名文件」。
    const display = resolveWorkspaceTabDisplay(taskTab());
    expect(display.title).toBe("读取 package.json");
    expect(display.icon).toEqual({
      kind: "glyph",
      glyph: "activity"
    });
    expect(display.typeHint).toBe("任务");
    expect(display.hint).toBe("Read");
    expect(display.diffBadge).toBe("");
  });

  it("任务 tab 缺标题时兜底为「任务预览」，不会渲染空标题", () => {
    const display = resolveWorkspaceTabDisplay({
      kind: "task",
      key: "task:tc-1",
      taskKey: "tc-1",
      title: "",
      fallbackTask: {
        id: "tc-1",
        messageType: "tool_result"
      },
    } as unknown as WorkspaceTab);
    expect(display.title).toBe("任务预览");
    expect(display.typeHint).toBe("任务");
  });

  it("key 与 workspaceTabKey 一致 —— 条上与浮层用同一个身份", () => {
    for (const tab of [
      fileTab(),
      patchTab(),
      agentTab(),
      directoryTab(),
      taskTab(),
    ]) {
      expect(resolveWorkspaceTabDisplay(tab).key).toBe(workspaceTabKey(tab));
    }
  });

  it("标题缺失时有兜底文案，不会渲染出空标题", () => {
    const display = resolveWorkspaceTabDisplay({name: "",} as unknown as WorkspaceTab);
    expect(display.title).toBe("未命名文件");
  });
});
