import { ImageIcon, X } from "lucide-react";
import { isImageFileLike } from "@/utils/taskArtifacts";
import { cn } from "@/lib/utils";
import {
  FILE_KIND_TONE,
  type FileKind,
  resolveFileKind,
} from "@/utils/fileKind";

type Props = {
  files?: CHAT.TFile[];
  preview?: boolean;
  remove?: (index: number) => void;
  review?: (file: CHAT.TFile) => void;
};

function resolveExt(file: CHAT.TFile): string {
  return (file.type || file.name?.split(".").pop() || "").toLowerCase();
}

/** Attachment 列表把 py 并入 code 展示。 */
function resolveKind(file: CHAT.TFile): FileKind {
  if (isImageFileLike(file)) {
    return "img";
  }
  const kind = resolveFileKind(file.type, file.name);
  return kind === "py" ? "code" : kind;
}

function typeLabel(file: CHAT.TFile, kind: FileKind): string {
  const ext = resolveExt(file).toUpperCase();
  switch (kind) {
    case "img":
      return file.originFileName || describeFromName(file.name) || `Image · ${ext || "PNG"}`;
    case "xlsx":
      return `Spreadsheet · ${ext || "XLSX"}`;
    case "md":
      return describeFromName(file.name) || `Document · ${ext || "MD"}`;
    case "html":
      return "Web page · HTML";
    case "pdf":
      return "Document · PDF";
    case "css":
      return "样式文件 · CSS";
    case "code":
    case "py":
      return `Code · ${ext || "FILE"}`;
    default:
      return describeFromName(file.name) || (ext ? `File · ${ext}` : "File");
  }
}

/** 从文件名提炼副标题（去掉扩展名后的短描述） */
function describeFromName(name?: string): string {
  if (!name) return "";
  const base = name.replace(/\.[^.]+$/, "").trim();
  if (base.length <= 28) return base;
  return `${base.slice(0, 26)}…`;
}

function displayTitle(file: CHAT.TFile): string {
  const name = file.name || "未命名文件";
  // 主卡标题尽量用可读名，去掉过长扩展噪声
  return name;
}

function KindBadge({ kind, className }: { kind: FileKind; className?: string }) {
  // 对齐 ClawsGO：X / T / 图片图标字母角标
  if (kind === "xlsx") {
    return (
      <span className={cn("text-[15px] font-bold tracking-tight", className)}>
        X
      </span>
    );
  }
  if (kind === "md" || kind === "pdf" || kind === "file" || kind === "css") {
    return (
      <span className={cn("text-[15px] font-bold tracking-tight", className)}>
        T
      </span>
    );
  }
  if (kind === "html" || kind === "code" || kind === "py") {
    return (
      <span className={cn("text-[13px] font-bold tracking-tight", className)}>
        &lt;/&gt;
      </span>
    );
  }
  return <ImageIcon className={cn("h-5 w-5", className)} strokeWidth={1.6} />;
}

