import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";

import { WorkspaceMRagView } from "./view";

describe("WorkspaceMRagView", () => {
  it("渲染知识库、文件访问入口和检索结果区域", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter initialEntries={["/workspace/mrag"]}>
        <WorkspaceMRagView
          toolBaseUrlDraft="http://127.0.0.1:1601"
          activeToolBaseUrl="http://127.0.0.1:1601"
          onToolBaseUrlChange={() => {}}
          onApplyToolBaseUrl={() => {}}
          knowledgeBases={[
            {
              id: "kb-1",
              name: "产品资料库",
              description: "用于销售问答",
              chunkType: "fixed_size",
              chunkSize: null,
              chunkOverlapSize: null,
              createdAt: "2026-04-26T10:00:00",
              updatedAt: "2026-04-26T10:00:00",
              raw: {},
            },
          ]}
          knowledgeBasesLoading={false}
          knowledgeBasesError=""
          selectedKnowledgeBaseId="kb-1"
          onSelectKnowledgeBase={() => {}}
          onRefreshKnowledgeBases={() => {}}
          deletingKnowledgeBaseId=""
          onDeleteKnowledgeBase={() => {}}
          createKnowledgeBaseName=""
          createKnowledgeBaseDesc=""
          onCreateKnowledgeBaseNameChange={() => {}}
          onCreateKnowledgeBaseDescChange={() => {}}
          creatingKnowledgeBase={false}
          onCreateKnowledgeBase={() => {}}
          selectedKnowledgeBase={{
            id: "kb-1",
            name: "产品资料库",
            description: "用于销售问答",
            chunkType: "fixed_size",
            chunkSize: null,
            chunkOverlapSize: null,
            createdAt: "2026-04-26T10:00:00",
            updatedAt: "2026-04-26T10:00:00",
            raw: {},
          }}
          files={[
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
              fileStatus: "SUCCESS",
              taskStatus: {},
              docCount: 4,
              createdAt: "2026-04-26T10:00:00",
              updatedAt: "2026-04-26T10:00:00",
              errorMessage: "",
              raw: {},
            },
            {
              id: "file-2",
              knowledgeBaseId: "kb-1",
              title: "官网说明",
              sourceType: "url",
              sourceUrl: "https://example.com/guide",
              fileUrl: "https://example.com/guide",
              previewUrl: "https://example.com/guide",
              downloadUrl: "https://example.com/guide",
              fileExt: ".md",
              fileStatus: "RUNNING",
              taskStatus: {},
              docCount: 0,
              createdAt: "2026-04-26T10:00:00",
              updatedAt: "2026-04-26T10:00:00",
              errorMessage: "",
              raw: {},
            },
          ]}
          filesLoading={false}
          filesError=""
          uploadingFiles={false}
          addingWebUrl={false}
          webUrl=""
          onWebUrlChange={() => {}}
          onUploadFiles={() => {}}
          onAddWebUrl={() => {}}
          onRefreshFiles={() => {}}
          activeFullContentFileId=""
          fullContentLoading={false}
          fullContentDrawerOpen={true}
          fullContentTitle="demo.pdf"
          fullContentStatus="READY"
          fullContentError=""
          fullContentMarkdown={"# 正文标题\n\n这里是正文。"}
          onOpenFullContent={() => {}}
          onCloseFullContent={() => {}}
          onDeleteFile={() => {}}
          question="资料里有哪些关键流程？"
          onQuestionChange={() => {}}
          querying={false}
          queryAnswer={"## 结论\n\n- 命中了产品接入流程说明"}
          queryError=""
          queryRawChunks={[{ choices: [{ delta: { content: "命中了产品接入流程说明" } }] }]}
          onSubmitQuery={() => {}}
          onStopQuery={() => {}}
          onClearQueryResult={() => {}}
        />
      </MemoryRouter>
    );

    expect(html).toContain("MRAG 文件工作台");
    expect(html).toContain("产品资料库");
    expect(html).toContain("上传文件");
    expect(html).toContain("预览");
    expect(html).toContain("下载");
    expect(html).toContain("打开原链接");
    expect(html).toContain("查看正文");
    expect(html).toContain("原始资料");
    expect(html).toContain("整篇正文");
    expect(html).toContain("正文标题");
    expect(html).toContain("调试");
    expect(html).toContain("命中了产品接入流程说明");
  });

  it("在正文不可用时展示明确提示", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter initialEntries={["/workspace/mrag"]}>
        <WorkspaceMRagView
          toolBaseUrlDraft="http://127.0.0.1:1601"
          activeToolBaseUrl="http://127.0.0.1:1601"
          onToolBaseUrlChange={() => {}}
          onApplyToolBaseUrl={() => {}}
          knowledgeBases={[]}
          knowledgeBasesLoading={false}
          knowledgeBasesError=""
          selectedKnowledgeBaseId=""
          onSelectKnowledgeBase={() => {}}
          onRefreshKnowledgeBases={() => {}}
          deletingKnowledgeBaseId=""
          onDeleteKnowledgeBase={() => {}}
          createKnowledgeBaseName=""
          createKnowledgeBaseDesc=""
          onCreateKnowledgeBaseNameChange={() => {}}
          onCreateKnowledgeBaseDescChange={() => {}}
          creatingKnowledgeBase={false}
          onCreateKnowledgeBase={() => {}}
          selectedKnowledgeBase={null}
          files={[]}
          filesLoading={false}
          filesError=""
          uploadingFiles={false}
          addingWebUrl={false}
          webUrl=""
          onWebUrlChange={() => {}}
          onUploadFiles={() => {}}
          onAddWebUrl={() => {}}
          onRefreshFiles={() => {}}
          activeFullContentFileId="file-2"
          fullContentLoading={false}
          fullContentDrawerOpen={true}
          fullContentTitle="官网说明"
          fullContentStatus="FAILED"
          fullContentError="解析失败"
          fullContentMarkdown=""
          onOpenFullContent={() => {}}
          onCloseFullContent={() => {}}
          onDeleteFile={() => {}}
          question=""
          onQuestionChange={() => {}}
          querying={false}
          queryAnswer=""
          queryError=""
          queryRawChunks={[]}
          onSubmitQuery={() => {}}
          onStopQuery={() => {}}
          onClearQueryResult={() => {}}
        />
      </MemoryRouter>
    );

    expect(html).toContain("正文暂不可用");
    expect(html).toContain("解析失败");
  });
});
