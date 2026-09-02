import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import AttachmentList from "./index";

const htmlFile: CHAT.TFile = {
  name: "notes-competitor-canvas.html",
  url: "https://example.com/preview/notes-competitor-canvas.html",
  type: "html",
  size: 1,
  resourceKey: "call_xyz::notes-competitor-canvas.html",
};

describe("AttachmentList workspace entry", () => {
  it("renders featured html without extra workspace files", () => {
    const html = renderToStaticMarkup(
      <AttachmentList files={[htmlFile]} preview />
    );
    expect(html).toContain("notes-competitor-canvas.html");
    expect(html).not.toContain("全部文件");
  });

  it("renders the all-files card under the featured file", () => {
    const html = renderToStaticMarkup(
      <AttachmentList
        files={[htmlFile]}
        preview
        showWorkspaceFilesEntry
        onOpenWorkspaceFiles={() => undefined}
      />
    );
    expect(html).toContain("notes-competitor-canvas.html");
    expect(html).toContain("全部文件");
    expect(html).toContain("查看或下载文件");
    expect(html).toContain("预览");
    expect(html).not.toContain("style.css");
  });
});
