export type DocxPreviewModule = typeof import("docx-preview");

let docxPreviewPromise: Promise<DocxPreviewModule> | undefined;

export function loadDocxPreview(): Promise<DocxPreviewModule> {
  return (docxPreviewPromise ??= import("docx-preview"));
}
