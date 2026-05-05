import type { PanelItemType, SearchListItem } from "./type";

export interface PanelResolverMessageTypes {
  useCode?: boolean;
  useHtml?: boolean;
  useImage?: boolean;
  useExcel?: boolean;
  useFile?: boolean;
  useJSON?: boolean | string;
  isHtml?: boolean;
  searchList?: SearchListItem[];
  usePpt?: boolean;
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
  type: "file" | "image" | "excel";
  fileUrl: string;
  fileName?: string;
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

type EmptyPanelView = {
  type: "empty";
};

export type PanelView =
  | EmptyPanelView
  | SearchPanelView
  | HtmlPanelView
  | InlineHtmlPanelView
  | FilePanelView
  | JsonPanelView
  | MarkdownPanelView;

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
    useJSON,
    searchList,
    usePpt,
  } = msgTypes || {};

  if (searchList?.length) {
    return {
      type: "search",
      searchList,
    };
  }

  if (useHtml || usePpt) {
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
      missingReason,
    };
  }

  if (useImage) {
    return {
      type: "image",
      fileUrl: primaryFile?.url || "",
      fileName: primaryFile?.name,
      missingReason,
    };
  }

  if (useFile) {
    return {
      type: "file",
      fileUrl: primaryFile?.url || "",
      fileName: primaryFile?.name,
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
