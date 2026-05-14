import { afterEach, describe, expect, it, vi } from "vitest";

import {
  deleteKnowledgeBase,
  getKnowledgeBaseFileFullContent,
  MRagWorkspaceRequestError,
  extractMragChunkContent,
  hasProcessingFiles,
  mapMragError,
  normalizeKnowledgeBase,
  normalizeKnowledgeBaseFile,
  resolveUploadedFileUrl,
  resolveWorkspaceDownloadUrl,
  resolveWorkspacePreviewUrl,
} from "./mragWorkspace";

const originalFetch = globalThis.fetch;

afterEach(() => {
  vi.restoreAllMocks();
  globalThis.fetch = originalFetch;
});

describe("mragWorkspace service utils", () => {
  it("归一化知识库结构并保留核心字段", () => {
    expect(
      normalizeKnowledgeBase({
        kb_id: "kb-1",
        kb_name: "产品资料库",
        kb_desc: "面向销售场景",
        chunk_type: "fixed_size",
      })
    ).toMatchObject({
      id: "kb-1",
      name: "产品资料库",
      description: "面向销售场景",
      chunkType: "fixed_size",
    });
  });

  it("能为本地文件推导预览和下载地址，并纠正网页来源类型", () => {
    const localFile = normalizeKnowledgeBaseFile({
      kb_id: "kb-1",
      file_id: "file-1",
      file_url: "http://127.0.0.1:1601/download/req/demo.pdf",
      title: "demo.pdf",
      file_ext: ".pdf",
      source_type: "file",
      task_status: {global_status: "RUNNING",},
    });
    const webFile = normalizeKnowledgeBaseFile({
      kb_id: "kb-1",
      file_id: "file-2",
      file_url: "https://example.com/article",
      title: "",
      file_ext: ".md",
      source_type: "file",
      task_status: {global_status: "SUCCESS",},
    });

    expect(localFile).toMatchObject({
      sourceType: "file",
      previewUrl: "http://127.0.0.1:1601/preview/req/demo.pdf",
      downloadUrl: "http://127.0.0.1:1601/download/req/demo.pdf",
      fileStatus: "RUNNING",
    });
    expect(webFile).toMatchObject({
      sourceType: "url",
      previewUrl: "https://example.com/article",
      downloadUrl: "https://example.com/article",
      fileStatus: "SUCCESS",
    });
  });

  it("能在 preview / download 之间互相推导地址", () => {
    expect(
      resolveWorkspacePreviewUrl("http://127.0.0.1:1601/download/req/demo.pdf")
    ).toBe("http://127.0.0.1:1601/preview/req/demo.pdf");
    expect(
      resolveWorkspaceDownloadUrl("http://127.0.0.1:1601/preview/req/demo.pdf")
    ).toBe("http://127.0.0.1:1601/download/req/demo.pdf");
  });

  it("本地上传优先写入可预览地址", () => {
    expect(
      resolveUploadedFileUrl({
        documentId: "doc-1",
        filename: "demo.pdf",
        originalFilename: "demo.pdf",
        previewUrl: "http://127.0.0.1:1601/preview/req/demo.pdf",
        permanentUrl: "http://127.0.0.1:1601/download/req/demo.pdf",
        presignedUrl: "http://127.0.0.1:1601/download/req/demo.pdf",
        storageType: "local",
        contentType: "application/pdf",
        fileSize: 1024,
        uploadTime: "2026-04-26T10:00:00",
        raw: {},
      })
    ).toBe("http://127.0.0.1:1601/preview/req/demo.pdf");
  });

  it("识别处理中状态并解析 OpenAI 兼容 chunk", () => {
    expect(
      hasProcessingFiles([
        {
          id: "file-1",
          knowledgeBaseId: "kb-1",
          title: "demo.pdf",
          sourceType: "file",
          sourceUrl: "http://127.0.0.1:1601/preview/req/demo.pdf",
          fileUrl: "http://127.0.0.1:1601/preview/req/demo.pdf",
          previewUrl: "http://127.0.0.1:1601/preview/req/demo.pdf",
          downloadUrl: "http://127.0.0.1:1601/download/req/demo.pdf",
          fileExt: ".pdf",
          fileStatus: "PENDING",
          taskStatus: {},
          docCount: 0,
          createdAt: null,
          updatedAt: null,
          errorMessage: "",
          raw: {},
        },
      ])
    ).toBe(true);
    expect(
      extractMragChunkContent({
        choices: [
          {delta: {content: "这里是流式回答",},},
        ],
      })
    ).toBe("这里是流式回答");
  });

  it("统一映射服务层错误消息", () => {
    expect(
      mapMragError(
        new MRagWorkspaceRequestError("请求失败", {
          status: 500,
          rawResponse: {message: "请求失败",},
        })
      )
    ).toBe("请求失败");
    expect(mapMragError(new Error("网络中断"))).toBe("网络中断");
  });

  it("调用知识库删除接口并返回删除结果摘要", async () => {
    globalThis.fetch = vi.fn().mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          code: 200,
          msg: "success",
          data: {
            kb_id: "kb-1",
            deleted_file_count: 3,
          },
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }
      )
    ) as typeof fetch;

    await expect(deleteKnowledgeBase("http://127.0.0.1:1601", "kb-1")).resolves.toEqual({
      kbId: "kb-1",
      deletedFileCount: 3,
    });
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://127.0.0.1:1601/v1/documents/delete_knowledge_base",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ kb_id: "kb-1" }),
      })
    );
  });

  it("查询整篇正文时返回稳定内容状态", async () => {
    globalThis.fetch = vi.fn().mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          code: 200,
          msg: "success",
          data: {
            kb_id: "kb-1",
            file_id: "file-1",
            title: "demo.pdf",
            file_url: "http://127.0.0.1:1601/download/req/demo.pdf",
            source_type: "file",
            file_status: "SUCCESS",
            content_status: "READY",
            content_format: "markdown",
            content: "# 正文标题\n\n这里是正文。",
            error_message: "",
          },
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }
      )
    ) as typeof fetch;

    await expect(
      getKnowledgeBaseFileFullContent("http://127.0.0.1:1601", {
        kbId: "kb-1",
        fileId: "file-1",
      })
    ).resolves.toMatchObject({
      knowledgeBaseId: "kb-1",
      fileId: "file-1",
      contentStatus: "READY",
      contentFormat: "markdown",
      content: "# 正文标题\n\n这里是正文。",
      sourceType: "file",
    });
  });
});
