import { describe, expect, it } from "vitest";

import {
  createDefaultMRagWorkspaceStoredState,
  formatFileDocCount,
  parseMRagWorkspaceStoredState,
  resolveFileStatusMeta,
  resolveSourceSummary,
} from "./utils";

describe("WorkspaceMRag utils", () => {
  it("解析持久化状态时会回退默认值并清理尾部斜杠", () => {
    const defaults = createDefaultMRagWorkspaceStoredState();

    expect(parseMRagWorkspaceStoredState()).toEqual(defaults);
    expect(parseMRagWorkspaceStoredState("invalid-json")).toEqual(defaults);
    expect(
      parseMRagWorkspaceStoredState(
        JSON.stringify({
          toolBaseUrl: "http://127.0.0.1:1601/",
          selectedKnowledgeBaseId: "kb-1",
        })
      )
    ).toEqual({
      toolBaseUrl: "http://127.0.0.1:1601",
      selectedKnowledgeBaseId: "kb-1",
    });
  });

  it("根据文件状态返回稳定的展示文案", () => {
    expect(resolveFileStatusMeta("RUNNING")).toMatchObject({label: "处理中",});
    expect(resolveFileStatusMeta("SUCCESS")).toMatchObject({label: "已完成",});
    expect(resolveFileStatusMeta("FAILED")).toMatchObject({label: "失败",});
  });

  it("按不同阶段格式化文件片段数", () => {
    expect(formatFileDocCount({
      docCount: 8,
      fileStatus: "SUCCESS"
    })).toBe("8 个片段");
    expect(formatFileDocCount({
      docCount: 0,
      fileStatus: "FAILED"
    })).toBe("未完成切片");
    expect(formatFileDocCount({
      docCount: 0,
      fileStatus: "RUNNING"
    })).toBe("处理中");
  });

  it("提取来源摘要时优先返回主机名", () => {
    expect(resolveSourceSummary("https://docs.example.com/guide/index.html")).toBe(
      "docs.example.com"
    );
    expect(resolveSourceSummary("not-a-url")).toBe("not-a-url");
  });
});

