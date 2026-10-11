import { describe, expect, it } from "vitest";
import {
  isCompactMcpResult,
  isMcpToolName,
  looksLikeJson,
  parseMcpToolName,
} from "./mcpToolName";

describe("mcpToolName", () => {
  it("识别后端 McpToolNamePolicy 的 FQ 名（mcp__{server}__{tool}）", () => {
    expect(isMcpToolName("mcp__filesystem__read_file")).toBe(true);
    expect(isMcpToolName("mcp__my_server__do_thing")).toBe(true);
  });

  it("拒绝非 MCP 名与残缺 FQ 名", () => {
    expect(isMcpToolName("Read")).toBe(false);
    expect(isMcpToolName("")).toBe(false);
    expect(isMcpToolName(null)).toBe(false);
    expect(isMcpToolName("mcp__onlyserver")).toBe(false);
    expect(isMcpToolName("mcp__server__")).toBe(false);
    expect(isMcpToolName("mcp____tool")).toBe(false);
  });

  it("按第一个 __ 切分，保留 server 段自身含下划线的情况", () => {
    const identity = parseMcpToolName("mcp__my_server__do_thing");
    expect(identity).not.toBeNull();
    expect(identity!.serverKey).toBe("my_server");
    expect(identity!.toolName).toBe("do_thing");
  });

  it("保留原始大小写（不能用 normalizeToolName 小写化）", () => {
    const identity = parseMcpToolName("mcp__GitHubServer__listIssues");
    expect(identity!.serverKey).toBe("GitHubServer");
    expect(identity!.toolName).toBe("listIssues");
  });

  it("非 FQ 名解析返回 null", () => {
    expect(parseMcpToolName("Read")).toBeNull();
    expect(parseMcpToolName("mcp__onlyserver")).toBeNull();
  });

  it("判定紧凑结果与 JSON 结果", () => {
    expect(isCompactMcpResult("ok")).toBe(true);
    expect(isCompactMcpResult("line one\nline two")).toBe(false);
    expect(isCompactMcpResult('{"a":1}')).toBe(false);
    expect(isCompactMcpResult("x".repeat(200))).toBe(false);
    expect(looksLikeJson('{"a":1}')).toBe(true);
    expect(looksLikeJson("[1,2]")).toBe(true);
    expect(looksLikeJson("plain text")).toBe(false);
  });
});