const AttachmentList: ReactorType.FC<Props> = (props) => {
  const { files, preview, remove, review } = props;
  const attachmentList = Array.isArray(files) ? files : [];

  if (!attachmentList.length) {
    return null;
  }

  // 交付态：主卡 + 其余 mini chips（ClawsGO / 截图样式）
  if (preview) {
    const [primary, ...rest] = attachmentList;
    const primaryKind = resolveKind(primary);

    return (
      <div className="mt-3.5 flex w-full max-w-full flex-col gap-2">
        <button
          type="button"
          className="group flex w-full items-center gap-3 rounded-2xl border border-[#e8e8ed] bg-[#fafafa] px-3.5 py-3 text-left transition-colors hover:bg-[#f3f3f5]"
          onClick={() => review?.(primary)}
        >
          <span
            className={cn(
              "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl",
              FILE_KIND_TONE
            )}
          >
            {isImageFileLike(primary) && primary.url ? (
              <img
                src={primary.url}
                alt=""
                className="h-10 w-10 rounded-xl object-cover"
              />
            ) : (
              <KindBadge kind={primaryKind} />
            )}
          </span>
          <div className="min-w-0 flex-1">
            <div className="truncate text-[14.5px] font-semibold tracking-[-0.01em] text-[#1d1d1f]">
              {displayTitle(primary)}
            </div>
            <div className="mt-0.5 truncate text-[12px] font-medium text-[#86868b]">
              {typeLabel(primary, primaryKind)}
            </div>
          </div>
          <span className="inline-flex h-8 shrink-0 items-center rounded-full border border-[#e5e5ea] bg-white px-3.5 text-[12.5px] font-medium text-[#1d1d1f] transition-colors group-hover:bg-[#f5f5f7]">
            打开文件
          </span>
        </button>

        {rest.length > 0 ? (
          <div className="flex w-full flex-wrap gap-2">
            {rest.map((file, index) => {
              const kind = resolveKind(file);
              return (
                <button
                  key={`${file.resourceKey || file.name}-${index}`}
                  type="button"
                  className="flex min-w-0 max-w-[calc(33.33%-6px)] flex-[1_1_140px] items-center gap-2 rounded-2xl border border-[#e8e8ed] bg-[#fafafa] px-2.5 py-2 text-left transition-colors hover:bg-[#f3f3f5]"
                  onClick={() => review?.(file)}
                  title={file.name}
                >
                  <span
                    className={cn(
                      "flex h-7 w-7 shrink-0 items-center justify-center rounded-lg",
                      FILE_KIND_TONE
                    )}
                  >
                    {isImageFileLike(file) && file.url ? (
                      <img
                        src={file.url}
                        alt=""
                        className="h-7 w-7 rounded-lg object-cover"
                      />
                    ) : kind === "img" ? (
                      <ImageIcon className="h-3.5 w-3.5" strokeWidth={1.6} />
                    ) : (
                      <KindBadge kind={kind} className="text-[12px]" />
                    )}
                  </span>
                  <span className="min-w-0">
                    <span className="block truncate text-[12.5px] font-semibold leading-tight text-[#1d1d1f]">
                      {displayTitle(file)}
                    </span>
                    <span className="mt-0.5 block truncate text-[11px] text-[#86868b]">
                      {typeLabel(file, kind)}
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        ) : null}
      </div>
    );
  }

  // 输入态附件：紧凑可删除
  return (
    <div className="flex w-full flex-wrap gap-2">
      {attachmentList.map((file, index) => {
        const kind = resolveKind(file);
        return (
          <div
            key={`${file.resourceKey || file.name}-${index}`}
            className="group relative flex max-w-sm items-center gap-2 rounded-2xl border border-[#e8e8ed] bg-[#fafafa] px-2.5 py-2"
          >
            <span
              className={cn(
                "flex h-8 w-8 shrink-0 items-center justify-center rounded-xl",
                FILE_KIND_TONE
              )}
            >
              {isImageFileLike(file) && file.url ? (
                <img
                  src={file.url}
                  alt=""
                  className="h-8 w-8 rounded-xl object-cover"
                />
              ) : (
                <KindBadge kind={kind} className="text-[13px]" />
              )}
            </span>
            <div className="min-w-0 flex-1">
              <div className="truncate text-[13px] font-medium text-[#1d1d1f]">
                {displayTitle(file)}
              </div>
              <div className="truncate text-[11px] text-[#86868b]">
                {typeLabel(file, kind)}
              </div>
            </div>
            {remove ? (
              <button
                type="button"
                className="absolute -right-1.5 -top-1.5 hidden h-5 w-5 items-center justify-center rounded-full border border-[#e5e5ea] bg-white text-[#86868b] shadow-sm group-hover:flex hover:text-[#1d1d1f]"
                onClick={(e) => {
                  e.stopPropagation();
                  remove(index);
                }}
                aria-label="移除附件"
              >
                <X className="h-3 w-3" />
              </button>
            ) : null}
          </div>
        );
      })}
    </div>
  );
};

export default AttachmentList;
