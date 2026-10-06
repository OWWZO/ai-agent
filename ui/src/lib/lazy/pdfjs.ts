export type PdfjsModule = typeof import("pdfjs-dist");

let pdfjsPromise: Promise<PdfjsModule> | undefined;
let workerUrlPromise: Promise<string> | undefined;

export function loadPdfjs(): Promise<PdfjsModule> {
  return (pdfjsPromise ??= import("pdfjs-dist"));
}

export function loadPdfWorkerUrl(): Promise<string> {
  return (workerUrlPromise ??= import(
    "pdfjs-dist/build/pdf.worker.min.mjs?url"
  ).then((module) => module.default));
}
