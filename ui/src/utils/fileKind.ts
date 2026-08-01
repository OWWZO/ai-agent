export type FileKind =
  | "img"
  | "xlsx"
  | "md"
  | "html"
  | "pdf"
  | "css"
  | "code"
  | "py"
  | "file";

export const FILE_KIND_TONE = "bg-[#f5f5f7] text-[#6b6b70]";

export function resolveFileKind(type?: string, name?: string): FileKind {
  const ext = (type || name?.split(".").pop() || "").toLowerCase();
  if (["png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "avif"].includes(ext)) {
    return "img";
  }
  if (["csv", "xlsx", "xls"].includes(ext)) return "xlsx";
  if (["md", "markdown", "txt"].includes(ext)) return "md";
  if (["html", "htm"].includes(ext)) return "html";
  if (ext === "pdf") return "pdf";
  if (["css", "scss", "less"].includes(ext)) return "css";
  if (ext === "py") return "py";
  if (["js", "ts", "tsx", "jsx", "java", "json", "xml", "code"].includes(ext)) {
    return "code";
  }
  return "file";
}

export function fileKindLabel(type?: string, name?: string) {
  const kind = resolveFileKind(type, name);
  const ext = (type || name?.split(".").pop() || "").toUpperCase();
  switch (kind) {
    case "img":
      return `Image · ${ext || "PNG"}`;
    case "xlsx":
      return `Spreadsheet · ${ext || "XLSX"}`;
    case "md":
      return name?.replace(/\.[^.]+$/, "") || `Document · ${ext || "MD"}`;
    case "html":
      return "Web page · HTML";
    case "pdf":
      return "PDF · PDF";
    case "css":
      return "样式文件 · CSS";
    case "py":
      return "Python · PY";
    case "code":
      return `Code · ${ext || "FILE"}`;
    default:
      return ext ? `File · ${ext}` : "File";
  }
}

export function fileKindBadge(kind: FileKind) {
  if (kind === "xlsx") return "X";
  if (kind === "py") return "Py";
  if (kind === "html" || kind === "code") return "</>";
  if (kind === "img") return "▢";
  if (kind === "pdf") return "P";
  return "T";
}
