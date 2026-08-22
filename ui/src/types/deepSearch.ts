export type DeepSearchCardItemKind = "result" | "query";

export type DeepSearchCardItem = {
  name: string;
  pageContent: string;
  url: string;
  kind?: DeepSearchCardItemKind;
  interactive?: boolean;
  metaLabel?: string;
};

export type DeepSearchStage = "extend" | "search" | "chapter_summary" | "report";

export type DeepSearchPreviewModel = {
  stage: Exclude<DeepSearchStage, "report">;
  query: string;
  statusLabel: string;
  description: string;
  loading: boolean;
  interactive: boolean;
  resultCount: number;
  hasSummary?: boolean;
  summary?: string;
  summaryStreaming?: boolean;
  sources?: DeepSearchCardItem[];
};

export type DeepSearchChapterWorkspaceModel = {
  title: string;
  content?: string;
  summary: string;
  sources: DeepSearchCardItem[];
  order?: number;
  isStreaming?: boolean;
};
