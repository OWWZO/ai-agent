import { renderToStaticMarkup } from "react-dom/server";
import { createElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resolveToolRendererKind } from "./toolRegistry";
import { buildEditDiffCode, editDiffStats, formatEditDiffChip } from "./toolDiff";
import { normalizeToolName } from "./toolMeta";
import { resolveTaskToolOutput } from "./toolTaskAdapter";
import { resetToolCardOpenState } from "./toolCardOpenState";
import { GenericToolCall } from "./GenericToolCall";
import { EditToolCall } from "./EditToolCall";
import { ReadToolCall } from "./ReadToolCall";
import { McpToolCall } from "./McpToolCall";

// 折叠状态是模块级持久表（对齐 ZCode toolLayoutOpenState）。
// 用例里所有工具都复用同一个 id，必须逐条清空，否则前一条的展开态会漏进下一条。
beforeEach(() => {
  resetToolCardOpenState();
});

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
        // 展开体的内容只在展开后才进 DOM（对齐 ZCode ToolLayout「未展开不渲染 children」），
        // 所以这里必须显式展开才能断言段标题与结果文本。
        defaultExpanded: true,
        changeActiveChat: () => undefined,
      })
    );

    expect(html).toContain('aria-expanded="true"');
    // 段标题对齐 ZCode `ToolInput` / `ToolOutput`：Parameters / Result
    expect(html).toContain("Parameters");
    expect(html).toContain("found Controller");
  });

  it("默认折叠：不传 defaultExpanded 时收起，入参/出参不进 DOM", () => {
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

    // 时间线不再传 defaultExpanded（此前是 step.active），卡片一律从收起态开始。
    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain("Parameters");
    expect(html).not.toContain("found Controller");
  });

  it("入参流默认折叠：参数不进 DOM，点头部才展开", () => {
    const html = renderToStaticMarkup(
      createElement(GenericToolCall, {
        tool: toolTask({
          finish: false,
          isFinal: false,
          resultMap: {
            toolName: "Bash",
            status: "streaming",
            argsStreaming: true,
            argumentsText: '{"command":"ls -la"}',
          },
        }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined,
      })
    );

    expect(html).toContain('data-testid="generic-tool-arg-stream"');
    // 头部可点且默认收起 —— 入参不会自己摊开
    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain("ls -la");
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

  it("read 卡即使出错也不提供内联展开（对齐 ZCode canToggle=false）", () => {
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

    // ZCode read 渲染器是 `canToggle={false}` + `content={null}`：失败态同样不给展开入口，
    // 报错只由摘要行的状态词 tooltip 承载，正文一律走右侧面板。
    expect(html).not.toContain("kimi-tool-row-toggle");
    // 展开体恒为空（ToolRow 仍会渲染 body 外壳，但里面没有任何内容）
    expect(html).toContain('<div class="kimi-tool-row-body-pad"></div>');
    expect(html).toContain("执行失败");
  });

  it("keeps edit diff inline（成功态不出 Result）", () => {
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
        defaultExpanded: true,
        changeActiveChat: () => undefined,
      })
    );

    expect(html).toContain('aria-expanded="true"');
    expect(html).toContain("a.ts");
    // 展开体里是内联 diff，不是 toolResult 的 JSON 转储
    // （ZCode `diffToolStrategy` 的 `showOutput: Boolean(errorText)`）
    expect(html).toContain("kimi-inline-diff");
    expect(html).not.toContain("updated a.ts");
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
        defaultExpanded: true,
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

  it("mcp 卡以「结果」为一级内容，参数收进「调用详情」二级折叠", () => {
    const html = renderToStaticMarkup(
      createElement(McpToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "mcp__filesystem__read_file",
            input: { path: "src/a.ts" },
            // 多行结果走完整代码块分支（单行短结果会走 compact 分支，见下一个用例）
            toolResult: "line one\nline two\nline three"
          },
        }),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined,
      })
    );

    // 一级：结果 + 头部动作
    expect(html).toContain("结果");
    expect(html).toContain("自动换行");
    expect(html).toContain("复制结果");
    expect(html).toContain('data-testid="mcp-result-surface"');
    // 二级：调用详情折叠（默认收起，参数不在一级平铺）
    expect(html).toContain("调用详情");
    expect(html).toContain('data-testid="mcp-call-details-trigger"');
    expect(html).toContain('aria-expanded="false"');
    // 摘要行展示 server 段，工具段作主标题
    expect(html).toContain("read_file");
    expect(html).toContain("filesystem");
  });

  it("mcp 短结果走 compact 分支：不套代码块、不给结果标题", () => {
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

    expect(html).toContain("kimi-tool-mcp-compact");
    expect(html).toContain("file contents");
    expect(html).not.toContain("kimi-tool-mcp-head");
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

    // 对齐 ZCode `renderers/fallback.tsx`：一块 `<pre>` 原始 JSON，**不给段标题**。
    expect(html).toContain('data-testid="tool-raw-json"');
    expect(html).toContain("SomeUnknownTool");
    // 卡片本身为空时不应该再出现 Parameters / Result 段
    expect(html).not.toContain("Parameters");
    expect(html).not.toContain("Result");
  });
});

