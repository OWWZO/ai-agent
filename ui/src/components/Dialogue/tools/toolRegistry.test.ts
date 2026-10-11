import { renderToStaticMarkup } from "react-dom/server";
import { createElement } from "react";
import { describe, expect, it, vi } from "vitest";
import { resolveToolRendererKind } from "./toolRegistry";
import { buildEditDiffCode, editDiffStats, formatEditDiffChip } from "./toolDiff";
import { normalizeToolName } from "./toolMeta";
import { resolveTaskToolOutput } from "./toolTaskAdapter";
import { GenericToolCall } from "./GenericToolCall";
import { EditToolCall } from "./EditToolCall";
import { ReadToolCall } from "./ReadToolCall";
import { McpToolCall } from "./McpToolCall";

function toolTask(
  partial: Partial<CHAT.Task> & { resultMap?: Record<string, unknown> }
): CHAT.Task {
  return {
    id: "t1",
    messageId: "t1",
    messageType: "tool_result",
    messageTime: "1",
    requestId: "r1",
    finish: true,
    isFinal: true,
    resultMap: {
      status: "success",
      isFinal: true,
      ...(partial.resultMap || {}),
    },
    ...partial,
  } as CHAT.Task;
}

/**
 * 按真实 payload 形态构造任务。
 *
 * 单独抽出来是为了让调用点保持单行 —— 直接写 `toolTask({ resultMap: {...} })`
 * 会同时踩 `object-curly-newline` 与 `object-property-newline`（本仓库既有规则冲突）。
 */
function taskOf(
  toolName: string,
  input?: Record<string, unknown>,
  resultMapExtra?: Record<string, unknown>
): CHAT.Task {
  const resultMap = {
    toolName,
    input,
    ...(resultMapExtra || {})
  };
  return toolTask({ resultMap });
}

