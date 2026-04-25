export type DeepSearchCardItemKind = "result" | "query";

export type DeepSearchCardItem = {
  name: string;
  pageContent: string;
  url: string;
  kind?: DeepSearchCardItemKind;
  interactive?: boolean;
  metaLabel?: string;
};

export type DeepSearchPreviewModel = {
  stage: "extend" | "search";
  query: string;
  statusLabel: string;
  description: string;
  loading: boolean;
  interactive: boolean;
  resultCount: number;
};