describe("Cute-agent 真实工具名（workspace_* 前缀）", () => {
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

describe("Edit 卡内联 diff（对齐 ZCode EditInlineDiffContent）", () => {
  it("单文件 edit 展开体只渲染 diff，不再摊 Parameters", () => {
    const html = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "Edit",
            input: {
              path: "a.ts",
              old_string: "const a = 1",
              new_string: "const a = 2"
            },
            toolResult: "updated a.ts"
          }
        }),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined
      })
    );

    expect(html).toContain("kimi-inline-diff");
    expect(html).toContain('data-testid="inline-diff-preview"');
    expect(html).toContain("const a = 2");
    // ZCode 在有内联 diff 的分支会把通用 Parameters 关掉：变更内容已由 diff 表达
    expect(html).not.toContain("Parameters");
    // diffToolStrategy 是 `showOutput: Boolean(errorText)` —— 成功态**不渲染 Result**。
    // 现在 toolResult 里还带着整份 patch，渲染出来就是一坨 JSON。
    expect(html).not.toContain("kimi-tool-section-title");
    expect(html).not.toContain("updated a.ts");
  });

  it("后端 diff 进展开体：hunk 头与上下文行都保留", () => {
    const backendPatch = [
      "--- a/a.ts",
      "+++ b/a.ts",
      "@@ -44,7 +44,7 @@",
      " 第44行",
      "-第47行",
      "+第47行-新",
      " 第50行",
      "",
    ].join("\n");
    const html = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "workspace_edit",
            input: {
              path: "a.ts",
              old_string: "第47行",
              new_string: "第47行-新"
            },
            toolResult: JSON.stringify({
              path: "a.ts",
              replacements: 1,
              diff: backendPatch
            })
          }
        }),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined
      })
    );

    // getPlainTextPatchPreviewLines 剥掉 ---/+++/@@ 协议头，但保留上下文行
    expect(html).toContain("第44行");
    expect(html).toContain("第50行");
    expect(html).toContain("第47行-新");
    expect(html).not.toContain("@@ -44");
  });

  it("成功态不渲染 Result，但失败态渲染 Error", () => {
    const failed = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "workspace_edit",
            status: "failed",
            input: {
              path: "a.ts",
              old_string: "x",
              new_string: "y"
            },
            errorMsg: "old_string not found"
          }
        }),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined
      })
    );
    expect(failed).toContain("Error");
    expect(failed).toContain("old_string not found");
    expect(failed).not.toContain("Result");
  });

  it("多文件 edit 走 border-l 导轨 + 逐文件可展开子卡", () => {
    const html = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "MultiEdit",
            input: {
              files: [
                {
                  path: "a.ts",
                  old_string: "x",
                  new_string: "y"
                },
                {
                  path: "b/c.ts",
                  old_string: "p\nq",
                  new_string: "p"
                }
              ]
            },
            toolResult: "done"
          }
        }),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined
      })
    );

    expect(html).toContain("kimi-tool-multi-edit-list");
    expect(html).toContain("2 个文件");
    // 主行 + 每个文件一张子卡。子卡沿用 ZCode `EDIT_CHILD_LAYOUT`（canToggle=true /
    // forceOpen=false）：默认收起、展开后才渲染自己的 diff —— 所以这里断言的是
    // 「子卡结构」，不再断言收起态下 DOM 里就有 diff 预览。
    expect(html.match(/class="kimi-tool-row(?![-\w])/g)).toHaveLength(3);
    expect(html).toContain("a.ts");
    expect(html).toContain("c.ts");
  });

  it("write 走合成 patch：展开后是整文件新增的 diff", () => {
    const html = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "Write",
            input: {
              path: "new.ts",
              content: "alpha\nbeta"
            },
            toolResult: "written"
          }
        }),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined
      })
    );

    expect(html).toContain("kimi-inline-diff");
    expect(html).toContain("alpha");
    expect(html).toContain("beta");
  });

  it("默认折叠：write 卡不展开时 diff 不渲染", () => {
    const html = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "Write",
            input: {
              path: "new.ts",
              content: "alpha\nbeta"
            },
            toolResult: "written"
          }
        }),
        chat: {} as CHAT.ChatItem,
        changeActiveChat: () => undefined
      })
    );

    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain("kimi-inline-diff");
  });

  it("失败态只渲染错误文本，不渲染 diff 也不渲染 Parameters", () => {
    const html = renderToStaticMarkup(
      createElement(EditToolCall, {
        tool: toolTask({
          resultMap: {
            toolName: "Edit",
            status: "failed",
            input: {
              path: "a.ts",
              old_string: "x",
              new_string: "y"
            },
            errorMsg: "old_string not found"
          }
        }),
        chat: {} as CHAT.ChatItem,
        defaultExpanded: true,
        changeActiveChat: () => undefined
      })
    );

    expect(html).toContain("Error");
    expect(html).toContain("old_string not found");
    expect(html).not.toContain("kimi-inline-diff");
    expect(html).not.toContain("Parameters");
  });
});
