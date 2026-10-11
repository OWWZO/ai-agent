import type { ReactNode } from "react";
import FileRenderer from "./FileRenderer";
import HTMLRenderer from "./HTMLRenderer";
import ImageRenderer from "./ImageRenderer";
import MarkdownFileRenderer from "./MarkdownFileRenderer";
import MediaFileRenderer from "./MediaFileRenderer";
import PdfRenderer from "./PdfRenderer";
import PptxRenderer from "./PptxRenderer";
import SvgFileRenderer from "./SvgFileRenderer";
import TableRenderer from "./TableRenderer";
import WordRenderer from "./WordRenderer";
import DocumentFallback from "./DocumentFallback";
import GenUiModel3D from "@/components/genui/GenUiModel3D";
import { resolveFilePreviewKind } from "@/lib/filePreview/kinds";

export interface FilePreviewSwitchProps {
  fileUrl: string;
  fileName?: string;
  /** MIME（后端给的类型字段），扩展名取不到时用于兜底判定。 */
  mimeType?: string;
  downloadUrl?: string;
  missingReason?: string;
  /** 强制源码高亮（HTML 不直出、SVG 不出图形、3D 不渲染）。 */
  forceSource?: boolean;
  /** 工作区内嵌：渲染器自带顶栏时隐藏，避免双层 chrome。 */
  embedded?: boolean;
}

/**
 * 「扩展名 → 预览器」的唯一分派点。
 *
 * 工作区文件树（FileList）与验证 harness 共用本组件，避免两处各写一份 switch
 * （历史上 FileList 与 ActionPanel 就因此出现过判定不一致）。
 *
 * 各分支的 className 是刻意保持的：自带 ViewerPanelShell 的渲染器需要 `h-full`
 * 撑满，表格需要 `min-h-0` 参与 flex 收缩，HTML 需要最小高度兜底。
 */
export function FilePreviewSwitch(props: FilePreviewSwitchProps): ReactNode {
  const { fileUrl, fileName, mimeType, downloadUrl, missingReason, forceSource = false, embedded = false } = props;

  const kind = resolveFilePreviewKind({ name: fileName, mimeType, url: fileUrl });
  const resolvedDownload = downloadUrl || fileUrl;

  // 强制源码模式：**任何**类型都不走专门预览器，一律按纯文本读。
  // 这一条必须在 switch 之前短路 —— 否则 markdown / svg / 图片等「有预览形态」的类型
  // 会绕过 forceSource（历史行为：forceSource 会把它们交给 FileRenderer 取文本）。
  if (forceSource) {
    return (
      <FileRenderer
        fileUrl={fileUrl}
        fileName={fileName}
        downloadUrl={resolvedDownload}
        missingReason={missingReason}
        forceSource
      />
    );
  }

  switch (kind) {
    case "image":
      return (
        <ImageRenderer
          imageUrl={fileUrl}
          fileName={fileName}
          missingReason={missingReason}
          className="h-full"
        />
      );

    case "svg":
      return (
        <SvgFileRenderer
          fileUrl={fileUrl}
          fileName={fileName}
          downloadUrl={resolvedDownload}
          missingReason={missingReason}
          hideChrome={embedded}
          className="h-full"
        />
      );

    case "markdown":
      return (
        <MarkdownFileRenderer
          fileUrl={fileUrl}
          fileName={fileName}
          downloadUrl={resolvedDownload}
          missingReason={missingReason}
          hideChrome={embedded}
          className="h-full"
        />
      );

    case "video":
    case "audio":
      return (
        <MediaFileRenderer
          kind={kind}
          fileUrl={fileUrl}
          fileName={fileName}
          missingReason={missingReason}
          className="h-full"
        />
      );

    case "model3d":
      return (
        <div className="flex h-full min-h-[360px] items-stretch bg-[#0f172a] p-3">
          <div className="min-h-[360px] w-full">
            <GenUiModel3D src={fileUrl} caption={fileName} height={520} />
          </div>
        </div>
      );

    case "pdf":
      return (
        <PdfRenderer
          fileUrl={fileUrl}
          fileName={fileName}
          downloadUrl={resolvedDownload}
          missingReason={missingReason}
          hideChrome={embedded}
          className="h-full"
        />
      );

    case "docx":
    case "doc":
      return (
        <WordRenderer
          fileUrl={fileUrl}
          fileName={fileName}
          downloadUrl={resolvedDownload}
          missingReason={missingReason}
          legacyDoc={kind === "doc"}
          hideChrome={embedded}
          className="h-full"
        />
      );

    case "html":
      return (
        <HTMLRenderer
          htmlUrl={fileUrl}
          downloadUrl={resolvedDownload}
          missingReason={missingReason}
          showToolBar
          className="h-full min-h-[480px]"
        />
      );

    case "pptx":
      // 有可读文件就交给内置渲染引擎；拿不到文件才回落下载引导。
      if (!fileUrl) {
        return (
          <DocumentFallback
            label="PPT"
            title="演示文稿暂不可预览"
            description={
              missingReason ||
              "未拿到可读取的演示文稿文件，请下载后用 PowerPoint / WPS 打开。若产物已转为 HTML 演示页，请打开对应 .html 文件。"
            }
            fileName={fileName}
            downloadUrl={resolvedDownload}
            className="h-full"
            type="info"
          />
        );
      }
      return (
        <PptxRenderer
          fileUrl={fileUrl}
          fileName={fileName}
          downloadUrl={resolvedDownload}
          missingReason={missingReason}
          className="h-full"
        />
      );

    case "xlsx":
    case "csv":
      return (
        <TableRenderer
          fileUrl={fileUrl}
          fileName={fileName}
          downloadUrl={resolvedDownload}
          missingReason={missingReason}
          className="h-full min-h-0"
        />
      );

    default:
      break;
  }

  return (
    <FileRenderer
      fileUrl={fileUrl}
      fileName={fileName}
      downloadUrl={resolvedDownload}
      missingReason={missingReason}
    />
  );
}

export default FilePreviewSwitch;
