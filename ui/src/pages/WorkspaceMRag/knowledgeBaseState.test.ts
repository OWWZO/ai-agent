import { describe, expect, it } from "vitest";

import {
  resolveKnowledgeBaseAfterDeletion,
  resolveSelectedKnowledgeBaseId,
  shouldBootstrapKnowledgeBases,
  shouldPollKnowledgeBaseFiles,
} from "./knowledgeBaseState";

describe("knowledgeBaseState", () => {
  it("优先使用 preferredKnowledgeBaseId，缺失时回退当前选中，再回退第一项", () => {
    const knowledgeBases = [{ id: "kb-1" }, { id: "kb-2" }];

    expect(resolveSelectedKnowledgeBaseId(knowledgeBases, "kb-1", "kb-2")).toBe(
      "kb-2"
    );
    expect(resolveSelectedKnowledgeBaseId(knowledgeBases, "kb-1", "kb-x")).toBe(
      "kb-1"
    );
  });

  it("只有存在处理中文件时才轮询文件列表", () => {
    expect(
      shouldPollKnowledgeBaseFiles([
        { fileStatus: "RUNNING" },
      ] as Array<{ fileStatus: "RUNNING" }>)
    ).toBe(true);
    expect(
      shouldPollKnowledgeBaseFiles([
        { fileStatus: "SUCCESS" },
      ] as Array<{ fileStatus: "SUCCESS" }>)
    ).toBe(false);
  });

  it("同一个 toolBaseUrl 初始化后不应重复触发目录拉取", () => {
    expect(
      shouldBootstrapKnowledgeBases(null, "http://127.0.0.1:1601")
    ).toBe(true);
    expect(
      shouldBootstrapKnowledgeBases(
        "http://127.0.0.1:1601",
        "http://127.0.0.1:1601"
      )
    ).toBe(false);
    expect(
      shouldBootstrapKnowledgeBases(
        "http://127.0.0.1:1601",
        "http://127.0.0.1:1701"
      )
    ).toBe(true);
  });

  it("删除知识库后能收敛选中态和空态", () => {
    expect(
      resolveKnowledgeBaseAfterDeletion(
        [{ id: "kb-1" }, { id: "kb-2" }],
        "kb-1",
        "kb-1"
      )
    ).toBe("kb-2");
    expect(
      resolveKnowledgeBaseAfterDeletion(
        [{ id: "kb-2" }],
        "kb-2",
        "kb-1"
      )
    ).toBe("kb-2");
    expect(resolveKnowledgeBaseAfterDeletion([], "", "kb-1")).toBe("");
  });
});
