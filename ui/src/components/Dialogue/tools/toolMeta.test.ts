import { describe, expect, it } from "vitest";
import {
  extractExecuteCommand,
  normalizeToolName,
  toolChip,
  toolLabel,
  toolSummary,
} from "./toolMeta";

describe("toolMeta", () => {
  it("normalizes aliases", () => {
    expect(normalizeToolName("shell")).toBe("bash");
    expect(normalizeToolName("web_search")).toBe("search");
    expect(normalizeToolName("Read")).toBe("read");
  });

  it("归一 ZCode 的 execute 工具名到 bash", () => {
    expect(normalizeToolName("execute")).toBe("bash");
    expect(normalizeToolName("Execute")).toBe("bash");
    expect(normalizeToolName("exec")).toBe("bash");
  });

  it("summarizes read path with range", () => {
    expect(
      toolSummary(
        "read",
        JSON.stringify({ path: "src/a.ts", offset: 10, limit: 20 })
      )
    ).toBe("src/a.ts:10-30");
  });

  it("hides empty object in collapsed header", () => {
    expect(toolSummary("bash", "{}")).toBe("");
    expect(toolSummary("bash", "{}", true)).toBe("{}");
  });

  it("summarizes bash command", () => {
    expect(
      toolSummary("bash", JSON.stringify({ command: "ls -la" }))
    ).toBe("ls -la");
  });

  it("labels and chips", () => {
    expect(toolLabel("web_search")).toBe("Search");
    expect(
      toolChip({
        name: "read",
        arg: "{}",
        output: ["a", "b", "c"],
      })
    ).toBe("3 lines");
  });
});

describe("extractExecuteCommand", () => {
  it("取对象里的 command 全文，不截断", () => {
    const long = "echo " + "x".repeat(120);
    expect(extractExecuteCommand(JSON.stringify({ command: long }))).toBe(long);
  });

  it("认 cmd / script / code 字段", () => {
    expect(extractExecuteCommand('{"cmd":"ls -la"}')).toBe("ls -la");
    expect(extractExecuteCommand('{"script":"pwd"}')).toBe("pwd");
    expect(extractExecuteCommand('{"code":"print(1)"}')).toBe("print(1)");
  });

  it("认 ZCode 的数组形态，取 -lc 之后那一段", () => {
    expect(
      extractExecuteCommand(JSON.stringify(["/bin/bash", "-lc", "npm run build"]))
    ).toBe("npm run build");
  });

  it("纯字符串命令剥掉 shell 包装前缀", () => {
    expect(extractExecuteCommand("bash -lc 'ls -la'")).toBe("'ls -la'");
    expect(extractExecuteCommand("/bin/zsh -lc pwd")).toBe("pwd");
    expect(extractExecuteCommand("ls -la")).toBe("ls -la");
  });

  it("空入参返回空串", () => {
    expect(extractExecuteCommand("")).toBe("");
    expect(extractExecuteCommand("   ")).toBe("");
  });
});

describe("toolSummary glob", () => {
  it("pattern + path 拼成 'pattern in path'", () => {
    expect(
      toolSummary("glob", JSON.stringify({ pattern: "*.ts", path: "src" }))
    ).toBe("*.ts  in src");
  });

  it("只有 pattern 时退回 pattern 本身", () => {
    expect(toolSummary("glob", JSON.stringify({ pattern: "*.ts" }))).toBe("*.ts");
  });

  it("path 缺省时用 cwd 兜底，不漏成原始入参", () => {
    expect(toolSummary("glob", JSON.stringify({ cwd: "packages/ui" }))).toBe(
      "packages/ui"
    );
  });

  it("pattern 和路径都没有时退回原始入参", () => {
    expect(toolSummary("glob", JSON.stringify({ limit: 5 }))).toBe(
      JSON.stringify({ limit: 5 })
    );
  });
});