describe("toolRegistry", () => {
  it("routes edit/write/multi_edit to edit renderer", () => {
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: { toolName: "Edit", input: { path: "a.ts" } },
        })
      )
    ).toBe("edit");
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: { toolName: "Write", input: { path: "b.ts" } },
        })
      )
    ).toBe("edit");
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: { toolName: "MultiEdit", input: { path: "c.ts" } },
        })
      )
    ).toBe("edit");
  });

  it("routes read / read_file to read renderer", () => {
    expect(resolveToolRendererKind(taskOf("Read", { path: "a.ts" }))).toBe("read");
    expect(resolveToolRendererKind(taskOf("read_file", { file_path: "a.ts" }))).toBe("read");
  });

  it("routes mcp__{server}__{tool} to the mcp renderer", () => {
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: {
            toolName: "mcp__filesystem__read_file",
            input: { path: "a.ts" }
          },
        })
      )
    ).toBe("mcp");
    // 保留大小写：不能被 normalizeToolName 抹掉后落回 generic
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: {
            toolName: "mcp__GitHubServer__listIssues",
            input: { repo: "a/b" }
          },
        })
      )
    ).toBe("mcp");
    // 非 FQ 的 mcp 前缀不应误判
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: {
            toolName: "mcp_health_check",
            input: {}
          },
        })
      )
    ).toBe("generic");
  });

  it("routes Agent / task aliases to agent renderer", () => {
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: {
            toolName: "Agent",
            input: { prompt: "x", subagent_type: "Explore" },
          },
        })
      )
    ).toBe("agent");
    expect(
      resolveToolRendererKind(
        toolTask({
          toolResult: {
            toolName: "Agent",
            toolResult: "status=completed\n\nok",
            toolParam: { query: "x" },
          },
        })
      )
    ).toBe("agent");
  });

  it("routes ask_user_question to askuser renderer", () => {
    expect(
      resolveToolRendererKind(
        toolTask({
          messageType: "ask_user_question",
          resultMap: { toolName: "AskUserQuestion" },
        })
      )
    ).toBe("askuser");
  });

  it("defaults to generic", () => {
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: { toolName: "Bash", input: { command: "ls" } },
        })
      )
    ).toBe("generic");
  });

  it("reads nested tool output for a child tool result", () => {
    const task = toolTask({
      resultMap: {
        toolResult: {
          toolName: "workspace_grep",
          toolResult: "found Controller",
        },
      },
    });

    expect(resolveTaskToolOutput(task)).toContain("found Controller");
  });

  it("keeps generic tool results expandable inline instead of opening workspace", () => {
    const html = renderToStaticMarkup(
      createElement(GenericToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "workspace_grep",
            input: { pattern: "Controller" },
            toolResult: "found Controller",
          },
        }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined,
      })
    );

    expect(html).toContain('aria-expanded="false"');
    expect(html).toContain("参数");
    expect(html).toContain("found Controller");
  });

  it("renders canvas_publish as a previewable tool card", () => {
    const html = renderToStaticMarkup(
      createElement(GenericToolCall, {
        tool: toolTask({
          messageType: "tool_call",
          resultMap: {
            toolName: "canvas_publish",
            status: "success",
            input: { html_path: "pages/index.html" },
            fileInfo: [
              {
                fileName: "index.html",
                relativePath: "pages/index.html",
                domainUrl: "http://x/preview/pages/index.html",
                ossUrl: "http://x/download/pages/index.html",
                fileSize: 12,
              },
            ],
          },
        }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined,
      })
    );

    expect(html).toContain("发布画布");
    expect(html).toContain("pages/index.html");
  });

  it("read 卡的文件名 chip 可点击打开右侧文件内容", () => {
    const onOpenToolDiff = vi.fn();
    const html = renderToStaticMarkup(
      createElement(ReadToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "Read",
            input: { path: "src/app/main.ts" },
            toolResult: "1\tconst a = 1;",
          },
        }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined,
        onOpenToolDiff,
      })
    );

    // 正常读取：chip 是 button（可点击打开右侧）
    expect(html).toContain("main.ts");
    expect(html).toContain("<button");
    // ZCode read 渲染器语义：正常读取不提供内联展开箭头
    expect(html).not.toContain("kimi-tool-row-toggle");
    // 正文默认折叠（body 带 inert）
    expect(html).toContain("kimi-tool-row-body");
  });

  it("read 出错时才允许内联展开看原始输出", () => {
    const html = renderToStaticMarkup(
      createElement(ReadToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "Read",
            status: "failed",
            input: { path: "missing.ts" },
            errorMsg: "ENOENT: no such file",
          },
        }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined,
      })
    );

    // 出错时才提供内联展开箭头，且渲染真实错误输出
    expect(html).toContain("kimi-tool-row-toggle");
    expect(html).toContain("ENOENT: no such file");
  });

  it("keeps edit input and output inline", () => {
    const html = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "Edit",
            input: { path: "a.ts", old_string: "old", new_string: "new" },
            toolResult: "updated a.ts",
          },
        }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined,
      })
    );

    expect(html).toContain('aria-expanded="false"');
    expect(html).toContain("a.ts");
    expect(html).toContain("updated a.ts");
  });

  it("pretty prints JSON input and output inside the tool row", () => {
    const html = renderToStaticMarkup(
      createElement(GenericToolCall, {
        tool: toolTask({
          resultMap: {
            // 用非 execute 工具：Bash 已改走 `$ 命令` + 吸底输出（见 Stage 5 用例），
            // 这里的意图是验证通用卡对 JSON 入参 / 出参的美化打印。
            toolName: "WebFetch",
            input: {
              url: "https://example.test",
              options: { hidden: false },
            },
            toolResult: JSON.stringify({
              ok: true,
              files: ["a.ts", "b.ts"],
            }),
          },
        }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined,
      })
    );

    expect(html.match(/data-testid="tool-json-block"/g)).toHaveLength(2);
    expect(html).toContain("files");
    expect(html).toContain("a.ts");
  });
});

describe("editDiffStats chip", () => {
  it("computes +A −B from old_string/new_string", () => {
    const arg = JSON.stringify({
      path: "a.ts",
      old_string: "a\nb\nc",
      new_string: "a\nx\nc\nd",
    });
    const stats = editDiffStats("Edit", arg);
    expect(stats).toEqual({ added: 2, removed: 1 });
    expect(formatEditDiffChip("Edit", arg)).toBe("+2 −1");
  });

  it("counts write content as added lines", () => {
    const arg = JSON.stringify({
      path: "new.ts",
      content: "one\ntwo\nthree",
    });
    expect(formatEditDiffChip("Write", arg)).toBe("+3 −0");
  });
});

