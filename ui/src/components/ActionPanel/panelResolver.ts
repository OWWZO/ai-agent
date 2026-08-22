import type { DeepSearchChapterWorkspaceModel } from "@/types/deepSearch";
import { buildDeepSearchChapterWorkspaceModel } from "@/utils/deepSearch";
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

type DeepSearchChapterPanelView = {
  type: "deep-search-chapter";
  model: DeepSearchChapterWorkspaceModel;
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

type AskUserQuestionPanelView = {
  type: "ask_user_question";
  tool: PanelItemType;
};

export type PanelView =
  | EmptyPanelView
  | AskUserQuestionPanelView
  | SearchPanelView
  | DeepSearchChapterPanelView
  | HtmlPanelView
  | InlineHtmlPanelView
  | FilePanelView
  | DownloadOnlyPanelView
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

function fileView(
  type: FilePanelView["type"],
  primaryFile?: CHAT.TFile,
  missingReason?: string
): FilePanelView {
  return {
    type,
    fileUrl: primaryFile?.url || "",
    fileName: primaryFile?.name,
    downloadUrl: primaryFile?.downloadUrl || primaryFile?.url,
    missingReason,
  };
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

  if (taskItem.messageType === "ask_user_question") {
    return {
      type: "ask_user_question",
      tool: taskItem,
    };
  }

  // 解析顺序就是展示优先级：搜索/GenUI 先于文件，再到 markdown 兜底，避免同一事件被多个 renderer 抢占。

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

  if (taskItem.messageType === "deep_search") {
    const deepSearchTask = taskItem as unknown as Pick<CHAT.Task, "messageType" | "resultMap">;
    const chapterModel = buildDeepSearchChapterWorkspaceModel(deepSearchTask);
    if (chapterModel) {
      return {
        type: "deep-search-chapter",
        model: chapterModel,
      };
    }
  }

  if (searchList?.length) {
    return {
      type: "search",
      searchList,
    };
  }

  // GenUI 仅在对话主回复区展示，工作区不渲染
  if (useGenUi || taskItem.messageType === "ui_tree") {
    return { type: "empty" };
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
    // HTML 流式期间隐藏工具栏，只有最终产物具备稳定下载/交互能力。
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
    return fileView("excel", primaryFile, missingReason);
  }

  if (useImage) {
    return fileView("image", primaryFile, missingReason);
  }

  if (usePdf) {
    return fileView("pdf", primaryFile, missingReason);
  }

  if (useDocx) {
    return fileView("docx", primaryFile, missingReason);
  }

  if (useLegacyDoc) {
    return fileView("legacy-doc", primaryFile, missingReason);
  }

  if (useFile) {
    return fileView("file", primaryFile, missingReason);
  }

  // 工具结构化出参（Structured data / JSON）暂不展示。
  if (useJSON) {
    return { type: "empty" };
  }

  return {
    type: "markdown",
    content: markDownContent,
    isStreaming: !isFinal,
  };
}
