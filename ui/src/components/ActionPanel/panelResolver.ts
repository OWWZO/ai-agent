import type { PanelItemType, SearchListItem } from "./type";

export interface PanelResolverMessageTypes {
  useCode?: boolean;
  useHtml?: boolean;
  useImage?: boolean;
  useExcel?: boolean;
  usePdf?: boolean;
  useDocx?: boolean;
  useLegacyDoc?: boolean;
  useWord?: boolean;
  useFile?: boolean;
  useJSON?: boolean | string;
  isHtml?: boolean;
  searchList?: SearchListItem[];
  usePpt?: boolean;
  useGenUi?: boolean;
}

type HtmlPanelView = {
  type: "html";
  htmlUrl?: string;
  downloadUrl?: string;
  missingReason?: string;
  outputCode?: string;
  showToolBar: boolean;
  isStreaming: boolean;
};

type InlineHtmlPanelView = {
  type: "inline-html";
  htmlUrl: string;
};

type FilePanelView = {
  type: "file" | "image" | "excel" | "pdf" | "docx" | "legacy-doc";
  fileUrl: string;
  fileName?: string;
  downloadUrl?: string;
  missingReason?: string;
};

type DownloadOnlyPanelView = {
  type: "download-only";
  label: string;
  title: string;
  description: string;
  fileName?: string;
  downloadUrl?: string;
  missingReason?: string;
};

type SearchPanelView = {
  type: "search";
  searchList: SearchListItem[];
};

type JsonPanelView = {
  type: "json";
  jsonData: object;
};

type MarkdownPanelView = {
  type: "markdown";
  content: string;
  isStreaming: boolean;
};

type GenUiPanelView = {
  type: "ui_tree";
  tree?: unknown;
};

type EmptyPanelView = {
  type: "empty";
};

export type PanelView =
  | EmptyPanelView
  | SearchPanelView
  | HtmlPanelView
  | InlineHtmlPanelView
  | FilePanelView
  | DownloadOnlyPanelView
  | JsonPanelView
  | MarkdownPanelView
  | GenUiPanelView;

interface ResolvePanelViewParams {
  taskItem?: PanelItemType;
  msgTypes?: PanelResolverMessageTypes;
  markDownContent: string;
  htmlUrl?: string;
  downloadHtmlUrl?: string;
  missingReason?: string;
  allowShowToolBar?: boolean;
  isFinal?: boolean;
  codeOutput?: string;
  toolResultText?: string;
  primaryFile?: CHAT.TFile;
}

function parseJsonSafely(value?: string) {
  try {
    const parsed = JSON.parse(value || "{}");
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

export function resolvePanelView(params: ResolvePanelViewParams): PanelView {
  const {
    taskItem,
    msgTypes,
    markDownContent,
    htmlUrl,
    downloadHtmlUrl,
    missingReason,
    allowShowToolBar,
    isFinal,
    codeOutput,
    toolResultText,
    primaryFile,
  } = params;

  if (!taskItem) {
    return { type: "empty" };
  }

  const {
    useHtml,
    useCode,
    useFile,
    useImage,
    isHtml,
    useExcel,
    usePdf,
    useDocx,
    useLegacyDoc,
    useJSON,
    searchList,
    usePpt,
    useGenUi,
  } = msgTypes || {};

  if (searchList?.length) {
    return {
      type: "search",
      searchList,
    };
  }

  if (useGenUi || taskItem.messageType === "ui_tree") {
    const nested = (taskItem as any)?.resultMap;
    return {
      type: "ui_tree",
      tree: nested?.tree || nested?.resultMap?.tree,
    };
  }

  if (usePpt) {
    return {
      type: "download-only",
      label: "PPT",
      title: "暂不支持在线预览 PPT/PPTX",
      description:
        missingReason ||
        "二进制演示文稿无法在浏览器中直接打开，请下载后用 PowerPoint / WPS 查看。若后端已转为 HTML 演示页，请使用 .html 产物。",
      fileName: primaryFile?.name,
      downloadUrl: primaryFile?.downloadUrl || primaryFile?.url || downloadHtmlUrl,
      missingReason,
    };
  }

  if (useHtml) {
    return {
      type: "html",
      htmlUrl,
      downloadUrl: downloadHtmlUrl,
      missingReason,
      outputCode: codeOutput,
      showToolBar: Boolean(allowShowToolBar && isFinal),
      isStreaming: !isFinal,
    };
  }

  if (useCode && isHtml) {
    return {
      type: "inline-html",
      htmlUrl: `data:text/html;charset=utf-8,${encodeURIComponent(toolResultText || "")}`,
    };
  }

  if (useExcel) {
    return {
      type: "excel",
      fileUrl: primaryFile?.url || "",
      fileName: primaryFile?.name,
      downloadUrl: primaryFile?.downloadUrl || primaryFile?.url,
      missingReason,
    };
  }

  if (useImage) {
    return {
      type: "image",
      fileUrl: primaryFile?.url || "",
      fileName: primaryFile?.name,
      downloadUrl: primaryFile?.downloadUrl || primaryFile?.url,
      missingReason,
    };
  }

  if (usePdf) {
    return {
      type: "pdf",
      fileUrl: primaryFile?.url || "",
      fileName: primaryFile?.name,
      downloadUrl: primaryFile?.downloadUrl || primaryFile?.url,
      missingReason,
    };
  }

  if (useDocx) {
    return {
      type: "docx",
      fileUrl: primaryFile?.url || "",
      fileName: primaryFile?.name,
      downloadUrl: primaryFile?.downloadUrl || primaryFile?.url,
      missingReason,
    };
  }

  if (useLegacyDoc) {
    return {
      type: "legacy-doc",
      fileUrl: primaryFile?.url || "",
      fileName: primaryFile?.name,
      downloadUrl: primaryFile?.downloadUrl || primaryFile?.url,
      missingReason,
    };
  }

  if (useFile) {
    return {
      type: "file",
      fileUrl: primaryFile?.url || "",
      fileName: primaryFile?.name,
      downloadUrl: primaryFile?.downloadUrl || primaryFile?.url,
      missingReason,
    };
  }

  if (useJSON) {
    return {
      type: "json",
      jsonData: parseJsonSafely(toolResultText),
    };
  }

  return {
    type: "markdown",
    content: markDownContent,
    isStreaming: !isFinal,
  };
}
