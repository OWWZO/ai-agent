import type { FileUIPart } from "ai";

export type PromptInputAttachmentItem = FileUIPart & {
  id: string;
  file?: File;
};

export type PromptInputAttachmentError = {
  code: "max_files" | "max_file_size" | "accept";
  message: string;
};