describe("Stage 5 · 卡内展开区", () => {
  it("execute 卡渲染 `$ 命令` 与吸底输出容器", () => {
    const html = renderToStaticMarkup(
      createElement(GenericToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "Bash",
            input: { command: "npm run build" },
            toolResult: "build ok"
          },
        }),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined,
      })
    );

    expect(html).toContain("kimi-tool-execute");
    expect(html).toContain("kimi-tool-execute-prompt");
    expect(html).toContain("npm run build");
    // 吸底容器与 following 标记由 ExecuteOutput 提供
    expect(html).toContain('data-testid="bash-output-scroll"');
    expect(html).toContain('data-following="true"');
  });

  it("mcp 卡渲染「调用参数 / 结果」两段并带复制与换行开关", () => {
    const html = renderToStaticMarkup(
      createElement(McpToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "mcp__filesystem__read_file",
            input: { path: "src/a.ts" },
            toolResult: "file contents"
          },
        }),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined,
      })
    );

    expect(html).toContain("调用参数");
    expect(html).toContain("结果");
    expect(html).toContain("自动换行");
    expect(html).toContain("复制结果");
    expect(html).toContain('data-testid="mcp-result-surface"');
    // 摘要行展示 server 段，工具段作主标题
    expect(html).toContain("read_file");
    expect(html).toContain("filesystem");
  });

  it("fallback：无入参预览且无输出时退化为原始 JSON", () => {
    const html = renderToStaticMarkup(
      createElement(GenericToolCall, {
        tool: taskOf("SomeUnknownTool"),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined,
      })
    );

    expect(html).toContain("原始数据");
    expect(html).toContain('data-testid="tool-json-block"');
  });
});

describe("Reactor-agent 真实工具名（workspace_* 前缀）", () => {
  it("workspace_read / workspace_write / workspace_edit 各自命中专用渲染器", () => {
    expect(resolveToolRendererKind(taskOf("workspace_read", { path: "src/a.ts" }))).toBe("read");
    expect(resolveToolRendererKind(taskOf("workspace_write", { path: "src/a.ts" }))).toBe("edit");
    expect(resolveToolRendererKind(taskOf("workspace_edit", { path: "src/a.ts" }))).toBe("edit");
  });

  it("workspace_* 归一化后可被 toolDiff 直接消费（patch 能构造出来）", () => {
    const arg = JSON.stringify({
      path: "src/a.ts",
      old_string: "one\ntwo",
      new_string: "one\ntwo!"
    });
    expect(normalizeToolName("workspace_edit")).toBe("edit");
    expect(
      buildEditDiffCode({
        name: "workspace_edit",
        arg
      })
    ).toContain("-two");
    expect(
      editDiffStats("workspace_edit", arg)
    ).toEqual({
      added: 1,
      removed: 1
    });
  });

  it("workspace_read 的 chip 可点开右侧文件内容", () => {
    const onOpenToolDiff = vi.fn();
    const html = renderToStaticMarkup(
      createElement(ReadToolCall, {
        tool: taskOf("workspace_read", { path: "src/app/main.ts" }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined,
        onOpenToolDiff
      })
    );
    expect(html).toContain("main.ts");
    expect(html).toContain("<button");
  });

  it("workspace_edit 的 chip 可点开右侧 diff", () => {
    const onOpenToolDiff = vi.fn();
    const html = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: taskOf("workspace_edit", { path: "src/app/main.ts" }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined,
        onOpenToolDiff
      })
    );
    expect(html).toContain("main.ts");
    expect(html).toContain("<button");
  });

  it("workspace_glob / grep / list 命中各自图标分流（不落 generic 的 Wrench）", () => {
    expect(normalizeToolName("workspace_glob")).toBe("glob");
    expect(normalizeToolName("workspace_grep")).toBe("grep");
    expect(normalizeToolName("workspace_list")).toBe("ls");
  });

  it("code_execution 归一为 bash，走 execute 卡", () => {
    expect(normalizeToolName("code_execution")).toBe("bash");
    const html = renderToStaticMarkup(
      createElement(GenericToolCall, {
        tool: taskOf("code_execution", { source: "print(1)" }, { toolResult: "1" }),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined
      })
    );
    expect(html).toContain("kimi-tool-execute");
    expect(html).toContain("print(1)");
  });
});

describe("write 工具也有可点 chip（回归）", () => {
  it("workspace_write 渲染文件名 chip 且带 +N −0", () => {
    const onOpenToolDiff = vi.fn();
    const html = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: taskOf("workspace_write", {
          path: "src/app/new.ts",
          content: "one\ntwo"
        }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined,
        onOpenToolDiff
      })
    );

    // 文件名 chip 必须是 button（可点开右侧）
    expect(html).toContain("new.ts");
    expect(html).toContain("<button");
    // 摘要行 chip 走翻页节点
    expect(html).toContain("kimi-tool-diff");
  });

  it("没有 onOpenToolDiff 时退化为不可点的 span（不报错）", () => {
    const html = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: taskOf("workspace_write", {
          path: "src/app/new.ts",
          content: "x"
        }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined
      })
    );
    expect(html).toContain("new.ts");
    expect(html).not.toContain('<button type="button" title="src/app/new.ts"');
  });
});
