export type MRagFileStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "UNKNOWN";

export type MRagSourceType = "file" | "url";

export type MRagWorkspaceStoredState = {
  toolBaseUrl: string;
  selectedKnowledgeBaseId: string;
};

export type KnowledgeBase = {
  id: string;
  name: string;
  description: string;
  chunkType: string;
  chunkSize?: number | null;
  chunkOverlapSize?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  raw: Record<string, unknown>;
};

export type KnowledgeBaseFile = {
  id: string;
  knowledgeBaseId: string;
  title: string;
  sourceType: MRagSourceType;
  sourceUrl: string;
  fileUrl: string;
  previewUrl: string;
  downloadUrl: string;
  fileExt: string;
  fileStatus: MRagFileStatus;
  taskStatus: Record<string, unknown>;
  docCount: number;
  createdAt?: string | null;
  updatedAt?: string | null;
  errorMessage: string;
  raw: Record<string, unknown>;
};

export type UploadDocumentResult = {
  documentId: string;
  filename: string;
  originalFilename: string;
  previewUrl: string;
  permanentUrl: string;
  presignedUrl: string;
  storageType: string;
  contentType: string;
  fileSize: number;
  uploadTime: string;
  raw: Record<string, unknown>;
};

export type MRagChunkEnvelope = {
  raw: unknown;
  content: string;
  finishReason?: string | null;
};

